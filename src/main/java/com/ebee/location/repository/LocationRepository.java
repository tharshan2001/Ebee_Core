package com.ebee.location.repository;

import com.ebee.location.entity.Location;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LocationRepository extends JpaRepository<Location, UUID> {

    @Query("SELECT l FROM Location l WHERE l.defaultLocation = true")
    Optional<Location> findDefault();

    List<Location> findAllByOrderByNameAsc();

    boolean existsByCodeIgnoreCase(String code);

    @Modifying
    @Query("UPDATE Location l SET l.defaultLocation = false WHERE l.defaultLocation = true")
    void clearDefault();

    Optional<Location> findByCodeIgnoreCase(String code);
}
