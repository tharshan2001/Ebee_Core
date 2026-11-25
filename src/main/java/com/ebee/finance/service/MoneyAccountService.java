package com.ebee.finance.service;

import com.ebee.common.exception.ResourceNotFoundException;
import com.ebee.common.security.SecurityService;
import com.ebee.finance.dto.AccountStatement;
import com.ebee.finance.dto.PaymentAccountRequest;
import com.ebee.finance.dto.PaymentAccountResponse;
import com.ebee.finance.entity.AccountTransaction;
import com.ebee.finance.entity.AccountTransaction.Direction;
import com.ebee.finance.entity.AccountTransaction.Type;
import com.ebee.finance.entity.PaymentAccount;
import com.ebee.finance.entity.PaymentAccountDefault;
import com.ebee.finance.repository.AccountTransactionRepository;
import com.ebee.finance.repository.PaymentAccountDefaultRepository;
import com.ebee.finance.repository.PaymentAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Bank and cash accounts. Every real movement of money — customer receipts, refunds, supplier
 * payments, expenses, commission payouts — is recorded here against the account it went
 * through, so each account's balance is what is actually in it.
 */
@Service
public class MoneyAccountService {

    private final PaymentAccountRepository accountRepository;
    private final AccountTransactionRepository transactionRepository;
    private final PaymentAccountDefaultRepository defaultRepository;
    private final SecurityService securityService;

