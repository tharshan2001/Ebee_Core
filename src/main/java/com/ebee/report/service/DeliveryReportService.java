package com.ebee.report.service;

import com.ebee.delivery.enums.DeliveryStatus;
import com.ebee.delivery.repository.DeliveryRepository;
import com.ebee.report.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import com.ebee.common.util.DateUtils;
import com.ebee.common.util.NumberUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DeliveryReportService {

    private final DeliveryRepository deliveryRepository;

    public DeliverySummaryReport getSummary(PeriodRange range) {
        LocalDateTime start = DateUtils.toStartDateTime(range.startDate());
        LocalDateTime end = DateUtils.toEndDateTime(range.endDate());

        List<Object[]> statusCounts = deliveryRepository.countByDeliveryStatusAndDateRange(start, end);
        long total = 0, success = 0, fail = 0;
        for (Object[] row : statusCounts) {
            String status = row[0] != null ? row[0].toString() : "";
            long count = NumberUtils.toLong(row[1]);
            total += count;
            if ("DELIVERED".equals(status)) success += count;
            else if ("FAILED".equals(status) || "FAILED_PERMANENT".equals(status)) fail += count;
        }

        double successRate = total > 0 ? (double) success / total * 100 : 0.0;

        return DeliverySummaryReport.builder()
                .totalDeliveries(total)
                .successCount(success)
                .failCount(fail)
                .successRate(Math.round(successRate * 100.0) / 100.0)
                .period(range)
                .build();
    }

    public List<DeliveryByDriverReport> getByDriver(PeriodRange range) {
        LocalDateTime start = DateUtils.toStartDateTime(range.startDate());
        LocalDateTime end = DateUtils.toEndDateTime(range.endDate());

        List<Object[]> results = deliveryRepository.findDeliveryStatsByDriver(start, end);
        List<DeliveryByDriverReport> list = new ArrayList<>();

        for (Object[] row : results) {
            UUID driverId = (UUID) row[0];
            String name = (String) row[1];
            long total = NumberUtils.toLong(row[2]);
            long success = NumberUtils.toLong(row[3]);
            long fail = NumberUtils.toLong(row[4]);
            double rate = total > 0 ? (double) success / total * 100 : 0.0;

            list.add(DeliveryByDriverReport.builder()
                    .driverId(driverId)
                    .driverName(name != null ? name : "")
                    .totalDeliveries(total)
                    .successCount(success)
                    .failCount(fail)
                    .successRate(Math.round(rate * 100.0) / 100.0)
                    .build());
        }
        return list;
    }

    public List<DeliveryFailureReasonReport> getFailureReasons(PeriodRange range) {
        LocalDateTime start = DateUtils.toStartDateTime(range.startDate());
        LocalDateTime end = DateUtils.toEndDateTime(range.endDate());

        List<Object[]> results = deliveryRepository.findFailureReasonBreakdown(start, end);
        long total = results.stream().mapToLong(r -> NumberUtils.toLong(r[1])).sum();
        List<DeliveryFailureReasonReport> list = new ArrayList<>();

        for (Object[] row : results) {
            String reason = row[0] != null ? row[0].toString() : "No reason provided";
            long count = NumberUtils.toLong(row[1]);
            double pct = total > 0 ? (double) count / total * 100 : 0.0;

            list.add(DeliveryFailureReasonReport.builder()
                    .reason(reason)
                    .count(count)
                    .percentage(Math.round(pct * 100.0) / 100.0)
                    .build());
        }
        return list;
    }

}
