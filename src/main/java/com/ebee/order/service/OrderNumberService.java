package com.ebee.order.service;

import com.ebee.common.enums.District;
import com.ebee.order.enums.OrderSource;
import com.ebee.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class OrderNumberService {

    private final OrderRepository orderRepository;

    public String generateOrderNumber(District district, OrderSource source) {
        String datePart = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String type = source == OrderSource.DIRECT ? "D" : "A";
        String prefix = district.name() + "-" + type;
        String maxOrderNumber = orderRepository.findMaxOrderNumberByPattern(prefix + "-" + datePart + "-%");
        int nextSeq = 1;
        if (maxOrderNumber != null) {
            String[] parts = maxOrderNumber.split("-");
            nextSeq = Integer.parseInt(parts[parts.length - 1]) + 1;
        }
        return String.format("%s-%s-%03d", prefix, datePart, nextSeq);
    }
}
