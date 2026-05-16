package co.empresa.vivaeventos.payments.domain.model.Dto;

import co.empresa.vivaeventos.payments.domain.model.Payment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponse {

    private UUID id;
    private UUID orderId;
    private UUID userId;
    private String providerReference;
    private String paymentProvider;
    private BigDecimal amount;
    private String currency;
    private Payment.PaymentStatus status;
    private String paymentMethod;
    private String failureReason;
    private String processorResponse;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant processedAt;
    private String refundId;
    private String clientSecret;

    public static PaymentResponse fromEntity(Payment payment) {
        return fromEntity(payment, null);
    }

    public static PaymentResponse fromEntity(Payment payment, String clientSecret) {
        return PaymentResponse.builder()
                .id(payment.getId())
                .orderId(payment.getOrderId())
                .userId(payment.getUserId())
                .providerReference(payment.getProviderReference())
                .paymentProvider(payment.getPaymentProvider())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .status(payment.getStatus())
                .paymentMethod(payment.getPaymentMethod())
                .failureReason(payment.getFailureReason())
                .processorResponse(payment.getProcessorResponse())
                .createdAt(payment.getCreatedAt())
                .updatedAt(payment.getUpdatedAt())
                .processedAt(payment.getProcessedAt())
                .refundId(payment.getRefundId())
                .clientSecret(clientSecret)
                .build();
    }
}