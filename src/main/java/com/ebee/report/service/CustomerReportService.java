package com.ebee.report.service;

import com.ebee.customer.repository.CustomerRepository;
import com.ebee.order.repository.OrderRepository;
import com.ebee.report.dto.CustomerByDistrictReport;
import com.ebee.report.dto.CustomerSummaryReport;
import com.ebee.report.dto.PeriodRange;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomerReportService {

    private final CustomerRepository customerRepository;
    private final OrderRepository orderRepository;

    public CustomerSummaryReport getSummary(PeriodRange range) {
        LocalDateTime start = toStartDateTime(range.startDate());
        LocalDateTime end = toEndDateTime(range.endDate());

        long totalCustomers = customerRepository.count();
        long newCustomers = customerRepository.countNewCustomersSince(start, end);
        long totalOrderCustomers = orderRepository.countOrdersByDateRange(start, end);

        long repeatCustomers = customerRepository.findAll().stream()
                .filter(c -> orderRepository.findByCustomerIdWithDetails(c.getId()).size() > 1)
                .count();

        double repeatRate = totalCustomers > 0
                ? (double) repeatCustomers / totalCustomers * 100
                : 0.0;

        return CustomerSummaryReport.builder()
                .totalCustomers(totalCustomers)
                .newCustomersThisPeriod(newCustomers)
                .repeatOrderCount(repeatCustomers)
                .repeatRate(Math.round(repeatRate * 100.0) / 100.0)
                .period(range)
                .build();
    }

    public List<CustomerByDistrictReport> getByDistrict() {
        List<Object[]> results = customerRepository.findCustomerDistrictDistribution();
        List<CustomerByDistrictReport> list = new ArrayList<>();
        for (Object[] row : results) {
            String district = row[0] instanceof Enum<?> e ? e.name() : (row[0] != null ? row[0].toString() : "Unknown");
            list.add(CustomerByDistrictReport.builder()
                    .district(district)
                    .customerCount(toLong(row[1]))
                    .build());
        }
        return list;
    }

    private LocalDateTime toStartDateTime(LocalDate date) {
        return date != null ? date.atStartOfDay() : LocalDateTime.of(1970, 1, 1, 0, 0);
    }

    private LocalDateTime toEndDateTime(LocalDate date) {
        return date != null ? date.plusDays(1).atStartOfDay() : LocalDateTime.of(9999, 12, 31, 23, 59);
    }

    private Long toLong(Object obj) {
        if (obj instanceof Number n) return n.longValue();
        return 0L;
    }
}
