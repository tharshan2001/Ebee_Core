package com.ebee.report.service;

import com.ebee.delivery.repository.DeliveryRepository;
import com.ebee.driver.entity.Driver;
import com.ebee.driver.enums.DriverAvailability;
import com.ebee.driver.repository.DriverRepository;
import com.ebee.report.dto.DriverPerformanceReport;
import com.ebee.report.dto.DriverSummaryReport;
import com.ebee.report.dto.PeriodRange;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DriverReportService {

    private final DriverRepository driverRepository;
    private final DeliveryRepository deliveryRepository;

    public DriverSummaryReport getSummary() {
        List<Driver> all = driverRepository.findAll();
        long active = all.stream().filter(d -> Boolean.TRUE.equals(d.getIsActive())).count();
        long suspended = all.stream().filter(d -> d.getAvailability() == DriverAvailability.SUSPENDED).count();
        long offline = all.stream().filter(d -> d.getAvailability() == DriverAvailability.OFFLINE).count();
        long onTrip = all.stream().filter(d -> d.getAvailability() == DriverAvailability.ON_TRIP).count();

        return DriverSummaryReport.builder()
                .totalDrivers((long) all.size())
                .activeCount(active)
                .suspendedCount(suspended)
                .offlineCount(offline)
                .onTripCount(onTrip)
                .build();
    }

    public List<DriverPerformanceReport> getPerformance(PeriodRange range) {
        LocalDateTime start = toStartDateTime(range.startDate());
        LocalDateTime end = toEndDateTime(range.endDate());

        List<Object[]> results = deliveryRepository.findDeliveryStatsByDriver(start, end);
        List<DriverPerformanceReport> list = new ArrayList<>();

        for (Object[] row : results) {
            UUID driverId = (UUID) row[0];
            String name = (String) row[1];
            long total = toLong(row[2]);
            long success = toLong(row[3]);
            long fail = toLong(row[4]);
            double rate = total > 0 ? (double) success / total * 100 : 0.0;

            list.add(DriverPerformanceReport.builder()
                    .driverId(driverId)
                    .driverName(name != null ? name : "")
                    .deliveriesAssigned(total)
                    .deliveriesCompleted(success)
                    .deliveriesFailed(fail)
                    .successRate(Math.round(rate * 100.0) / 100.0)
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
