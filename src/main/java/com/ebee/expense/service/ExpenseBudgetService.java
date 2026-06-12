package com.ebee.expense.service;

import com.ebee.common.exception.ResourceNotFoundException;
import com.ebee.expense.dto.BudgetRequest;
import com.ebee.expense.dto.BudgetResponse;
import com.ebee.expense.entity.ExpenseBudget;
import com.ebee.expense.enums.ExpenseCategory;
import com.ebee.expense.mapper.ExpenseBudgetMapper;
import com.ebee.expense.repository.ExpenseBudgetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExpenseBudgetService {

    private final ExpenseBudgetRepository budgetRepository;
    private final ExpenseBudgetMapper budgetMapper;

    @Transactional
    public BudgetResponse createBudget(BudgetRequest request) {
        if (budgetRepository.findByCategoryAndPeriodStart(request.getCategory(), request.getPeriodStart()).isPresent()) {
            throw new IllegalArgumentException(
                    "Budget already exists for category " + request.getCategory()
                    + " starting " + request.getPeriodStart());
        }

        ExpenseBudget budget = ExpenseBudget.builder()
                .category(request.getCategory())
                .periodStart(request.getPeriodStart())
                .periodEnd(request.getPeriodEnd())
                .allocatedAmount(request.getAllocatedAmount())
                .build();

        budget = budgetRepository.save(budget);
        log.info("Budget created for {} period {}-{}: LKR {}",
                budget.getCategory(), budget.getPeriodStart(), budget.getPeriodEnd(), budget.getAllocatedAmount());
        return budgetMapper.toResponse(budget);
    }

    @Transactional(readOnly = true)
    public BudgetResponse getBudget(UUID id) {
        ExpenseBudget budget = findBudget(id);
        return budgetMapper.toResponse(budget);
    }

    @Transactional(readOnly = true)
    public List<BudgetResponse> getAllBudgets() {
        return budgetMapper.toResponseList(budgetRepository.findAllByOrderByPeriodStartDescCategoryAsc());
    }

    @Transactional
    public BudgetResponse updateBudget(UUID id, BudgetRequest request) {
        ExpenseBudget budget = findBudget(id);

        budgetRepository.findByCategoryAndPeriodStart(request.getCategory(), request.getPeriodStart())
                .ifPresent(existing -> {
                    if (!existing.getId().equals(id)) {
                        throw new IllegalArgumentException(
                                "Budget already exists for category " + request.getCategory()
                                + " starting " + request.getPeriodStart());
                    }
                });

        budget.setCategory(request.getCategory());
        budget.setPeriodStart(request.getPeriodStart());
        budget.setPeriodEnd(request.getPeriodEnd());
        budget.setAllocatedAmount(request.getAllocatedAmount());

        budget = budgetRepository.save(budget);
        log.info("Budget {} updated", budget.getId());
        return budgetMapper.toResponse(budget);
    }

    @Transactional
    public void addSpending(ExpenseCategory category, LocalDate dateIncurred, BigDecimal amount) {
        ExpenseBudget matchingBudget = budgetRepository.findByCategoryAndDate(category, dateIncurred).orElse(null);

        if (matchingBudget == null) {
            log.debug("No budget found for category {} on date {}", category, dateIncurred);
            return;
        }

        matchingBudget.setSpentAmount(matchingBudget.getSpentAmount().add(amount));
        budgetRepository.save(matchingBudget);

        BigDecimal utilization = matchingBudget.getSpentAmount()
                .multiply(BigDecimal.valueOf(100))
                .divide(matchingBudget.getAllocatedAmount(), 1, RoundingMode.HALF_UP);

        if (utilization.compareTo(BigDecimal.valueOf(100)) >= 0) {
            log.warn("Budget exceeded for category {}: spent LKR {} of allocated LKR {} ({}%)",
                    category, matchingBudget.getSpentAmount(), matchingBudget.getAllocatedAmount(), utilization);
        } else if (utilization.compareTo(BigDecimal.valueOf(80)) >= 0) {
            log.warn("Budget nearly exhausted for category {}: spent LKR {} of allocated LKR {} ({}%)",
                    category, matchingBudget.getSpentAmount(), matchingBudget.getAllocatedAmount(), utilization);
        }
    }

    private ExpenseBudget findBudget(UUID id) {
        return budgetRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ExpenseBudget", "id", id));
    }
}
