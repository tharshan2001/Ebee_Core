package com.ebee.message.repository;

import com.ebee.message.entity.MessageRecipient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface MessageRecipientRepository extends JpaRepository<MessageRecipient, UUID> {

    Page<MessageRecipient> findByMessageId(UUID messageId, Pageable pageable);

    long countByMessageId(UUID messageId);
}
