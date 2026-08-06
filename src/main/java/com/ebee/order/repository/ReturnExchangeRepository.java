package com.ebee.order.repository;

import com.ebee.order.entity.ReturnExchangeRequest;
import com.ebee.order.enums.ReturnExchangeStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReturnExchangeRepository extends JpaRepository<ReturnExchangeRequest, UUID> {

    Optional<ReturnExchangeRequest> findByOrderId(UUID orderId);

    boolean existsByOrderId(UUID orderId);

    @Query("SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END FROM ReturnExchangeRequest r WHERE r.order.id = :orderId AND r.status <> 'REJECTED'")
    boolean existsActiveByOrderId(@Param("orderId") UUID orderId);

    List<ReturnExchangeRequest> findByStatusOrderByCreatedAtDesc(ReturnExchangeStatus status);

    Page<ReturnExchangeRequest> findByStatus(ReturnExchangeStatus status, Pageable pageable);

    @Query("SELECT r FROM ReturnExchangeRequest r WHERE r.status = :status ORDER BY r.createdAt DESC")
    List<ReturnExchangeRequest> findPendingReviews(@Param("status") ReturnExchangeStatus status);

    @Query("SELECT COUNT(r) FROM ReturnExchangeRequest r WHERE r.status = :status")
    long countByStatus(@Param("status") ReturnExchangeStatus status);
}
