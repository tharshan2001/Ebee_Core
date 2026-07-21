package com.ebee.common.repository;

import com.ebee.common.entity.CodeSequence;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CodeSequenceRepository extends JpaRepository<CodeSequence, String> {

    default boolean tryReserve(String code) {
        try {
            save(new CodeSequence(code));
            return true;
        } catch (DataIntegrityViolationException e) {
            return false;
        }
    }
}
