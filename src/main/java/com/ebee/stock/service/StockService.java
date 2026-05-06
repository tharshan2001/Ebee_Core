package com.ebee.stock.service;

import com.ebee.common.exception.ResourceNotFoundException;
import com.ebee.common.security.SecurityService;
import com.ebee.product.entity.Product;
import com.ebee.product.repository.ProductRepository;
import com.ebee.stock.dto.StockRequest;
import com.ebee.stock.dto.StockResponse;
import com.ebee.stock.entity.Stock;
import com.ebee.stock.mapper.StockMapper;
import com.ebee.stock.repository.StockRepository;
import com.ebee.user.entity.User;
import com.ebee.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StockService {

    private final StockRepository stockRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final StockMapper stockMapper;
    private final SecurityService securityService;

    @Transactional
    public StockResponse recordStockMovement(StockRequest request) {
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", request.getProductId()));

        int quantityBefore = product.getQuantityInStock();
        int quantityAfter = quantityBefore + request.getQuantityChange();

        if (quantityAfter < 0) {
            throw new IllegalArgumentException("Insufficient stock. Current stock: " + quantityBefore);
        }

        product.setQuantityInStock(quantityAfter);
        productRepository.save(product);

        User currentUser = userRepository.findById(securityService.getCurrentUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", securityService.getCurrentUserId()));

        Stock stock = Stock.builder()
                .product(product)
                .quantityChange(request.getQuantityChange())
                .quantityBefore(quantityBefore)
                .quantityAfter(quantityAfter)
                .movementType(request.getMovementType())
                .notes(request.getNotes())
                .createdBy(currentUser)
                .build();

        Stock savedStock = stockRepository.save(stock);
        return stockMapper.toResponse(savedStock);
    }

    public List<StockResponse> getStockHistoryByProductId(UUID productId) {
        productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", productId));
        List<Stock> stocks = stockRepository.findByProductIdOrderByCreatedAtDesc(productId);
        return stockMapper.toResponseList(stocks);
    }

    public StockResponse getStockById(UUID id) {
        Stock stock = stockRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Stock", "id", id));
        return stockMapper.toResponse(stock);
    }
}
