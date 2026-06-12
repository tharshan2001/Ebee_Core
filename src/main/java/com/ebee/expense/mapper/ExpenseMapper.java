package com.ebee.expense.mapper;

import com.ebee.expense.dto.ExpenseResponse;
import com.ebee.expense.entity.Expense;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ExpenseMapper {

    @Mapping(target = "createdById", source = "createdBy.id")
    @Mapping(target = "createdByEmail", source = "createdBy.email")
    @Mapping(target = "createdByName", expression = "java(expense.getCreatedBy().getFirstName() + \" \" + expense.getCreatedBy().getLastName())")
    @Mapping(target = "driverId", source = "driver.id")
    @Mapping(target = "driverName", expression = "java(expense.getDriver() != null ? expense.getDriver().getUser().getFirstName() + \" \" + expense.getDriver().getUser().getLastName() : null)")
    @Mapping(target = "approvedById", source = "approvedBy.id")
    @Mapping(target = "approvedByName", expression = "java(expense.getApprovedBy() != null ? expense.getApprovedBy().getFirstName() + \" \" + expense.getApprovedBy().getLastName() : null)")
    ExpenseResponse toResponse(Expense expense);

    List<ExpenseResponse> toResponseList(List<Expense> expenses);
}
