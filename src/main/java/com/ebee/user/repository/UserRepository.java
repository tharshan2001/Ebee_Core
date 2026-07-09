package com.ebee.user.repository;

import com.ebee.user.entity.User;
import com.ebee.user.enums.RoleType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(String email, UUID id);

    List<User> findByRole(RoleType role);

    List<User> findByRoleIn(List<RoleType> roles);

    List<User> findByIsActiveTrue();
}