    public MoneyAccountService(PaymentAccountRepository accountRepository,
                               AccountTransactionRepository transactionRepository,
                               PaymentAccountDefaultRepository defaultRepository,
                               SecurityService securityService) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.defaultRepository = defaultRepository;
        this.securityService = securityService;
    }

    // ── recording business money movements ─────────────────────────────

    /**
     * Record money moving through the account picked by the caller, or else the default account
     * for {@code purpose} (a payment method name like CASH/CHEQUE, or EXPENSE / COMMISSION_PAYOUT /
     * REFUND). Returns null — and records nothing — when no account applies (e.g. CREDIT, which
     * moves no money) or the amount is zero.
     */
    @Transactional
    public AccountTransaction recordMovement(Direction direction, BigDecimal amount, String purpose, UUID accountId,
                                             Type type, String referenceType, UUID referenceId, String description) {
        if (amount == null || amount.signum() <= 0) {
            return null;
        }
        PaymentAccount account = accountId != null ? requireActive(accountId) : defaultAccount(purpose);
        if (account == null) {
            return null;
        }
        return transactionRepository.save(new AccountTransaction(account, LocalDate.now(), direction, amount, type,
                referenceType, referenceId, null, description, securityService.getCurrentUserId()));
    }

    /** Money going out where some account must pay it (refunds): falls back to the REFUND default. */
    @Transactional
    public AccountTransaction recordPayout(BigDecimal amount, String purpose, UUID accountId, Type type,
                                           String referenceType, UUID referenceId, String description) {
        PaymentAccount account = accountId != null ? requireActive(accountId) : defaultAccount(purpose);
        UUID chosen = account != null ? account.getId() : null;
        if (chosen == null) {
            PaymentAccount refund = defaultAccount("REFUND");
            chosen = refund != null ? refund.getId() : null;
        }
        if (chosen == null) {
            return null;
        }
        return recordMovement(Direction.OUT, amount, purpose, chosen, type, referenceType, referenceId, description);
    }

    private PaymentAccount defaultAccount(String purpose) {
        if (purpose == null) {
            return null;
        }
        return defaultRepository.findById(purpose).map(PaymentAccountDefault::getAccount)
                .filter(PaymentAccount::isActive).orElse(null);
    }

    // ── accounts ───────────────────────────────────────────────────────

    @Transactional
    public PaymentAccountResponse createAccount(PaymentAccountRequest request) {
        if (accountRepository.existsByNameIgnoreCase(request.name().trim())) {
            throw new IllegalArgumentException("An account named '" + request.name() + "' already exists");
        }
        PaymentAccount account = accountRepository.save(new PaymentAccount(request.name().trim(), request.type(),
                request.bankName(), request.accountNumber()));
        if (request.openingBalance() != null && request.openingBalance().signum() != 0) {
            Direction dir = request.openingBalance().signum() > 0 ? Direction.IN : Direction.OUT;
            transactionRepository.save(new AccountTransaction(account,
                    request.openingDate() != null ? request.openingDate() : LocalDate.now(), dir,
                    request.openingBalance().abs(), Type.OPENING, null, null, null, "Opening balance",
                    securityService.getCurrentUserId()));
        }
        return toResponse(account, balance(account.getId(), LocalDate.now()));
    }

    @Transactional
    public PaymentAccountResponse updateAccount(UUID id, PaymentAccountRequest request) {
        PaymentAccount account = requireAccount(id);
        account.setName(request.name().trim());
        account.setType(request.type());
        account.setBankName(request.bankName());
        account.setAccountNumber(request.accountNumber());
        if (request.active() != null) {
            account.setActive(request.active());
        }
        return toResponse(accountRepository.save(account), balance(id, LocalDate.now()));
    }

    @Transactional(readOnly = true)
    public List<PaymentAccountResponse> listAccounts(LocalDate asOf) {
        Map<UUID, BigDecimal> balances = balancesAsOf(asOf);
        return accountRepository.findAllByOrderByNameAsc().stream()
                .map(a -> toResponse(a, balances.getOrDefault(a.getId(), BigDecimal.ZERO)))
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<UUID, BigDecimal> balancesAsOf(LocalDate asOf) {
        Map<UUID, BigDecimal> m = new HashMap<>();
        for (Object[] r : transactionRepository.balancesAsOf(asOf)) {
            m.put((UUID) r[0], (BigDecimal) r[1]);
        }
        return m;
    }

    @Transactional(readOnly = true)
    public BigDecimal balance(UUID accountId, LocalDate asOf) {
        return transactionRepository.balanceAsOf(accountId, asOf);
    }

    @Transactional(readOnly = true)
    public AccountStatement statement(UUID accountId, LocalDate from, LocalDate to) {
        PaymentAccount account = requireAccount(accountId);
        BigDecimal running = balance(accountId, from.minusDays(1));
        BigDecimal opening = running;
        List<AccountStatement.Line> lines = new ArrayList<>();
        for (AccountTransaction t : transactionRepository.statement(accountId, from, to)) {
            running = running.add(t.signedAmount());
            lines.add(new AccountStatement.Line(t.getId(), t.getTxnDate(), t.getType(), t.getDirection(), t.getAmount(),
                    running, t.getReferenceType(), t.getReferenceId(), t.getDescription()));
        }
        return new AccountStatement(account.getId(), account.getName(), from, to, opening, running, lines);
    }

    // ── internal moves and owner money ─────────────────────────────────

    /** Move money between your own accounts (e.g. cash banked). Not income or spending. */
    @Transactional
    public UUID transfer(UUID fromId, UUID toId, BigDecimal amount, LocalDate date, String note) {
        return internalMove(fromId, toId, amount, date, note, Type.TRANSFER);
    }

    /** Cheques received are held in a CHEQUES_IN_HAND account until banked. */
    @Transactional
    public UUID depositCheques(UUID chequesAccountId, UUID bankAccountId, BigDecimal amount, LocalDate date, String reference) {
        PaymentAccount from = requireActive(chequesAccountId);
        if (from.getType() != PaymentAccount.Type.CHEQUES_IN_HAND) {
            throw new IllegalArgumentException("'" + from.getName() + "' is not a cheques-in-hand account");
        }
        if (requireActive(bankAccountId).getType() != PaymentAccount.Type.BANK) {
            throw new IllegalArgumentException("Cheques can only be deposited into a bank account");
        }
        return internalMove(chequesAccountId, bankAccountId, amount, date,
                "Cheque deposit" + (reference != null ? ": " + reference : ""), Type.CHEQUE_DEPOSIT);
    }

    /** Owner puts money in (capital) or takes it out (drawings). */
    @Transactional
    public AccountTransaction ownerMovement(UUID accountId, Direction direction, BigDecimal amount, LocalDate date, String note) {
        requirePositive(amount);
        PaymentAccount account = requireActive(accountId);
        Type type = direction == Direction.IN ? Type.OWNER_DEPOSIT : Type.OWNER_WITHDRAWAL;
        return transactionRepository.save(new AccountTransaction(account, date != null ? date : LocalDate.now(),
                direction, amount, type, null, null, null, note, securityService.getCurrentUserId()));
    }

    private UUID internalMove(UUID fromId, UUID toId, BigDecimal amount, LocalDate date, String note, Type type) {
        requirePositive(amount);
        if (fromId.equals(toId)) {
            throw new IllegalArgumentException("Choose two different accounts");
        }
        PaymentAccount from = requireActive(fromId);
        PaymentAccount to = requireActive(toId);
        LocalDate d = date != null ? date : LocalDate.now();
        UUID group = UUID.randomUUID();
        UUID actor = securityService.getCurrentUserId();
        transactionRepository.save(new AccountTransaction(from, d, Direction.OUT, amount, type, null, null, group,
                note != null ? note : "To " + to.getName(), actor));
        transactionRepository.save(new AccountTransaction(to, d, Direction.IN, amount, type, null, null, group,
                note != null ? note : "From " + from.getName(), actor));
        return group;
    }

    // ── defaults ───────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Map<String, UUID> getDefaults() {
        Map<String, UUID> m = new java.util.TreeMap<>();
        defaultRepository.findAll().forEach(d -> m.put(d.getPurpose(), d.getAccount().getId()));
        return m;
    }

    @Transactional
    public Map<String, UUID> setDefault(String purpose, UUID accountId) {
        String key = purpose.trim().toUpperCase(java.util.Locale.ROOT);
        PaymentAccount account = requireActive(accountId);
        PaymentAccountDefault d = defaultRepository.findById(key).orElseGet(() -> new PaymentAccountDefault(key, account));
        d.setAccount(account);
        defaultRepository.save(d);
        return getDefaults();
    }

    // ── helpers ────────────────────────────────────────────────────────

    private PaymentAccount requireAccount(UUID id) {
        return accountRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("PaymentAccount", "id", id));
    }

    private PaymentAccount requireActive(UUID id) {
        PaymentAccount a = requireAccount(id);
        if (!a.isActive()) {
            throw new IllegalArgumentException("Account '" + a.getName() + "' is inactive");
        }
        return a;
    }

    private static void requirePositive(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
    }

    private static PaymentAccountResponse toResponse(PaymentAccount a, BigDecimal balance) {
        return new PaymentAccountResponse(a.getId(), a.getName(), a.getType(), a.getBankName(), a.getAccountNumber(),
                a.isActive(), balance);
    }
}
