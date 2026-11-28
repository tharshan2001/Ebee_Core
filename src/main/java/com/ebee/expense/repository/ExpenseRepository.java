package com.ebee.expense.repository;

import com.ebee.expense.entity.Expense;
import com.ebee.expense.enums.ExpenseCategory;
import com.ebee.expense.enums.ExpenseStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, UUID> {

    @Query("SELECT e FROM Expense e LEFT JOIN FETCH e.createdBy LEFT JOIN FETCH e.driver d LEFT JOIN FETCH d.user LEFT JOIN FETCH e.approvedBy WHERE e.createdBy.id = :createdById ORDER BY e.createdAt DESC")
    List<Expense> findByCreatedByIdOrderByCreatedAtDesc(@Param("createdById") UUID createdById);

    @Query("SELECT e FROM Expense e LEFT JOIN FETCH e.createdBy LEFT JOIN FETCH e.driver d LEFT JOIN FETCH d.user LEFT JOIN FETCH e.approvedBy WHERE e.driver.id = :driverId ORDER BY e.createdAt DESC")
    List<Expense> findByDriverIdOrderByCreatedAtDesc(@Param("driverId") UUID driverId);

    @Query(value = "SELECT e FROM Expense e LEFT JOIN FETCH e.createdBy LEFT JOIN FETCH e.driver d LEFT JOIN FETCH d.user LEFT JOIN FETCH e.approvedBy ORDER BY e.createdAt DESC",
           countQuery = "SELECT COUNT(e) FROM Expense e")
    Page<Expense> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<Expense> findByStatusOrderByCreatedAtDesc(com.ebee.expense.enums.ExpenseStatus status, Pageable pageable);

    long countByStatus(ExpenseStatus status);

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM Expense e WHERE e.status = 'PENDING'")
    BigDecimal sumPending();

    @Query(value = "SELECT e FROM Expense e LEFT JOIN FETCH e.createdBy u LEFT JOIN FETCH e.driver d "
            + "LEFT JOIN FETCH d.user LEFT JOIN FETCH e.approvedBy "
            + "WHERE (:anyStatus = true OR e.status = :status) AND (:anyCategory = true OR e.category = :category) "
            + "AND (:anyCreator = true OR u.id = :creator) "
            + "AND (LOWER(e.description) LIKE :q OR LOWER(CONCAT(u.firstName, ' ', u.lastName)) LIKE :q)",
           countQuery = "SELECT COUNT(e) FROM Expense e LEFT JOIN e.createdBy u "
            + "WHERE (:anyStatus = true OR e.status = :status) AND (:anyCategory = true OR e.category = :category) "
            + "AND (:anyCreator = true OR u.id = :creator) "
            + "AND (LOWER(e.description) LIKE :q OR LOWER(CONCAT(u.firstName, ' ', u.lastName)) LIKE :q)")
    Page<Expense> search(@Param("anyStatus") boolean anyStatus, @Param("status") ExpenseStatus status,
                         @Param("anyCategory") boolean anyCategory, @Param("category") ExpenseCategory category,
                         @Param("anyCreator") boolean anyCreator, @Param("creator") UUID creator,
                         @Param("q") String q, Pageable pageable);

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM Expense e WHERE e.status = 'APPROVED' AND e.dateIncurred >= :start AND e.dateIncurred <= :end")
    BigDecimal sumApprovedByDateRange(@Param("start") LocalDate start, @Param("end") LocalDate end);

    @Query("SELECT e.category, COALESCE(SUM(e.amount), 0) FROM Expense e WHERE e.status = 'APPROVED' AND e.dateIncurred >= :start AND e.dateIncurred <= :end GROUP BY e.category")
    List<Object[]> sumApprovedGroupByCategory(@Param("start") LocalDate start, @Param("end") LocalDate end);

    List<Expense> findByTripId(UUID tripId);

    long countByTripId(UUID tripId);

    @Query(value = "SELECT e FROM Expense e LEFT JOIN FETCH e.createdBy LEFT JOIN FETCH e.driver d LEFT JOIN FETCH d.user LEFT JOIN FETCH e.approvedBy LEFT JOIN FETCH e.trip WHERE e.trip.id = :tripId")
    List<Expense> findByTripIdWithRelations(@Param("tripId") UUID tripId);
}
