package co.empresa.vivaeventos.payments.domain.repository;

import co.empresa.vivaeventos.payments.domain.model.Payment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IPaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByProviderReference(String providerReference);

    Optional<Payment> findByOrderId(UUID orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.orderId = :orderId")
    Optional<Payment> findByOrderIdWithLock(@Param("orderId") UUID orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.id = :id")
    Optional<Payment> findByIdWithLock(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.providerReference = :providerReference")
    Optional<Payment> findByProviderReferenceWithLock(@Param("providerReference") String providerReference);

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    boolean existsByOrderIdAndStatusIn(UUID orderId, Payment.PaymentStatus... statuses);

    @Query("SELECT p FROM Payment p WHERE p.status IN :statuses AND p.createdAt < :cutoffTime")
    List<Payment> findPaymentsTimedOut(
            @Param("statuses") List<Payment.PaymentStatus> statuses,
            @Param("cutoffTime") Instant cutoffTime);

}