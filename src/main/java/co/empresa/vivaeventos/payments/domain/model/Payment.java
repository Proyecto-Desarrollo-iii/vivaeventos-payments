package co.empresa.vivaeventos.payments.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments", uniqueConstraints = {
    @UniqueConstraint(name = "uk_payments_idempotency_key", columnNames = "idempotency_key"),
    @UniqueConstraint(name = "uk_payments_refund_idempotency_key", columnNames = "refund_idempotency_key")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "idempotency_key", length = 255)
    private String idempotencyKey;

    @Version
    @Column(name = "version")
    private Long version;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "user_email")
    private String userEmail;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", length = 3)
    @Builder.Default
    private String currency = "COP";

    @Column(name = "payment_method", length = 50)
    private String paymentMethod;

    @Column(name = "payment_provider", length = 50)
    @Builder.Default
    private String paymentProvider = "STRIPE";

    @Column(name = "provider_reference", length = 255)
    private String providerReference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private PaymentStatus status;

    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    @Column(name = "processor_response", columnDefinition = "TEXT")
    private String processorResponse;

    @Column(name = "callback_received_at")
    private Instant callbackReceivedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "refund_id", length = 255)
    private String refundId;

    @Column(name = "refund_idempotency_key", length = 255)
    private String refundIdempotencyKey;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public enum PaymentStatus {
        PENDING,
        PROCESSING,
        APPROVED,
        DECLINED,
        FAILED,
        TIMEOUT,
        REFUNDED,
        CANCELLED,
        PAID
    }
}