package com.ebee.expense.repository;

import com.ebee.expense.entity.ExpenseBudget;
import com.ebee.expense.enums.ExpenseCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ExpenseBudgetRepository extends JpaRepository<ExpenseBudget, UUID> {

    Optional<ExpenseBudget> findByCategoryAndPeriodStart(ExpenseCategory category, LocalDate periodStart);

    List<ExpenseBudget> findByPeriodStartGreaterThanEqualOrderByCategory(LocalDate periodStart);

    List<ExpenseBudget> findAllByOrderByPeriodStartDescCategoryAsc();
}
