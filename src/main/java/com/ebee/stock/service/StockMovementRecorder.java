package com.ebee.stock.service;

import com.ebee.common.security.SecurityService;
import com.ebee.inventory.entity.Inventory;
import com.ebee.stock.entity.Stock;
import com.ebee.stock.enums.StockMovementType;
import com.ebee.stock.repository.StockRepository;
import com.ebee.user.entity.User;
import com.ebee.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StockMovementRecorder {

    private final StockRepository stockRepository;
    private final SecurityService securityService;
    private final UserRepository userRepository;

    public Stock record(Inventory inventory, Integer quantityChange,
                        Integer quantityBefore, Integer quantityAfter,
                        StockMovementType movementType, String notes) {
        UUID userId = securityService.getCurrentUserId();
        User createdBy = userId != null ? userRepository.findById(userId).orElse(null) : null;

        Stock stock = Stock.builder()
                .inventory(inventory)
                .quantityChange(quantityChange)
                .quantityBefore(quantityBefore)
                .quantityAfter(quantityAfter)
                .movementType(movementType)
                .notes(notes)
                .createdBy(createdBy)
                .build();

        return stockRepository.save(stock);
    }
}
