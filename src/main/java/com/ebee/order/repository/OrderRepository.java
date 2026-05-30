package com.ebee.order.repository;

import com.ebee.order.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {

    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.agent a LEFT JOIN FETCH a.user LEFT JOIN FETCH o.customer LEFT JOIN FETCH o.createdBy LEFT JOIN FETCH o.items i LEFT JOIN FETCH i.product WHERE o.id = :id")
    Optional<Order> findByIdWithDetails(@Param("id") UUID id);

    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.agent a LEFT JOIN FETCH a.user LEFT JOIN FETCH o.customer LEFT JOIN FETCH o.createdBy LEFT JOIN FETCH o.items i LEFT JOIN FETCH i.product WHERE o.orderNumber = :orderNumber")
    Optional<Order> findByOrderNumberWithDetails(@Param("orderNumber") String orderNumber);

    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.agent a LEFT JOIN FETCH a.user LEFT JOIN FETCH o.customer LEFT JOIN FETCH o.createdBy LEFT JOIN FETCH o.items i LEFT JOIN FETCH i.product WHERE a.id = :agentId")
    List<Order> findByAgentIdWithDetails(@Param("agentId") UUID agentId);

    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.agent a LEFT JOIN FETCH a.user LEFT JOIN FETCH o.customer LEFT JOIN FETCH o.createdBy LEFT JOIN FETCH o.items i LEFT JOIN FETCH i.product")
    List<Order> findAllWithDetails();

    Optional<Order> findByOrderNumber(String orderNumber);

    List<Order> findByAgentId(UUID agentId);

    List<Order> findByCreatedById(UUID userId);

    List<Order> findByStatus(com.ebee.order.enums.OrderStatus status);

    boolean existsByOrderNumber(String orderNumber);
}
