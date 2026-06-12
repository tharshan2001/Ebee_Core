package com.ebee.expense.repository;

import com.ebee.expense.entity.ExpenseBudget;
import com.ebee.expense.enums.ExpenseCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ExpenseBudgetRepository extends JpaRepository<ExpenseBudget, UUID> {

    Optional<ExpenseBudget> findByCategoryAndPeriodStart(ExpenseCategory category, LocalDate periodStart);

    @Query("SELECT b FROM ExpenseBudget b WHERE b.category = :category AND :date BETWEEN b.periodStart AND b.periodEnd")
    Optional<ExpenseBudget> findByCategoryAndDate(@Param("category") ExpenseCategory category, @Param("date") LocalDate date);

    List<ExpenseBudget> findAllByOrderByPeriodStartDescCategoryAsc();
}
