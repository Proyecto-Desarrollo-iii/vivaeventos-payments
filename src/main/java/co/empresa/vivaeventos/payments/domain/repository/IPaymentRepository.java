package co.empresa.vivaeventos.payments.domain.repository;

import co.empresa.vivaeventos.payments.domain.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
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

    boolean existsByOrderIdAndStatusIn(UUID orderId, Payment.PaymentStatus... statuses);

    @Query("SELECT p FROM Payment p WHERE p.status IN :statuses AND p.createdAt < :cutoffTime")
    List<Payment> findPaymentsTimedOut(
            @Param("statuses") List<Payment.PaymentStatus> statuses,
            @Param("cutoffTime") Instant cutoffTime);

}