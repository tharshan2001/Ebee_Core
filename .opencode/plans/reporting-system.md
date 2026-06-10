# Complete Reporting System with Periods

## Architecture
- **Package**: `com.ebee.report`
- **Controller**: `ReportsController.java` (`/api/reports`) — single entry point, all endpoints `@PreAuthorize("hasRole('ADMIN')")`
- **Services**: 11 sub-services (SalesReportService, OrderReportService, DeliveryReportService, CommissionReportService, AgentReportService, DriverReportService, CustomerReportService, ProductReportService, InventoryReportService, FinancialReportService, ActivityReportService)
- **DTOs**: One record/class per report type in `com.ebee.report.dto`
- **Utility**: `PeriodUtils` — resolves `period` string + optional `startDate`/`endDate` → `PeriodRange`

## Files to Create (36 total)

### Foundation (Phase 1)
| File | Description |
|------|-------------|
| `report/dto/PeriodRange.java` | Record with `startDate`, `endDate`, static `ALL_TIME` constant |
| `report/util/PeriodUtils.java` | `resolve(period, startDate, endDate)` + `previousPeriod(range)` |
| `report/ReportsController.java` | All 27 endpoints mapped, delegating to sub-services |

### DTOs (24 files)
| File | Fields |
|------|--------|
| `report/dto/SalesSummaryReport.java` | totalRevenue, totalOrders, avgOrderValue, totalCommission, previousPeriod block |
| `report/dto/SalesTrendReport.java` | period, totalOrders, totalRevenue, totalCommission |
| `report/dto/SalesBySourceReport.java` | agent: {revenue, orders}, direct: {revenue, orders} |
| `report/dto/SalesByStatusReport.java` | status, count, percentage |
| `report/dto/OrderSummaryReport.java` | totalOrders, pendingCount, shippedCount, deliveredCount, cancelledCount, returnedCount, cancellationRate |
| `report/dto/TopCustomerReport.java` | customerId, customerName, phoneNumber, orderCount, totalSpent, avgOrderValue |
| `report/dto/AvgDeliveryTimeReport.java` | avgHours, minHours, maxHours, deliveredCount |
| `report/dto/DeliverySummaryReport.java` | totalDeliveries, successCount, failCount, successRate, avgDeliveryHours |
| `report/dto/DeliveryByDriverReport.java` | driverId, driverName, totalDeliveries, successCount, failCount, successRate |
| `report/dto/DeliveryFailureReasonReport.java` | reason, count, percentage |
| `report/dto/CommissionSummaryReport.java` | totalApproved, totalPaid, totalProcessing, totalPending, outstandingLiability |
| `report/dto/CommissionByAgentReport.java` | agentId, agentCode, agentName, orderCount, totalCommission, paidAmount, pendingAmount |
| `report/dto/CommissionPendingAgingReport.java` | ageBucket (0-7d, 7-14d, 14-30d, 30+d), count, totalAmount |
| `report/dto/AgentSummaryReport.java` | totalActive, totalInactive, newRegistrations, totalAgents |
| `report/dto/AgentByDistrictReport.java` | district, agentCount, activeCount |
| `report/dto/DriverSummaryReport.java` | totalDrivers, activeCount, suspendedCount, offlineCount, onTripCount |
| `report/dto/DriverPerformanceReport.java` | driverId, driverName, deliveriesAssigned, deliveriesCompleted, deliveriesFailed, successRate, avgDeliveryHours |
| `report/dto/CustomerSummaryReport.java` | totalCustomers, newCustomersThisPeriod, repeatOrderCount, repeatRate |
| `report/dto/CustomerByDistrictReport.java` | district, customerCount |
| `report/dto/ProductSummaryReport.java` | totalProducts, activeCount, inactiveCount, categoryCount |
| `report/dto/TopSellerReport.java` | productId, productName, itemCode, category, quantitySold, revenue, rank |
| `report/dto/ProductByCategoryReport.java` | category, productCount, quantitySold, revenue |
| `report/dto/InventorySummaryReport.java` | totalStockQty, totalStockValue, lowStockCount, outOfStockCount |
| `report/dto/LowStockReport.java` | productId, productName, itemCode, currentStock, reorderLevel, shortfall |
| `report/dto/FinancialReport.java` | totalRevenue, totalCostPrice, grossProfit, grossMargin, totalCommissions, netProfit |
| `report/dto/CommissionLiabilityReport.java` | totalUnpaid, approvedAmount, processingAmount, approvedCount, processingCount |
| `report/dto/ActivitySummaryReport.java` | totalActions, actionBreakdown: [{action, count, uniqueUsers}] |

