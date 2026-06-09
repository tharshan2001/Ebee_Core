package com.ebee.sales.controller;

import com.ebee.common.enums.District;
import com.ebee.common.response.ApiResponse;
import com.ebee.sales.dto.*;
import com.ebee.sales.service.SalesService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/sales")
@RequiredArgsConstructor
public class SalesController {

    private final SalesService salesService;

    @GetMapping("/summary")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<SalesSummaryResponse>> getSalesSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        SalesSummaryResponse response = salesService.getSalesSummary(startDate, endDate);
        return ResponseEntity.ok(ApiResponse.success("Sales summary retrieved successfully", response));
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<DashboardResponse>> getDashboard(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        DashboardResponse response = salesService.getDashboard(startDate, endDate);
        return ResponseEntity.ok(ApiResponse.success("Dashboard data retrieved successfully", response));
    }

    @GetMapping("/by-agent")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<SalesByAgentResponse>>> getSalesByAgent(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        List<SalesByAgentResponse> response = salesService.getSalesByAgent(startDate, endDate);
        return ResponseEntity.ok(ApiResponse.success("Sales by agent retrieved successfully", response));
    }

    @GetMapping("/leaderboard")
    @PreAuthorize("hasAnyRole('ADMIN', 'AGENT')")
    public ResponseEntity<ApiResponse<List<AgentLeaderboardResponse>>> getLeaderboard(
            @RequestParam(defaultValue = "revenue") String metric,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) District district,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        List<AgentLeaderboardResponse> response = salesService.getLeaderboard(
                metric, limit, district, startDate, endDate);
        return ResponseEntity.ok(ApiResponse.success("Leaderboard retrieved successfully", response));
    }

    @GetMapping("/leaderboard/my-rank")
    @PreAuthorize("hasRole('AGENT')")
    public ResponseEntity<ApiResponse<List<AgentLeaderboardResponse>>> getMyRank(
            @RequestParam(defaultValue = "revenue") String metric,
            @RequestParam(required = false) District district,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        List<AgentLeaderboardResponse> response = salesService.getMyRank(
                metric, district, startDate, endDate);
        return ResponseEntity.ok(ApiResponse.success("My rank retrieved successfully", response));
    }

    @GetMapping("/by-product")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<SalesByProductResponse>>> getSalesByProduct(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        List<SalesByProductResponse> response = salesService.getSalesByProduct(startDate, endDate);
        return ResponseEntity.ok(ApiResponse.success("Sales by product retrieved successfully", response));
    }

    @GetMapping("/by-category")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<SalesByCategoryResponse>>> getSalesByCategory(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        List<SalesByCategoryResponse> response = salesService.getSalesByCategory(startDate, endDate);
        return ResponseEntity.ok(ApiResponse.success("Sales by category retrieved successfully", response));
    }

    @GetMapping("/agent/{agentId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<SalesByAgentDetailResponse>> getSalesByAgentDetail(
            @PathVariable UUID agentId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        SalesByAgentDetailResponse response = salesService.getSalesByAgentDetail(agentId, startDate, endDate);
        return ResponseEntity.ok(ApiResponse.success("Agent sales detail retrieved successfully", response));
    }

    @GetMapping("/trend/daily")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<SalesTrendResponse>>> getDailyTrend(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        List<SalesTrendResponse> response = salesService.getDailyTrend(startDate, endDate);
        return ResponseEntity.ok(ApiResponse.success("Daily sales trend retrieved successfully", response));
    }

    @GetMapping("/trend/monthly")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<SalesTrendResponse>>> getMonthlyTrend(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        List<SalesTrendResponse> response = salesService.getMonthlyTrend(startDate, endDate);
        return ResponseEntity.ok(ApiResponse.success("Monthly sales trend retrieved successfully", response));
    }

    @GetMapping("/trend/yearly")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<SalesTrendResponse>>> getYearlyTrend(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        List<SalesTrendResponse> response = salesService.getYearlyTrend(startDate, endDate);
        return ResponseEntity.ok(ApiResponse.success("Yearly sales trend retrieved successfully", response));
    }
}
