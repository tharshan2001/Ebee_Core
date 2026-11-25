package com.ebee.finance.repository;

import com.ebee.finance.entity.AccountTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface AccountTransactionRepository extends JpaRepository<AccountTransaction, UUID> {

    @Query("SELECT COALESCE(SUM(CASE WHEN t.direction = com.ebee.finance.entity.AccountTransaction.Direction.IN "
            + "THEN t.amount ELSE -t.amount END), 0) FROM AccountTransaction t "
            + "WHERE t.account.id = :accountId AND t.txnDate <= :asOf")
    BigDecimal balanceAsOf(@Param("accountId") UUID accountId, @Param("asOf") LocalDate asOf);

    /** Rows: [accountId, balance] as of the date. */
    @Query("SELECT t.account.id, COALESCE(SUM(CASE WHEN t.direction = com.ebee.finance.entity.AccountTransaction.Direction.IN "
            + "THEN t.amount ELSE -t.amount END), 0) FROM AccountTransaction t "
            + "WHERE t.txnDate <= :asOf GROUP BY t.account.id")
    List<Object[]> balancesAsOf(@Param("asOf") LocalDate asOf);

    @Query("SELECT t FROM AccountTransaction t WHERE t.account.id = :accountId "
            + "AND t.txnDate >= :from AND t.txnDate <= :to ORDER BY t.txnDate, t.createdAt")
    List<AccountTransaction> statement(@Param("accountId") UUID accountId,
                                       @Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Rows: [type, direction, Σ amount] for the period, excluding internal moves. */
    @Query("SELECT t.type, t.direction, COALESCE(SUM(t.amount), 0) FROM AccountTransaction t "
            + "WHERE t.txnDate >= :from AND t.txnDate <= :to "
            + "AND t.type NOT IN (com.ebee.finance.entity.AccountTransaction.Type.TRANSFER, "
            + "com.ebee.finance.entity.AccountTransaction.Type.CHEQUE_DEPOSIT) "
            + "GROUP BY t.type, t.direction")
    List<Object[]> flowsByType(@Param("from") LocalDate from, @Param("to") LocalDate to);

    boolean existsByAccountId(UUID accountId);
}
