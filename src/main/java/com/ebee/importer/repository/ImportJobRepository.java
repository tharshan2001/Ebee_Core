package com.ebee.importer.repository;

import com.ebee.importer.entity.ImportJob;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ImportJobRepository extends JpaRepository<ImportJob, UUID> {
    Page<ImportJob> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
