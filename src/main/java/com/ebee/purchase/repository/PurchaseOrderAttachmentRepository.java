package com.ebee.purchase.repository;

import com.ebee.purchase.entity.PurchaseOrderAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PurchaseOrderAttachmentRepository extends JpaRepository<PurchaseOrderAttachment, UUID> {

    List<PurchaseOrderAttachment> findByPurchaseOrderId(UUID purchaseOrderId);

    Optional<PurchaseOrderAttachment> findByIdAndPurchaseOrderId(UUID id, UUID purchaseOrderId);
}
