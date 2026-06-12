package com.ebee.expense.mapper;

import com.ebee.expense.dto.BudgetResponse;
import com.ebee.expense.entity.ExpenseBudget;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ExpenseBudgetMapper {

    BudgetResponse toResponse(ExpenseBudget budget);

    List<BudgetResponse> toResponseList(List<ExpenseBudget> budgets);
}
