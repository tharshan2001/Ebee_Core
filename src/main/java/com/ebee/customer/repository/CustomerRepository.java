package com.ebee.customer.repository;

import com.ebee.customer.entity.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    Optional<Customer> findByPhoneNumber(String phoneNumber);

    boolean existsByPhoneNumber(String phoneNumber);

    @Modifying
    @Query("UPDATE Customer c SET c.totalOrders = c.totalOrders + 1 WHERE c.id = :id")
    int incrementTotalOrders(@Param("id") UUID id);

    @Modifying
    @Query("UPDATE Customer c SET c.totalOrders = c.totalOrders - 1 WHERE c.id = :id")
    int decrementTotalOrders(@Param("id") UUID id);

    @Query("SELECT COUNT(c) FROM Customer c WHERE c.createdAt >= :start AND c.createdAt < :end")
    Long countNewCustomersSince(@Param("start") java.time.LocalDateTime start, @Param("end") java.time.LocalDateTime end);

    @Query("SELECT COALESCE(c.address.district, 'Unknown'), COUNT(c) FROM Customer c GROUP BY c.address.district ORDER BY COUNT(c) DESC")
    List<Object[]> findCustomerDistrictDistribution();

    Page<Customer> findAll(Pageable pageable);

    @Query("SELECT c FROM Customer c LEFT JOIN FETCH c.address WHERE c.phoneNumber LIKE %:search% OR c.firstName LIKE %:search% OR c.lastName LIKE %:search% OR c.email LIKE %:search%")
    Page<Customer> findBySearchTerm(@Param("search") String search, Pageable pageable);

    @Query("SELECT c FROM Customer c LEFT JOIN FETCH c.address WHERE c.phoneNumber LIKE %:search% OR c.firstName LIKE %:search% OR c.lastName LIKE %:search% OR c.email LIKE %:search%")
    List<Customer> findBySearchTerm(@Param("search") String search);
}
