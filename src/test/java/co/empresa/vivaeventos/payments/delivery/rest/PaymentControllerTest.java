package co.empresa.vivaeventos.payments.delivery.rest;

import co.empresa.vivaeventos.payments.config.JwtUtil;
import co.empresa.vivaeventos.payments.config.StripeConfig;
import co.empresa.vivaeventos.payments.domain.model.Dto.CreatePaymentRequest;
import co.empresa.vivaeventos.payments.domain.model.Dto.PaymentResponse;
import co.empresa.vivaeventos.payments.domain.model.Dto.RefundRequest;
import co.empresa.vivaeventos.payments.domain.model.Payment;
import co.empresa.vivaeventos.payments.domain.service.IPaymentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentController.class)
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private IPaymentService paymentService;

    @MockitoBean
    private StripeConfig stripeConfig;

    @MockitoBean
    private JwtUtil jwtUtil;

    private UUID orderId;
    private UUID paymentId;
    private final String validToken = "Bearer valid.jwt.token";
    private Claims mockClaims;

    @BeforeEach
    void setUp() {
        orderId = UUID.randomUUID();
        paymentId = UUID.randomUUID();
        mockClaims = mock(Claims.class);

        when(jwtUtil.validateToken("valid.jwt.token")).thenReturn(Optional.of(mockClaims));
        when(jwtUtil.extractUserId(mockClaims)).thenReturn("user-id-123");
        when(jwtUtil.extractEmail(mockClaims)).thenReturn("user@test.com");
    }

    @Test
    void createPayment_withoutAuth_returns401() throws Exception {
        CreatePaymentRequest request = CreatePaymentRequest.builder()
                .orderId(orderId)
                .amount(BigDecimal.valueOf(50000))
                .currency("COP")
                .build();

        mockMvc.perform(post("/api/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createPayment_withAuth_returns201() throws Exception {
        CreatePaymentRequest request = CreatePaymentRequest.builder()
                .orderId(orderId)
                .amount(BigDecimal.valueOf(50000))
                .currency("COP")
                .build();

        PaymentResponse response = PaymentResponse.builder()
                .id(paymentId)
                .orderId(orderId)
                .status(Payment.PaymentStatus.PENDING)
                .build();

        when(paymentService.createPaymentIntent(any(), any(), anyString(), anyString())).thenReturn(response);

        mockMvc.perform(post("/api/v1/payments")
                .header("Authorization", validToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(paymentId.toString()));
    }

    @Test
    void getPaymentById_withoutAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/payments/{id}", paymentId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getPaymentById_withAuth_returns200() throws Exception {
        PaymentResponse response = PaymentResponse.builder()
                .id(paymentId)
                .orderId(orderId)
                .status(Payment.PaymentStatus.APPROVED)
                .build();

        when(paymentService.getPaymentById(paymentId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/payments/{id}", paymentId)
                .header("Authorization", validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(paymentId.toString()));
    }

    @Test
    void getPaymentByOrderId_withAuth_returns200() throws Exception {
        PaymentResponse response = PaymentResponse.builder()
                .id(paymentId)
                .orderId(orderId)
                .status(Payment.PaymentStatus.APPROVED)
                .build();

        when(paymentService.getPaymentByOrderId(orderId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/payments/order/{orderId}", orderId)
                .header("Authorization", validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId.toString()));
    }

    @Test
    void confirmPayment_withAuth_returns200() throws Exception {
        PaymentResponse response = PaymentResponse.builder()
                .id(paymentId)
                .status(Payment.PaymentStatus.PAID)
                .build();

        when(paymentService.confirmPayment("pi_test_123")).thenReturn(response);

        mockMvc.perform(post("/api/v1/payments/confirm/{paymentIntentId}", "pi_test_123")
                .header("Authorization", validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));
    }

    @Test
    void refundPayment_withAuth_returns200() throws Exception {
        RefundRequest refundRequest = RefundRequest.builder()
                .reason("requested_by_customer")
                .build();

        PaymentResponse response = PaymentResponse.builder()
                .id(paymentId)
                .status(Payment.PaymentStatus.REFUNDED)
                .build();

        when(paymentService.processRefund(any(), any(), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/payments/{id}/refund", paymentId)
                .header("Authorization", validToken)
                .header("Idempotency-Key", "refund-key-001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(refundRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUNDED"));
    }

    @Test
    void refundPayment_withoutIdempotencyKey_returns400() throws Exception {
        RefundRequest refundRequest = RefundRequest.builder()
                .reason("requested_by_customer")
                .build();

        mockMvc.perform(post("/api/v1/payments/{id}/refund", paymentId)
                .header("Authorization", validToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(refundRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void retryPayment_withAuth_returns200() throws Exception {
        PaymentResponse response = PaymentResponse.builder()
                .id(paymentId)
                .status(Payment.PaymentStatus.PENDING)
                .build();

        when(paymentService.retryPayment(orderId, "user-id-123", "user@test.com")).thenReturn(response);

        mockMvc.perform(post("/api/v1/payments/order/{orderId}/retry", orderId)
                .header("Authorization", validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void retryPayment_withIllegalState_returns409() throws Exception {
        when(paymentService.retryPayment(orderId, "user-id-123", "user@test.com"))
                .thenThrow(new IllegalStateException("Payment already completed"));

        mockMvc.perform(post("/api/v1/payments/order/{orderId}/retry", orderId)
                .header("Authorization", validToken))
                .andExpect(status().isConflict());
    }

    @Test
    void retryPayment_withIllegalArgument_returns404() throws Exception {
        when(paymentService.retryPayment(orderId, "user-id-123", "user@test.com"))
                .thenThrow(new IllegalArgumentException("No payment found"));

        mockMvc.perform(post("/api/v1/payments/order/{orderId}/retry", orderId)
                .header("Authorization", validToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void cancelPayment_withAuth_returns200() throws Exception {
        PaymentResponse response = PaymentResponse.builder()
                .id(paymentId)
                .status(Payment.PaymentStatus.CANCELLED)
                .build();

        when(paymentService.cancelPayment(orderId)).thenReturn(response);

        mockMvc.perform(post("/api/v1/payments/order/{orderId}/cancel", orderId)
                .header("Authorization", validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void cancelPayment_withIllegalState_returns409() throws Exception {
        when(paymentService.cancelPayment(orderId))
                .thenThrow(new IllegalStateException("Cannot cancel"));

        mockMvc.perform(post("/api/v1/payments/order/{orderId}/cancel", orderId)
                .header("Authorization", validToken))
                .andExpect(status().isConflict());
    }

    @Test
    void cancelPayment_withIllegalArgument_returns404() throws Exception {
        when(paymentService.cancelPayment(orderId))
                .thenThrow(new IllegalArgumentException("No payment found"));

        mockMvc.perform(post("/api/v1/payments/order/{orderId}/cancel", orderId)
                .header("Authorization", validToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void webhook_withInvalidSignature_returns400() throws Exception {
        when(stripeConfig.validateWebhookSignature(anyString(), anyString())).thenReturn(false);

        mockMvc.perform(post("/api/v1/payments/webhook")
                .header("Stripe-Signature", "invalid")
                .content("{\"payload\": \"test\"}")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$").value("Invalid signature"));
    }

    @Test
    void webhook_withValidSignature_returns200() throws Exception {
        when(stripeConfig.validateWebhookSignature(anyString(), anyString())).thenReturn(true);

        String payload = """
                {
                    "id": "evt_001",
                    "type": "payment_intent.succeeded",
                    "data": {
                        "object": {
                            "id": "pi_001",
                            "payment_intent": "pi_001",
                            "status": "succeeded",
                            "amount": 50000,
                            "currency": "cop"
                        }
                    }
                }
                """;

        mockMvc.perform(post("/api/v1/payments/webhook")
                .header("Stripe-Signature", "valid_sig")
                .content(payload)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value("Webhook processed"));
    }

    @Test
    void webhook_withValidSignatureAndProcessingError_returns500() throws Exception {
        when(stripeConfig.validateWebhookSignature(anyString(), anyString())).thenReturn(true);

        mockMvc.perform(post("/api/v1/payments/webhook")
                .header("Stripe-Signature", "valid_sig")
                .content("invalid json")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$").value("Processing failed"));
    }

    @Test
    void health_returns200() throws Exception {
        mockMvc.perform(get("/api/v1/payments/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value("OK"));
    }
}
