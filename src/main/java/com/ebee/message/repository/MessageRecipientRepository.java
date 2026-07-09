package com.ebee.message.repository;

import com.ebee.message.entity.MessageRecipient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface MessageRecipientRepository extends JpaRepository<MessageRecipient, UUID> {

    @Query("SELECT mr FROM MessageRecipient mr LEFT JOIN FETCH mr.message WHERE mr.message.id = :messageId")
    Page<MessageRecipient> findByMessageId(@Param("messageId") UUID messageId, Pageable pageable);

    long countByMessageId(UUID messageId);
}
