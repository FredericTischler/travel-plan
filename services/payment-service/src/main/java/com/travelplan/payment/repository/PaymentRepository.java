package com.travelplan.payment.repository;

import com.travelplan.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data access for {@link Payment}.
 *
 * All query methods filter on {@code deleted_at IS NULL} to honour the
 * soft-delete contract. No business logic lives here — only data access.
 */
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    /**
     * Find a non-deleted payment by id.
     */
    @Query("SELECT p FROM Payment p WHERE p.id = :id AND p.deletedAt IS NULL")
    Optional<Payment> findActiveById(@Param("id") UUID id);

    /**
     * Return all non-deleted payments.
     */
    @Query("SELECT p FROM Payment p WHERE p.deletedAt IS NULL")
    List<Payment> findAllActive();
}