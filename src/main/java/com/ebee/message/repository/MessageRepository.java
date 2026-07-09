package com.ebee.message.repository;

import com.ebee.message.entity.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface MessageRepository extends JpaRepository<Message, UUID> {

    @Query("SELECT m FROM Message m LEFT JOIN FETCH m.sentBy WHERE m.sentBy.id = :sentById ORDER BY m.sentAt DESC")
    Page<Message> findBySentByIdOrderBySentAtDesc(@Param("sentById") UUID sentById, Pageable pageable);
}
