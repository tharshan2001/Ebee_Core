package com.ebee.report.controller;

import com.ebee.common.response.ApiResponse;
import com.ebee.report.dto.*;
import com.ebee.report.service.*;
import com.ebee.report.util.PeriodUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class ReportsController {

    private final SalesReportService salesReportService;
    private final OrderReportService orderReportService;
    private final DeliveryReportService deliveryReportService;
    private final CommissionReportService commissionReportService;
    private final AgentReportService agentReportService;
    private final DriverReportService driverReportService;
    private final CustomerReportService customerReportService;
    private final ProductReportService productReportService;
    private final InventoryReportService inventoryReportService;
    private final FinancialReportService financialReportService;
    private final ActivityReportService activityReportService;
    private final ReportsService reportsService;

    @GetMapping("/sales/summary")
    public ResponseEntity<ApiResponse<SalesSummaryReport>> getSalesSummary(
            @RequestParam(defaultValue = "this_month") String period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        PeriodRange range = PeriodUtils.resolve(period, startDate, endDate);
        return ResponseEntity.ok(ApiResponse.success("Sales summary retrieved",
                salesReportService.getSummary(range)));
    }

    @GetMapping("/sales/trend")
    public ResponseEntity<ApiResponse<List<SalesTrendReport>>> getSalesTrend(
            @RequestParam(defaultValue = "this_year") String period,
            @RequestParam(defaultValue = "monthly") String granularity,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        PeriodRange range = PeriodUtils.resolve(period, startDate, endDate);
        return ResponseEntity.ok(ApiResponse.success("Sales trend retrieved",
                salesReportService.getTrend(range, granularity)));
    }

    @GetMapping("/sales/by-source")
    public ResponseEntity<ApiResponse<SalesBySourceReport>> getSalesBySource(
            @RequestParam(defaultValue = "this_month") String period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        PeriodRange range = PeriodUtils.resolve(period, startDate, endDate);
        return ResponseEntity.ok(ApiResponse.success("Sales by source retrieved",
                salesReportService.getBySource(range)));
    }

    @GetMapping("/sales/by-status")
    public ResponseEntity<ApiResponse<List<SalesByStatusReport>>> getSalesByStatus(
            @RequestParam(defaultValue = "this_month") String period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        PeriodRange range = PeriodUtils.resolve(period, startDate, endDate);
        return ResponseEntity.ok(ApiResponse.success("Sales by status retrieved",
                salesReportService.getByStatus(range)));
    }

    @GetMapping("/orders/summary")
    public ResponseEntity<ApiResponse<OrderSummaryReport>> getOrderSummary(
            @RequestParam(defaultValue = "this_month") String period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        PeriodRange range = PeriodUtils.resolve(period, startDate, endDate);
        return ResponseEntity.ok(ApiResponse.success("Order summary retrieved",
                orderReportService.getSummary(range)));
    }

    @GetMapping("/orders/top-customers")
    public ResponseEntity<ApiResponse<List<TopCustomerReport>>> getTopCustomers(
            @RequestParam(defaultValue = "this_month") String period,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        PeriodRange range = PeriodUtils.resolve(period, startDate, endDate);
        return ResponseEntity.ok(ApiResponse.success("Top customers retrieved",
                orderReportService.getTopCustomers(range, limit)));
    }

    @GetMapping("/orders/avg-delivery-time")
    public ResponseEntity<ApiResponse<AvgDeliveryTimeReport>> getAvgDeliveryTime(
            @RequestParam(defaultValue = "this_month") String period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        PeriodRange range = PeriodUtils.resolve(period, startDate, endDate);
        return ResponseEntity.ok(ApiResponse.success("Average delivery time retrieved",
                orderReportService.getAvgDeliveryTime(range)));
    }

    @GetMapping("/deliveries/summary")
    public ResponseEntity<ApiResponse<DeliverySummaryReport>> getDeliverySummary(
            @RequestParam(defaultValue = "this_month") String period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        PeriodRange range = PeriodUtils.resolve(period, startDate, endDate);
        return ResponseEntity.ok(ApiResponse.success("Delivery summary retrieved",
                deliveryReportService.getSummary(range)));
    }

    @GetMapping("/deliveries/by-driver")
    public ResponseEntity<ApiResponse<List<DeliveryByDriverReport>>> getDeliveryByDriver(
            @RequestParam(defaultValue = "this_month") String period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        PeriodRange range = PeriodUtils.resolve(period, startDate, endDate);
        return ResponseEntity.ok(ApiResponse.success("Delivery by driver retrieved",
                deliveryReportService.getByDriver(range)));
    }

    @GetMapping("/deliveries/failure-reasons")
    public ResponseEntity<ApiResponse<List<DeliveryFailureReasonReport>>> getDeliveryFailureReasons(
            @RequestParam(defaultValue = "this_month") String period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        PeriodRange range = PeriodUtils.resolve(period, startDate, endDate);
        return ResponseEntity.ok(ApiResponse.success("Delivery failure reasons retrieved",
                deliveryReportService.getFailureReasons(range)));
    }

    @GetMapping("/commissions/summary")
    public ResponseEntity<ApiResponse<CommissionSummaryReport>> getCommissionSummary(
            @RequestParam(defaultValue = "this_month") String period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        PeriodRange range = PeriodUtils.resolve(period, startDate, endDate);
        return ResponseEntity.ok(ApiResponse.success("Commission summary retrieved",
                commissionReportService.getSummary(range)));
    }

    @GetMapping("/commissions/by-agent")
    public ResponseEntity<ApiResponse<List<CommissionByAgentReport>>> getCommissionByAgent(
            @RequestParam(defaultValue = "this_month") String period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        PeriodRange range = PeriodUtils.resolve(period, startDate, endDate);
        return ResponseEntity.ok(ApiResponse.success("Commission by agent retrieved",
                commissionReportService.getByAgent(range)));
    }

    @GetMapping("/commissions/pending-aging")
    public ResponseEntity<ApiResponse<List<CommissionPendingAgingReport>>> getCommissionPendingAging(
            @RequestParam(defaultValue = "this_month") String period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        PeriodRange range = PeriodUtils.resolve(period, startDate, endDate);
        return ResponseEntity.ok(ApiResponse.success("Commission pending aging retrieved",
                commissionReportService.getPendingAging(range)));
    }

    @GetMapping("/agents/summary")
    public ResponseEntity<ApiResponse<AgentSummaryReport>> getAgentSummary(
            @RequestParam(defaultValue = "this_month") String period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        PeriodRange range = PeriodUtils.resolve(period, startDate, endDate);
        return ResponseEntity.ok(ApiResponse.success("Agent summary retrieved",
                agentReportService.getSummary(range)));
    }

    @GetMapping("/agents/by-district")
    public ResponseEntity<ApiResponse<List<AgentByDistrictReport>>> getAgentByDistrict() {
        return ResponseEntity.ok(ApiResponse.success("Agent by district retrieved",
                agentReportService.getByDistrict()));
    }

    @GetMapping("/drivers/summary")
    public ResponseEntity<ApiResponse<DriverSummaryReport>> getDriverSummary() {
        return ResponseEntity.ok(ApiResponse.success("Driver summary retrieved",
                driverReportService.getSummary()));
    }

    @GetMapping("/drivers/performance")
    public ResponseEntity<ApiResponse<List<DriverPerformanceReport>>> getDriverPerformance(
            @RequestParam(defaultValue = "this_month") String period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        PeriodRange range = PeriodUtils.resolve(period, startDate, endDate);
        return ResponseEntity.ok(ApiResponse.success("Driver performance retrieved",
                driverReportService.getPerformance(range)));
    }

    @GetMapping("/customers/summary")
    public ResponseEntity<ApiResponse<CustomerSummaryReport>> getCustomerSummary(
            @RequestParam(defaultValue = "this_month") String period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        PeriodRange range = PeriodUtils.resolve(period, startDate, endDate);
        return ResponseEntity.ok(ApiResponse.success("Customer summary retrieved",
                customerReportService.getSummary(range)));
    }

    @GetMapping("/customers/by-district")
    public ResponseEntity<ApiResponse<List<CustomerByDistrictReport>>> getCustomerByDistrict() {
        return ResponseEntity.ok(ApiResponse.success("Customer by district retrieved",
                customerReportService.getByDistrict()));
    }

    @GetMapping("/products/summary")
    public ResponseEntity<ApiResponse<ProductSummaryReport>> getProductSummary() {
        return ResponseEntity.ok(ApiResponse.success("Product summary retrieved",
                productReportService.getSummary()));
    }

    @GetMapping("/products/top-sellers")
    public ResponseEntity<ApiResponse<List<TopSellerReport>>> getTopSellers(
            @RequestParam(defaultValue = "this_month") String period,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        PeriodRange range = PeriodUtils.resolve(period, startDate, endDate);
        return ResponseEntity.ok(ApiResponse.success("Top sellers retrieved",
                productReportService.getTopSellers(range, limit)));
    }

    @GetMapping("/products/by-category")
    public ResponseEntity<ApiResponse<List<ProductByCategoryReport>>> getProductByCategory(
            @RequestParam(defaultValue = "this_month") String period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        PeriodRange range = PeriodUtils.resolve(period, startDate, endDate);
        return ResponseEntity.ok(ApiResponse.success("Product by category retrieved",
                productReportService.getByCategory(range)));
    }

    @GetMapping("/inventory/summary")
    public ResponseEntity<ApiResponse<InventorySummaryReport>> getInventorySummary() {
        return ResponseEntity.ok(ApiResponse.success("Inventory summary retrieved",
                inventoryReportService.getSummary()));
    }

    @GetMapping("/inventory/low-stock")
    public ResponseEntity<ApiResponse<List<LowStockReport>>> getLowStock() {
        return ResponseEntity.ok(ApiResponse.success("Low stock items retrieved",
                inventoryReportService.getLowStock()));
    }

    @GetMapping("/financial/profit-loss")
    public ResponseEntity<ApiResponse<FinancialReport>> getProfitLoss(
            @RequestParam(defaultValue = "this_month") String period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        PeriodRange range = PeriodUtils.resolve(period, startDate, endDate);
        return ResponseEntity.ok(ApiResponse.success("Profit/loss report retrieved",
                financialReportService.getProfitLoss(range)));
    }

    @GetMapping("/financial/commission-liability")
    public ResponseEntity<ApiResponse<CommissionLiabilityReport>> getCommissionLiability() {
        return ResponseEntity.ok(ApiResponse.success("Commission liability retrieved",
                financialReportService.getCommissionLiability()));
    }

    @GetMapping("/activity/audit-summary")
    public ResponseEntity<ApiResponse<ActivitySummaryReport>> getAuditSummary(
            @RequestParam(defaultValue = "this_month") String period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        PeriodRange range = PeriodUtils.resolve(period, startDate, endDate);
        return ResponseEntity.ok(ApiResponse.success("Audit activity summary retrieved",
                activityReportService.getAuditSummary(range)));
    }

    // ── Accounting overview (was UnifiedReportsController in the old reporting package) ──

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<ReportSummaryResponse>> getSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        ReportSummaryResponse response = reportsService.getSummary(startDate, endDate);
        return ResponseEntity.ok(ApiResponse.success("Report summary retrieved successfully", response));
    }

    @GetMapping("/trial-balance")
    public ResponseEntity<ApiResponse<TrialBalanceResponse>> getTrialBalance() {
        return ResponseEntity.ok(ApiResponse.success("Trial balance retrieved successfully", reportsService.getTrialBalance()));
    }

    @GetMapping("/aging")
    public ResponseEntity<ApiResponse<AgingReportResponse>> getAgingReport(
            @org.springframework.web.bind.annotation.RequestParam(required = false) Integer page,
            @org.springframework.web.bind.annotation.RequestParam(required = false) Integer size) {
        AgingReportResponse report = size == null ? reportsService.getAgingReport()
                : reportsService.getAgingReport(com.ebee.common.util.PaginationUtils.safePage(page == null ? 0 : page),
                        com.ebee.common.util.PaginationUtils.safeSize(size));
        return ResponseEntity.ok(ApiResponse.success("Aging report retrieved successfully", report));
    }
}
