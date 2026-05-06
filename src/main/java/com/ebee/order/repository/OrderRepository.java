package com.ebee.order.repository;

import com.ebee.order.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {

    Optional<Order> findByOrderNumber(String orderNumber);

    List<Order> findByAgentId(UUID agentId);

    List<Order> findByCreatedById(UUID userId);

    List<Order> findByStatus(com.ebee.order.enums.OrderStatus status);

    boolean existsByOrderNumber(String orderNumber);
}
