package co.empresa.vivaeventos.payments.domain.service;

import co.empresa.vivaeventos.payments.domain.model.dto.CreatePaymentRequest;
import co.empresa.vivaeventos.payments.domain.model.dto.PaymentResponse;
import co.empresa.vivaeventos.payments.domain.model.dto.RefundRequest;
import co.empresa.vivaeventos.payments.domain.model.dto.WebhookPayload;

import java.util.UUID;

public interface IPaymentService {

    PaymentResponse createPaymentIntent(CreatePaymentRequest request, String idempotencyKey, String userId, String userEmail);

    PaymentResponse getPaymentById(UUID id);

    PaymentResponse getPaymentByOrderId(UUID orderId);

    PaymentResponse confirmPayment(String paymentIntentId);

    PaymentResponse handleWebhook(WebhookPayload payload);

    PaymentResponse processRefund(UUID paymentId, String idempotencyKey, RefundRequest request);

    PaymentResponse retryPayment(UUID orderId, String userId, String userEmail);

    PaymentResponse cancelPayment(UUID orderId);

}
