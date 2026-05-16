package co.empresa.vivaeventos.payments.domain.service;

import co.empresa.vivaeventos.payments.config.OrdersClient;
import co.empresa.vivaeventos.payments.config.TicketsClient;
import co.empresa.vivaeventos.payments.domain.model.Dto.CreatePaymentRequest;
import co.empresa.vivaeventos.payments.domain.model.Dto.PaymentResponse;
import co.empresa.vivaeventos.payments.domain.model.Dto.RefundRequest;
import co.empresa.vivaeventos.payments.domain.model.Dto.WebhookPayload;
import co.empresa.vivaeventos.payments.domain.model.Payment;
import co.empresa.vivaeventos.payments.domain.repository.IPaymentRepository;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.PaymentIntentRetrieveParams;
import com.stripe.param.RefundCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceImpl implements IPaymentService {

    private final IPaymentRepository paymentRepository;
    private final OrdersClient ordersClient;
    private final TicketsClient ticketsClient;

@Override
    @Transactional
    public PaymentResponse createPaymentIntent(CreatePaymentRequest request, String userId, String userEmail) {
        log.info("Creating payment intent for order: {} by user: {}", request.getOrderId(), userId);

        if (paymentRepository.existsByOrderIdAndStatusIn(
                request.getOrderId(),
                Payment.PaymentStatus.PENDING,
                Payment.PaymentStatus.PROCESSING,
                Payment.PaymentStatus.APPROVED)) {
            throw new IllegalStateException("Payment already exists for this order");
        }

        String currency = request.getCurrency() != null ? request.getCurrency() : "COP";

        PaymentIntentCreateParams.Builder paramsBuilder = PaymentIntentCreateParams.builder()
                .setAmount(request.getAmount().multiply(java.math.BigDecimal.valueOf(100)).longValue())
                .setCurrency(currency.toLowerCase())
                .putMetadata("order_id", request.getOrderId().toString())
                .putMetadata("user_id", userId != null ? userId : "")
                .setAutomaticPaymentMethods(
                        PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                .setEnabled(true)
                        .build()
                );

        PaymentIntentCreateParams params = paramsBuilder.build();

        try {
            PaymentIntent paymentIntent = PaymentIntent.create(params);

            Payment payment = Payment.builder()
                    .orderId(request.getOrderId())
                    .userId(userId != null ? userId : request.getUserId() != null ? request.getUserId().toString() : null)
                    .userEmail(userEmail)
                    .providerReference(paymentIntent.getId())
                    .amount(request.getAmount())
                    .currency(currency)
                    .status(Payment.PaymentStatus.PENDING)
                    .paymentMethod(request.getPaymentMethod())
                    .paymentProvider("STRIPE")
                    .build();

            payment = paymentRepository.save(payment);
            log.info("Payment created with ID: {}", payment.getId());

            String clientSecret = paymentIntent.getClientSecret();
            return PaymentResponse.fromEntity(payment, clientSecret);

        } catch (StripeException e) {
            log.error("Failed to create payment intent: {}", e.getMessage());
            throw new RuntimeException("Failed to create payment: " + e.getMessage(), e);
        }
    }

    @Override
    public PaymentResponse getPaymentById(UUID id) {
        return paymentRepository.findById(id)
                .map(PaymentResponse::fromEntity)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + id));
    }

    @Override
    public PaymentResponse getPaymentByOrderId(UUID orderId) {
        return paymentRepository.findByOrderId(orderId)
                .map(PaymentResponse::fromEntity)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found for order: " + orderId));
    }

    @Override
    @Transactional
    public PaymentResponse confirmPayment(String paymentIntentId) {
        log.info("Confirming payment: {}", paymentIntentId);

        Payment payment = paymentRepository.findByProviderReference(paymentIntentId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + paymentIntentId));

        try {
            PaymentIntentRetrieveParams params = PaymentIntentRetrieveParams.builder().build();
            PaymentIntent paymentIntent = PaymentIntent.retrieve(paymentIntentId, params, null);

            Payment.PaymentStatus newStatus = mapStripeStatus(paymentIntent.getStatus());
            payment.setStatus(newStatus);

            if (newStatus == Payment.PaymentStatus.APPROVED) {
                payment.setProcessedAt(Instant.now());
                try {
                    ordersClient.updateOrderStatus(payment.getOrderId(), "PAID");
                } catch (Exception e) {
                    log.error("Failed to update order status to PAID: {}", e.getMessage());
                }
            } else if (newStatus == Payment.PaymentStatus.FAILED) {
                payment.setFailureReason(paymentIntent.getLastPaymentError() != null
                        ? paymentIntent.getLastPaymentError().getMessage()
                        : "Payment failed");
                handlePaymentFailure(payment);
            }

            payment = paymentRepository.save(payment);
            log.info("Payment confirmed with status: {}", newStatus);

            return PaymentResponse.fromEntity(payment);

        } catch (StripeException e) {
            log.error("Failed to confirm payment: {}", e.getMessage());
            throw new RuntimeException("Failed to confirm payment: " + e.getMessage(), e);
        }
    }

    private void handlePaymentFailure(Payment payment) {
        try {
            ordersClient.updateOrderStatus(payment.getOrderId(), "FAILED");
            log.info("Order {} status updated to FAILED", payment.getOrderId());
        } catch (Exception e) {
            log.error("Failed to update order status to FAILED: {}", e.getMessage());
        }
        try {
            ticketsClient.releaseTicketsByOrderWithRetry(payment.getOrderId(), 3);
        } catch (Exception e) {
            log.error("Failed to release tickets for order {}: {}", payment.getOrderId(), e.getMessage());
        }
    }

    @Override
    @Transactional
    public PaymentResponse handleWebhook(WebhookPayload payload) {
        log.info("Processing webhook event: {} for payment: {}", payload.getEventType(), payload.getPaymentIntentId());

        Optional<Payment> optionalPayment = paymentRepository.findByProviderReference(payload.getPaymentIntentId());

        if (optionalPayment.isEmpty()) {
            log.warn("Payment not found for webhook: {}", payload.getPaymentIntentId());
            return null;
        }

        Payment payment = optionalPayment.get();
        Payment.PaymentStatus previousStatus = payment.getStatus();
        payment.setStatus(mapWebhookStatus(payload.getStatus()));

        if (payment.getStatus() == Payment.PaymentStatus.APPROVED && payment.getProcessedAt() == null) {
            payment.setProcessedAt(Instant.now());
            if (previousStatus != Payment.PaymentStatus.APPROVED) {
                try {
                    ordersClient.updateOrderStatus(payment.getOrderId(), "PAID");
                } catch (Exception e) {
                    log.error("Failed to update order status to PAID: {}", e.getMessage());
                }
            }
        }

        if (payment.getStatus() == Payment.PaymentStatus.FAILED && payload.getFailureReason() != null) {
            payment.setFailureReason(payload.getFailureReason());
            if (previousStatus != Payment.PaymentStatus.FAILED) {
                handlePaymentFailure(payment);
            }
        }

        payment = paymentRepository.save(payment);
        log.info("Webhook processed, payment status: {}", payment.getStatus());

        return PaymentResponse.fromEntity(payment);
    }

    private Payment.PaymentStatus mapStripeStatus(String stripeStatus) {
        return switch (stripeStatus) {
            case "requires_payment_method", "requires_confirmation", "requires_action" -> Payment.PaymentStatus.PROCESSING;
            case "processing" -> Payment.PaymentStatus.PROCESSING;
            case "succeeded" -> Payment.PaymentStatus.APPROVED;
            case "canceled" -> Payment.PaymentStatus.CANCELLED;
            default -> Payment.PaymentStatus.PENDING;
        };
    }

    private Payment.PaymentStatus mapWebhookStatus(String webhookStatus) {
        if (webhookStatus == null) return Payment.PaymentStatus.PENDING;
        return switch (webhookStatus.toLowerCase()) {
            case "succeeded" -> Payment.PaymentStatus.APPROVED;
            case "processing" -> Payment.PaymentStatus.PROCESSING;
            case "requires_payment_method", "requires_confirmation", "requires_action" -> Payment.PaymentStatus.PROCESSING;
            case "canceled" -> Payment.PaymentStatus.CANCELLED;
            case "failed" -> Payment.PaymentStatus.FAILED;
            default -> Payment.PaymentStatus.PENDING;
        };
    }

    @Override
    @Transactional
    public PaymentResponse processRefund(UUID paymentId, RefundRequest request) {
        log.info("Processing refund for payment: {}", paymentId);

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + paymentId));

        if (payment.getStatus() != Payment.PaymentStatus.APPROVED) {
            throw new IllegalStateException("Only approved payments can be refunded");
        }

        try {
            RefundCreateParams.Builder paramsBuilder = RefundCreateParams.builder()
                    .setPaymentIntent(payment.getProviderReference());

            if (request.getAmount() != null) {
                paramsBuilder.setAmount(request.getAmount().multiply(java.math.BigDecimal.valueOf(100)).longValue());
            }

            if (request.getReason() != null) {
                paramsBuilder.setReason(RefundCreateParams.Reason.valueOf(request.getReason().toUpperCase()));
            }

            com.stripe.model.Refund refund = com.stripe.model.Refund.create(paramsBuilder.build());

            payment.setStatus(Payment.PaymentStatus.REFUNDED);
            payment.setRefundId(refund.getId());
            payment = paymentRepository.save(payment);

            log.info("Refund processed: {}", refund.getId());
            return PaymentResponse.fromEntity(payment);

        } catch (StripeException e) {
            log.error("Failed to process refund: {}", e.getMessage());
            throw new RuntimeException("Failed to process refund: " + e.getMessage(), e);
        }
    }
}