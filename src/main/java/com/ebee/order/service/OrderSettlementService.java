package com.ebee.order.service;

import com.ebee.accounting.service.LedgerService;
import com.ebee.common.security.SecurityService;
import com.ebee.combo.entity.ComboItem;
import com.ebee.customer.entity.Customer;
import com.ebee.customer.enums.AdvanceReferenceType;
import com.ebee.customer.service.CustomerService;
import com.ebee.inventory.service.InventoryService;
import com.ebee.order.entity.Order;
import com.ebee.order.entity.OrderItem;
import com.ebee.order.enums.PaymentStatus;
import com.ebee.order.enums.RefundChoice;
import com.ebee.tax.entity.VatEntry;
import com.ebee.tax.service.VatService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The one place that settles money and stock when an order's value goes down:
 * cancellation, item returns, approved return/exchange requests, and permanently
 * failed deliveries.
 *
 * <p>Ledger rules (see {@code LedgerTransactionType}):
 * <ol>
 *   <li>Post a CREDIT_NOTE for the value removed.</li>
 *   <li>If the customer has now paid more than the order is worth, give the excess back:
 *       REFUND (cash/bank) or ADVANCE_TRANSFER (into the advance wallet). Both are debits.</li>
 *   <li>Money the customer still owes stays owed — a return on a partly paid order first
 *       reduces what they owe; cash only goes back for real overpayment.</li>
 * </ol>
 * After settlement, {@code order.outstandingBalance} always equals {@code effectiveTotal - paidAmount},
 * which keeps the ledger balance equal to the sum of order outstandings.
 */
@Service
public class OrderSettlementService {

    private final InventoryService inventoryService;
    private final LedgerService ledgerService;
    private final CustomerService customerService;
    private final SecurityService securityService;
    private final VatService vatService;
    private final com.ebee.finance.service.MoneyAccountService moneyAccountService;

    public OrderSettlementService(InventoryService inventoryService, LedgerService ledgerService,
                                  CustomerService customerService, SecurityService securityService,
                                  VatService vatService, com.ebee.finance.service.MoneyAccountService moneyAccountService) {
        this.inventoryService = inventoryService;
        this.ledgerService = ledgerService;
        this.customerService = customerService;
        this.securityService = securityService;
        this.vatService = vatService;
        this.moneyAccountService = moneyAccountService;
    }

    /** A quantity of one order line being taken back. */
    public record ReturnLine(OrderItem item, int quantity) {}

    /** What a settlement did, for events and responses. */
    public record Settlement(BigDecimal creditNote, BigDecimal refunded, BigDecimal toAdvance) {
        public boolean moneyReturned() {
            return refunded.signum() > 0 || toAdvance.signum() > 0;
        }
    }

    // ── Entry points ───────────────────────────────────────────────────

    /**
     * Cancel the whole order: optionally restore all remaining stock, credit-note the remaining
     * value, and give back everything paid. {@code order.totalAmount} is kept for history.
     */
    public Settlement cancel(Order order, RefundChoice refundChoice, boolean restoreStock, String reason) {
        // VAT on everything still kept is reversed (order.taxTotal is kept for history, like totalAmount).
        BigDecimal reversedTax = order.getItems().stream().map(OrderItem::getRetainedTax)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        postCreditNoteVat(order, order.getTotalAmount(), reversedTax);
        if (restoreStock) {
            List<ReturnLine> lines = order.getItems().stream()
                    .filter(i -> i.getReturnableQuantity() > 0)
                    .map(i -> new ReturnLine(i, i.getReturnableQuantity()))
                    .toList();
            restoreStock(order, lines, reason);
        }
        BigDecimal remainingValue = order.getTotalAmount();
        return settleValueReduction(order, remainingValue, BigDecimal.ZERO, refundChoice, reason,
                AdvanceReferenceType.ORDER_CANCELLED);
    }

    /**
     * Take back specific lines: mark them returned, restore stock (combos expand to their
     * products), lower {@code order.totalAmount}, and settle the money.
     */
    public Settlement returnLines(Order order, List<ReturnLine> lines, RefundChoice refundChoice, String reason) {
        BigDecimal returnedValue = BigDecimal.ZERO;
        for (ReturnLine line : lines) {
            OrderItem item = line.item();
            if (line.quantity() <= 0 || line.quantity() > item.getReturnableQuantity()) {
                throw new IllegalArgumentException("Cannot return " + line.quantity() + " of "
                        + lineName(item) + ". Returnable: " + item.getReturnableQuantity());
            }
            returnedValue = returnedValue.add(item.getUnitPrice().multiply(BigDecimal.valueOf(line.quantity())));
        }
        BigDecimal reversedTax = BigDecimal.ZERO;
        for (ReturnLine line : lines) {
            reversedTax = reversedTax.add(line.item().getUnitTax().multiply(BigDecimal.valueOf(line.quantity())));
        }
        restoreStock(order, lines, reason);
        for (ReturnLine line : lines) {
            line.item().setReturnedQuantity(line.item().getReturnedQuantity() + line.quantity());
        }
        order.setTaxTotal(order.getTaxTotal().subtract(reversedTax).max(BigDecimal.ZERO));
        postCreditNoteVat(order, returnedValue, reversedTax);

        BigDecimal newTotal = order.getTotalAmount().subtract(returnedValue).max(BigDecimal.ZERO);
        order.setTotalAmount(newTotal);
        return settleValueReduction(order, returnedValue, newTotal, refundChoice, reason,
                AdvanceReferenceType.ORDER_RETURNED);
    }

