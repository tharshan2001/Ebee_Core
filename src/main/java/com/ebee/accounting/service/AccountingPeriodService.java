package com.ebee.accounting.service;

import com.ebee.accounting.entity.AccountingPeriod;
import com.ebee.accounting.entity.AccountingPeriodAudit;
import com.ebee.accounting.entity.AccountingPeriodTransaction;
import com.ebee.accounting.entity.FiscalYear;
import com.ebee.accounting.enums.PeriodStatus;
import com.ebee.accounting.repository.AccountingPeriodAuditRepository;
import com.ebee.accounting.repository.AccountingPeriodRepository;
import com.ebee.accounting.repository.AccountingPeriodTransactionRepository;
import com.ebee.accounting.repository.FiscalYearRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountingPeriodService {

    private final AccountingPeriodRepository accountingPeriodRepository;
    private final AccountingPeriodAuditRepository accountingPeriodAuditRepository;
    private final AccountingPeriodTransactionRepository accountingPeriodTransactionRepository;
    private final FiscalYearRepository fiscalYearRepository;
    @org.springframework.context.annotation.Lazy
    private final LedgerService ledgerService;

    @Transactional(readOnly = true)
    public List<AccountingPeriod> getAllPeriods() {
        return accountingPeriodRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<AccountingPeriod> getPeriodsByYear(Integer year) {
        return accountingPeriodRepository.findByYearOrderByMonth(year);
    }

    @Transactional(readOnly = true)
    public Optional<AccountingPeriod> getPeriodByMonthAndYear(Integer month, Integer year) {
        return accountingPeriodRepository.findByMonthAndYear(month, year);
    }

    @Transactional(readOnly = true)
    public List<AccountingPeriod> getOpenPeriods() {
        return accountingPeriodRepository.findOpenPeriods();
    }

    @Transactional(readOnly = true)
    public boolean isPeriodOpen(Integer month, Integer year) {
        return accountingPeriodRepository.findByMonthAndYear(month, year)
                .map(p -> p.getStatus() == PeriodStatus.OPEN)
                .orElse(false);
    }

    @Transactional
    public AccountingPeriod openPeriod(Integer year, Integer month, UUID openedBy) {
        FiscalYear fiscalYear = fiscalYearRepository.findByFiscalYear(year)
                .orElseThrow(() -> new IllegalArgumentException("Fiscal year not found: " + year));

        AccountingPeriod period = accountingPeriodRepository.findByMonthAndYear(month, year)
                .orElseGet(() -> AccountingPeriod.builder()
                        .fiscalYear(fiscalYear)
                        .month(month)
                        .year(year)
                        .status(PeriodStatus.OPEN)
                        .openedAt(LocalDateTime.now())
                        .build());

        if (period.getStatus() == PeriodStatus.OPEN) {
            log.warn("Period {}/{} is already open", month, year);
            return period;
        }

        period.setStatus(PeriodStatus.OPEN);
        period.setOpenedAt(LocalDateTime.now());
        period = accountingPeriodRepository.save(period);

        auditPeriodChange(period.getId(), null, PeriodStatus.OPEN, openedBy, "Period opened");
        log.info("Opened period: {}/{}", month, year);

        return period;
    }

    @Transactional
    public AccountingPeriod closePeriod(UUID periodId, UUID closedBy, String reason) {
        AccountingPeriod period = accountingPeriodRepository.findById(periodId)
                .orElseThrow(() -> new IllegalArgumentException("Period not found: " + periodId));

        if (period.getStatus() != PeriodStatus.OPEN) {
            throw new IllegalStateException("Can only close OPEN periods");
        }

        // Capture balance snapshot before closing
        captureBalanceSnapshot(period, closedBy);

        PeriodStatus oldStatus = period.getStatus();
        period.setStatus(PeriodStatus.CLOSED);
        period.setClosedAt(LocalDateTime.now());
        period = accountingPeriodRepository.save(period);

        auditPeriodChange(periodId, oldStatus, PeriodStatus.CLOSED, closedBy, reason);
        log.info("Closed period: {} - {}", period.getYear() + "/" + period.getMonth(), reason);

        return period;
    }

    private void captureBalanceSnapshot(AccountingPeriod period, UUID closedBy) {
        // Record period summary as a snapshot transaction
        List<AccountingPeriodTransaction> transactions =
                accountingPeriodTransactionRepository.findByPeriodIdOrderByRecordedAtDesc(period.getId());

        BigDecimal totalDebits = transactions.stream()
                .map(AccountingPeriodTransaction::getDebit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCredits = transactions.stream()
                .map(AccountingPeriodTransaction::getCredit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        AccountingPeriodTransaction snapshot = AccountingPeriodTransaction.builder()
                .periodId(period.getId())
                .fiscalYear(period.getYear())
                .month(period.getMonth())
                .year(period.getYear())
                .transactionType("PERIOD_SNAPSHOT")
                .debit(totalDebits)
                .credit(totalCredits)
                .description("Period-end snapshot: " + transactions.size() + " transactions, "
                        + "total debits=" + totalDebits + ", total credits=" + totalCredits)
                .recordedBy(closedBy)
                .recordedAt(LocalDateTime.now())
                .build();

        accountingPeriodTransactionRepository.save(snapshot);
        log.info("Captured balance snapshot for period {}/{}: {} transactions",
                period.getYear(), period.getMonth(), transactions.size());
    }

    @Transactional
    public AccountingPeriod reopenPeriod(UUID periodId, UUID reopenedBy, String reason) {
        AccountingPeriod period = accountingPeriodRepository.findById(periodId)
                .orElseThrow(() -> new IllegalArgumentException("Period not found: " + periodId));

        if (period.getStatus() != PeriodStatus.CLOSED) {
            throw new IllegalStateException("Can only reopen CLOSED periods");
        }

        PeriodStatus oldStatus = period.getStatus();
        period.setStatus(PeriodStatus.OPEN);
        period.setClosedAt(null);
        period = accountingPeriodRepository.save(period);

        auditPeriodChange(periodId, oldStatus, PeriodStatus.OPEN, reopenedBy, reason);
        log.info("Reopened period: {} - {}", period.getYear() + "/" + period.getMonth(), reason);

        return period;
    }

    @Transactional
    public AccountingPeriod archivePeriod(UUID periodId, UUID archivedBy, String reason) {
        AccountingPeriod period = accountingPeriodRepository.findById(periodId)
                .orElseThrow(() -> new IllegalArgumentException("Period not found: " + periodId));

        if (period.getStatus() == PeriodStatus.ARCHIVED) {
            throw new IllegalStateException("Period is already archived");
        }

        PeriodStatus oldStatus = period.getStatus();
        period.setStatus(PeriodStatus.ARCHIVED);
        period = accountingPeriodRepository.save(period);

        auditPeriodChange(periodId, oldStatus, PeriodStatus.ARCHIVED, archivedBy, reason);
        log.info("Archived period: {} - {}", period.getYear() + "/" + period.getMonth(), reason);

        return period;
    }

    private void auditPeriodChange(UUID periodId, PeriodStatus oldStatus, PeriodStatus newStatus, UUID changedBy, String reason) {
        AccountingPeriod period = accountingPeriodRepository.findById(periodId).orElse(null);
        if (period == null) return;

        AccountingPeriodAudit audit = AccountingPeriodAudit.builder()
                .periodId(periodId)
                .fiscalYear(period.getYear())
                .month(period.getMonth())
                .previousStatus(oldStatus)
                .newStatus(newStatus)
                .changedBy(changedBy)
                .changedAt(LocalDateTime.now())
                .reason(reason)
                .build();

        accountingPeriodAuditRepository.save(audit);
    }

    @Transactional(readOnly = true)
    public List<AccountingPeriodAudit> getPeriodHistory(UUID periodId) {
        return accountingPeriodAuditRepository.findByPeriodIdOrderByChangedAtDesc(periodId);
    }

    @Transactional
    public AccountingPeriodTransaction recordPeriodTransaction(
            String transactionType,
            BigDecimal debit,
            BigDecimal credit,
            UUID referenceId,
            String referenceType,
            String description,
            UUID recordedBy) {

        LocalDate today = LocalDate.now();
        int month = today.getMonthValue();
        int year = today.getYear();

        Optional<AccountingPeriod> periodOpt = accountingPeriodRepository.findByMonthAndYear(month, year);

        if (periodOpt.isEmpty()) {
            log.warn("No open period found for {}/{}, transaction not recorded", month, year);
            return null;
        }

        AccountingPeriod period = periodOpt.get();
        if (period.getStatus() != PeriodStatus.OPEN) {
            log.warn("Period {}/{} is not open (status={}), transaction not recorded", month, year, period.getStatus());
            return null;
        }

        AccountingPeriodTransaction transaction = AccountingPeriodTransaction.builder()
                .periodId(period.getId())
                .fiscalYear(year)
                .month(month)
                .year(year)
                .transactionType(transactionType)
                .debit(debit)
                .credit(credit)
                .referenceId(referenceId)
                .referenceType(referenceType)
                .description(description)
                .recordedBy(recordedBy)
                .recordedAt(LocalDateTime.now())
                .build();

        log.info("Recorded period transaction: type={}, amount={}, period={}/{}",
                transactionType, debit.add(credit), month, year);

        return accountingPeriodTransactionRepository.save(transaction);
    }

    @Transactional(readOnly = true)
    public List<AccountingPeriodTransaction> getPeriodTransactions(UUID periodId) {
        return accountingPeriodTransactionRepository.findByPeriodIdOrderByRecordedAtDesc(periodId);
    }

    @Transactional(readOnly = true)
    public List<AccountingPeriodTransaction> getTransactionsByPeriod(int fiscalYear, int month) {
        return accountingPeriodTransactionRepository.findByFiscalYearAndMonthOrderByRecordedAtDesc(fiscalYear, month);
    }

    @Scheduled(cron = "0 0 1 1 * *") // 1st of every month at 1:00 AM
    @SchedulerLock(name = "autoOpenAccountingPeriod", lockAtMostFor = "PT5M")
    @Transactional
    public void autoOpenCurrentMonthPeriod() {
        LocalDate today = LocalDate.now();
        int month = today.getMonthValue();
        int year = today.getYear();

        Optional<AccountingPeriod> existing = accountingPeriodRepository.findByMonthAndYear(month, year);
        if (existing.isPresent()) {
            log.info("Period {}/{} already exists (status={}), skipping auto-open",
                    year, month, existing.get().getStatus());
            return;
        }

        // Ensure fiscal year exists
        Optional<FiscalYear> fiscalYear = fiscalYearRepository.findByFiscalYear(year);
        if (fiscalYear.isEmpty()) {
            FiscalYear newFy = FiscalYear.builder()
                    .fiscalYear(year)
                    .startDate(LocalDate.of(year, 1, 1))
                    .endDate(LocalDate.of(year, 12, 31))
                    .isActive(true)
                    .build();
            fiscalYearRepository.save(newFy);
            log.info("Auto-created fiscal year {}", year);
        }

        try {
            openPeriod(month, year, null);
            log.info("Auto-opened accounting period {}/{}", year, month);
        } catch (Exception e) {
            log.error("Failed to auto-open period {}/{}: {}", year, month, e.getMessage());
        }
    }
}