### Services (11 files)
| File | Key queries |
|------|------------|
| `report/service/SalesReportService.java` | Uses OrderRepository, CommissionRepository, OrderItemRepository — reuses existing queries, adds new ones for source/status breakdown |
| `report/service/OrderReportService.java` | Uses OrderRepository — needs new queries for status breakdown, top customers, delivery time |
| `report/service/DeliveryReportService.java` | Uses OrderRepository (delivery fields) — needs new queries for driver stats, failure reasons |
| `report/service/CommissionReportService.java` | Uses CommissionRepository, AgentRepository — reuses many existing sum queries |
| `report/service/AgentReportService.java` | Uses AgentRepository, UserRepository — needs new queries for new-registrations count |
| `report/service/DriverReportService.java` | Uses DriverRepository — needs new queries for performance |
| `report/service/CustomerReportService.java` | Uses CustomerRepository, OrderRepository — needs new queries for repeat rate |
| `report/service/ProductReportService.java` | Uses ProductRepository, OrderItemRepository — reuses existing sales-by-product query |
| `report/service/InventoryReportService.java` | Uses InventoryRepository, ProductRepository — reuses low-stock query |
| `report/service/FinancialReportService.java` | Uses OrderRepository, OrderItemRepository, CommissionRepository — cost price aggregation |
| `report/service/ActivityReportService.java` | Uses AuditLogRepository — new query for action grouping |

### Repository Queries to Add (new @Query methods)
| Repository | New Method | Description |
|------------|-----------|-------------|
| `OrderRepository` | `countByStatusInAndDateRange` | Order status breakdown |
| `OrderRepository` | `findSalesBySource` | AGENT vs DIRECT revenue/orders |
| `OrderRepository` | `findTopCustomersByRevenue` | Top customers by total spent |
| `OrderRepository` | `findAvgDeliveryTime` | Average hours from created → delivered |
| `OrderRepository` | `findOrderStatusDistribution` | Status distribution count |
| `OrderRepository` | `findSalesByStatusAndDateRange` | Revenue by order status |
| `DeliveryRepository` | `countDeliveriesByDriver` | Per-driver delivery stats |
| `DeliveryRepository` | `findFailureReasonBreakdown` | Failure reason group by count |
| `DeliveryRepository` | `countByDeliveryStatusAndDateRange` | Delivery status counts |
| `DeliveryRepository` | `findSuccessRateByDriver` | Per-driver success/fail counts |
| `CustomerRepository` | `countNewCustomersSince` | New customer registrations |
| `CustomerRepository` | `findCustomerDistrictDistribution` | Customer count by district |
| `AgentRepository` | `countNewAgentsSince` | New agent registrations |
| `AgentRepository` | `findAgentDistrictDistribution` | Agent count by district |
| `DriverRepository` | `findDriverPerformanceStats` | Per-driver delivery/completion stats |
| `ProductRepository` | `countActiveProducts` | Active product count |
| `AuditLogRepository` | `findActionSummaryByDateRange` | Action type breakdown |

## Implementation Order
Phase 1: Foundation → Phase 2: Sales → Phase 3: Orders → Phase 4: Delivery → Phase 5: Commission → Phase 6: Agent → Phase 7: Driver → Phase 8: Customer → Phase 9: Product → Phase 10: Inventory → Phase 11: Financial → Phase 12: Activity → Docs
