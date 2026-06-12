package com.ebee.expense.repository;

import com.ebee.expense.entity.Expense;
import com.ebee.expense.enums.ExpenseCategory;
import com.ebee.expense.enums.ExpenseStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, UUID> {

    List<Expense> findByCreatedByIdOrderByCreatedAtDesc(UUID createdById);

    List<Expense> findByDriverIdOrderByCreatedAtDesc(UUID driverId);

    Page<Expense> findAllByOrderByCreatedAtDesc(Pageable pageable);

    long countByStatus(ExpenseStatus status);

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM Expense e WHERE e.status = 'APPROVED' AND e.dateIncurred >= :start AND e.dateIncurred < :end")
    BigDecimal sumApprovedByDateRange(@Param("start") LocalDate start, @Param("end") LocalDate end);

    @Query("SELECT e.category, COALESCE(SUM(e.amount), 0) FROM Expense e WHERE e.status = 'APPROVED' AND e.dateIncurred >= :start AND e.dateIncurred < :end GROUP BY e.category")
    List<Object[]> sumApprovedGroupByCategory(@Param("start") LocalDate start, @Param("end") LocalDate end);
}