    /** Whether settling to {@code effectiveTotal} would give money back (so a refund choice is required). */
    public static boolean wouldReturnMoney(Order order, BigDecimal effectiveTotal) {
        return order.getPaidAmount().compareTo(effectiveTotal) > 0;
    }

    // ── Core ───────────────────────────────────────────────────────────

    private Settlement settleValueReduction(Order order, BigDecimal valueRemoved, BigDecimal effectiveTotal,
                                            RefundChoice refundChoice, String reason,
                                            AdvanceReferenceType advanceRefType) {
        Customer customer = order.getCustomer();
        if (customer == null) {
            throw new IllegalArgumentException("Order has no customer assigned");
        }
        UUID actor = securityService.getCurrentUserId();
        String label = reason + " - Order #" + order.getOrderNumber();

        if (valueRemoved.signum() > 0) {
            ledgerService.recordCustomerCreditNote(customer.getId(), order.getId(), valueRemoved, label, actor);
        }

        BigDecimal paid = order.getPaidAmount();
        BigDecimal overpaid = paid.subtract(effectiveTotal).max(BigDecimal.ZERO);
        BigDecimal refunded = BigDecimal.ZERO;
        BigDecimal toAdvance = BigDecimal.ZERO;

        if (overpaid.signum() > 0) {
            if (refundChoice == null) {
                throw new IllegalArgumentException(
                        "refundChoice (REFUND or CREDIT_ADVANCE) is required: customer has paid "
                                + paid + " against a value of " + effectiveTotal);
            }
            if (refundChoice == RefundChoice.CREDIT_ADVANCE) {
                customerService.creditToAdvance(customer, overpaid, advanceRefType,
                        order.getId(), "Credited from " + label);
                ledgerService.recordCustomerAdvanceTransfer(customer.getId(), order.getId(), overpaid,
                        "Moved to advance: " + label, actor);
                toAdvance = overpaid;
            } else {
                ledgerService.recordCustomerRefund(customer.getId(), order.getId(), overpaid,
                        "Refund: " + label, actor);
                // Cash/bank goes out of the account the order was paid through (REFUND default otherwise).
                moneyAccountService.recordPayout(overpaid,
                        order.getPaymentMethod() != null ? order.getPaymentMethod().name() : null, null,
                        com.ebee.finance.entity.AccountTransaction.Type.CUSTOMER_REFUND, "ORDER", order.getId(), "Refund: " + label);
                refunded = overpaid;
            }
            paid = paid.subtract(overpaid);
            order.setPaidAmount(paid);
        }

        order.setOutstandingBalance(effectiveTotal.subtract(paid).max(BigDecimal.ZERO));
        order.setPaymentStatus(paymentStatusAfter(order, paid, overpaid));

        customerService.syncAllBalances(customer);
        return new Settlement(valueRemoved, refunded, toAdvance);
    }

    private static PaymentStatus paymentStatusAfter(Order order, BigDecimal paid, BigDecimal returned) {
        if (paid.signum() == 0) {
            return returned.signum() > 0 ? PaymentStatus.REFUNDED : PaymentStatus.UNPAID;
        }
        return order.getOutstandingBalance().signum() == 0 ? PaymentStatus.PAID : PaymentStatus.PARTIALLY_PAID;
    }

    /** Negative OUTPUT VAT entry for value removed from a sale, dated today. */
    private void postCreditNoteVat(Order order, BigDecimal grossRemoved, BigDecimal taxRemoved) {
        vatService.post(VatEntry.Direction.OUTPUT, VatEntry.SourceType.CREDIT_NOTE, order.getId(),
                order.getOrderNumber(), grossRemoved.subtract(taxRemoved).negate(), taxRemoved.negate());
    }

    // ── Stock ──────────────────────────────────────────────────────────

    /** Restore stock for the given lines. Combo lines expand to their component products. */
    public void restoreStock(Order order, List<ReturnLine> lines, String reason) {
        List<UUID> productIds = new ArrayList<>();
        List<Integer> quantities = new ArrayList<>();
        List<BigDecimal> unitCosts = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        String note = reason + " - Order " + order.getOrderNumber();

        for (ReturnLine line : lines) {
            OrderItem item = line.item();
            if (item.getCombo() != null) {
                for (ComboItem ci : item.getCombo().getItems()) {
                    productIds.add(ci.getProduct().getId());
                    quantities.add(ci.getQuantity() * line.quantity());
                    unitCosts.add(null); // per-component cost isn't stored; goes back at current average
                    notes.add(note + " (combo " + item.getCombo().getName() + ")");
                }
            } else if (item.getProduct() != null) {
                productIds.add(item.getProduct().getId());
                quantities.add(line.quantity());
                unitCosts.add(item.getCostPrice()); // back in at the cost it left at
                notes.add(note);
            }
        }
        if (!productIds.isEmpty()) {
            // Back to where it was shipped from (pre-V127 orders were backfilled to the default location).
            UUID locationId = order.getLocation() != null ? order.getLocation().getId() : null;
            inventoryService.returnStock(locationId, productIds, quantities, unitCosts, notes);
        }
    }

    static String lineName(OrderItem item) {
        if (item.getCombo() != null) {
            return "combo " + item.getCombo().getName();
        }
        return item.getProduct() != null ? item.getProduct().getName() : "item " + item.getId();
    }
}
