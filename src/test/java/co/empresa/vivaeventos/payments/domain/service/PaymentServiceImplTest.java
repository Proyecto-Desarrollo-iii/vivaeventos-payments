package co.empresa.vivaeventos.payments.domain.service;

import co.empresa.vivaeventos.payments.config.OrdersClient;
import co.empresa.vivaeventos.payments.config.TicketsClient;
import co.empresa.vivaeventos.payments.domain.model.Dto.CreatePaymentRequest;
import co.empresa.vivaeventos.payments.domain.model.Dto.PaymentResponse;
import co.empresa.vivaeventos.payments.domain.model.Dto.RefundRequest;
import co.empresa.vivaeventos.payments.domain.model.Dto.WebhookPayload;
import co.empresa.vivaeventos.payments.domain.model.Payment;
import co.empresa.vivaeventos.payments.domain.model.WebhookEvent;
import co.empresa.vivaeventos.payments.domain.repository.IPaymentRepository;
import co.empresa.vivaeventos.payments.domain.repository.IWebhookEventRepository;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock
    private IPaymentRepository paymentRepository;

    @Mock
    private IWebhookEventRepository webhookEventRepository;

    @Mock
    private OrdersClient ordersClient;

    @Mock
    private TicketsClient ticketsClient;

    private PaymentServiceImpl paymentService;

    private CreatePaymentRequest validRequest;
    private Payment existingPayment;
    private Payment pendingPayment;
    private Payment approvedPayment;
    private Payment failedPayment;
    private Payment refundedPayment;
    private UUID orderId;
    private UUID paymentId;
    private String idempotencyKey;
    private String userId;
    private String userEmail;
    private String clientSecret;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentServiceImpl(
                paymentRepository, webhookEventRepository, ordersClient, ticketsClient);

        orderId = UUID.randomUUID();
        paymentId = UUID.randomUUID();
        idempotencyKey = "test-idempotency-key-001";
        userId = UUID.randomUUID().toString();
        userEmail = "user@test.com";
        clientSecret = "pi_secret_test";

        validRequest = CreatePaymentRequest.builder()
                .orderId(orderId)
                .amount(BigDecimal.valueOf(50000))
                .currency("COP")
                .build();

        existingPayment = Payment.builder()
                .id(paymentId)
                .orderId(orderId)
                .idempotencyKey(idempotencyKey)
                .amount(BigDecimal.valueOf(50000))
                .currency("COP")
                .status(Payment.PaymentStatus.PENDING)
                .providerReference("pi_test_123")
                .build();

        pendingPayment = Payment.builder()
                .id(UUID.randomUUID())
                .orderId(orderId)
                .amount(BigDecimal.valueOf(50000))
                .status(Payment.PaymentStatus.PENDING)
                .providerReference("pi_test_456")
                .build();

        approvedPayment = Payment.builder()
                .id(UUID.randomUUID())
                .orderId(orderId)
                .amount(BigDecimal.valueOf(50000))
                .status(Payment.PaymentStatus.APPROVED)
                .providerReference("pi_test_789")
                .build();

        failedPayment = Payment.builder()
                .id(UUID.randomUUID())
                .orderId(orderId)
                .amount(BigDecimal.valueOf(50000))
                .status(Payment.PaymentStatus.FAILED)
                .providerReference("pi_test_fail")
                .build();

        refundedPayment = Payment.builder()
                .id(UUID.randomUUID())
                .orderId(orderId)
                .amount(BigDecimal.valueOf(50000))
                .status(Payment.PaymentStatus.REFUNDED)
                .providerReference("pi_test_refund")
                .refundId("re_test_001")
                .refundIdempotencyKey("refund-key-001")
                .build();
    }

    @Test
    void createPaymentIntent_withSameIdempotencyKey_returnsExistingPayment() {
        when(paymentRepository.findByIdempotencyKey(idempotencyKey))
                .thenReturn(Optional.of(existingPayment));

        try (MockedStatic<PaymentIntent> piStatic = mockStatic(PaymentIntent.class)) {
            PaymentIntent mockPi = mock(PaymentIntent.class);
            when(mockPi.getClientSecret()).thenReturn(clientSecret);
            piStatic.when(() -> PaymentIntent.retrieve(existingPayment.getProviderReference()))
                    .thenReturn(mockPi);

            PaymentResponse response = paymentService.createPaymentIntent(
                    validRequest, idempotencyKey, userId, userEmail);

            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(paymentId);
            assertThat(response.getIdempotencyKey()).isEqualTo(idempotencyKey);
            assertThat(response.getClientSecret()).isEqualTo(clientSecret);
            verify(paymentRepository, never()).save(any());
        }
    }

    @Test
    void createPaymentIntent_withExistingActivePayment_throwsException() {
        when(paymentRepository.findByIdempotencyKey(idempotencyKey))
                .thenReturn(Optional.empty());
        when(paymentRepository.findByOrderIdWithLock(orderId))
                .thenReturn(Optional.of(pendingPayment));

        assertThatThrownBy(() -> paymentService.createPaymentIntent(
                validRequest, idempotencyKey, userId, userEmail))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Payment already exists for this order");

        verify(paymentRepository, never()).save(any());
    }

    @Test
    void createPaymentIntent_withExistingIdempotencyKeyAfterLock_returnsExisting() throws StripeException {
        String key = "lock-race-key";
        Payment lockedPayment = Payment.builder()
                .id(UUID.randomUUID())
                .orderId(orderId)
                .idempotencyKey(key)
                .amount(BigDecimal.valueOf(50000))
                .currency("COP")
                .status(Payment.PaymentStatus.PENDING)
                .providerReference("pi_test_lock_race")
                .build();

        when(paymentRepository.findByIdempotencyKey(key))
                .thenReturn(Optional.empty());
        when(paymentRepository.findByOrderIdWithLock(orderId))
                .thenReturn(Optional.of(lockedPayment));

        try (MockedStatic<PaymentIntent> piStatic = mockStatic(PaymentIntent.class)) {
            PaymentIntent mockPi = mock(PaymentIntent.class);
            when(mockPi.getClientSecret()).thenReturn(clientSecret);
            piStatic.when(() -> PaymentIntent.retrieve("pi_test_lock_race"))
                    .thenReturn(mockPi);

            PaymentResponse response = paymentService.createPaymentIntent(
                    validRequest, key, userId, userEmail);

            assertThat(response).isNotNull();
            assertThat(response.getIdempotencyKey()).isEqualTo(key);
            assertThat(response.getClientSecret()).isEqualTo(clientSecret);
            verify(paymentRepository, never()).save(any());
        }
    }

    @Test
    void createPaymentIntent_withExistingApprovedPayment_throwsException() {
        when(paymentRepository.findByIdempotencyKey(idempotencyKey))
                .thenReturn(Optional.empty());
        when(paymentRepository.findByOrderIdWithLock(orderId))
                .thenReturn(Optional.of(approvedPayment));

        assertThatThrownBy(() -> paymentService.createPaymentIntent(
                validRequest, idempotencyKey, userId, userEmail))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Payment already exists for this order");
    }

    @Test
    void createPaymentIntent_withFailedOrder_createsNewPayment() throws StripeException {
        when(paymentRepository.findByIdempotencyKey(idempotencyKey))
                .thenReturn(Optional.empty());
        when(paymentRepository.findByOrderIdWithLock(orderId))
                .thenReturn(Optional.of(failedPayment));

        PaymentIntent mockPaymentIntent = mock(PaymentIntent.class);
        when(mockPaymentIntent.getId()).thenReturn("pi_test_new");
        when(mockPaymentIntent.getClientSecret()).thenReturn(clientSecret);

        try (MockedStatic<PaymentIntent> piStatic = mockStatic(PaymentIntent.class)) {
            piStatic.when(() -> PaymentIntent.create(any(PaymentIntentCreateParams.class), any(RequestOptions.class)))
                    .thenReturn(mockPaymentIntent);

            Payment savedPayment = Payment.builder()
                    .id(UUID.randomUUID())
                    .orderId(orderId)
                    .idempotencyKey(idempotencyKey)
                    .providerReference("pi_test_new")
                    .amount(BigDecimal.valueOf(50000))
                    .currency("COP")
                    .status(Payment.PaymentStatus.PENDING)
                    .paymentProvider("STRIPE")
                    .build();

            when(paymentRepository.save(any(Payment.class))).thenReturn(savedPayment);

            PaymentResponse response = paymentService.createPaymentIntent(
                    validRequest, idempotencyKey, userId, userEmail);

            assertThat(response).isNotNull();
            assertThat(response.getIdempotencyKey()).isEqualTo(idempotencyKey);
            verify(paymentRepository).save(any(Payment.class));
        }
    }

    @Test
    void createPaymentIntent_concurrentIdempotencyKey_catchesDataIntegrityViolation() throws StripeException {
        Payment existingConflict = Payment.builder()
                .id(UUID.randomUUID())
                .orderId(orderId)
                .idempotencyKey(idempotencyKey)
                .providerReference("pi_test_conflict")
                .amount(BigDecimal.valueOf(50000))
                .currency("COP")
                .status(Payment.PaymentStatus.PENDING)
                .build();

        when(paymentRepository.findByIdempotencyKey(idempotencyKey))
                .thenReturn(Optional.empty(), Optional.of(existingConflict));
        when(paymentRepository.findByOrderIdWithLock(orderId))
                .thenReturn(Optional.empty());

        PaymentIntent mockPaymentIntent = mock(PaymentIntent.class);
        when(mockPaymentIntent.getId()).thenReturn("pi_test_new");
        when(mockPaymentIntent.getClientSecret()).thenReturn(clientSecret);

        try (MockedStatic<PaymentIntent> piStatic = mockStatic(PaymentIntent.class)) {
            piStatic.when(() -> PaymentIntent.create(any(PaymentIntentCreateParams.class), any(RequestOptions.class)))
                    .thenReturn(mockPaymentIntent);
            piStatic.when(() -> PaymentIntent.retrieve("pi_test_conflict"))
                    .thenReturn(mockPaymentIntent);

            when(paymentRepository.save(any(Payment.class)))
                    .thenThrow(new DataIntegrityViolationException("Unique constraint violation"));

            PaymentResponse response = paymentService.createPaymentIntent(
                    validRequest, idempotencyKey, userId, userEmail);

            assertThat(response).isNotNull();
            assertThat(response.getIdempotencyKey()).isEqualTo(idempotencyKey);
        }
    }

    @Test
    void createPaymentIntent_stripeException_throwsRuntimeException() throws StripeException {
        when(paymentRepository.findByIdempotencyKey(idempotencyKey))
                .thenReturn(Optional.empty());
        when(paymentRepository.findByOrderIdWithLock(orderId))
                .thenReturn(Optional.empty());

        try (MockedStatic<PaymentIntent> piStatic = mockStatic(PaymentIntent.class)) {
            piStatic.when(() -> PaymentIntent.create(any(PaymentIntentCreateParams.class), any(RequestOptions.class)))
                    .thenThrow(mock(StripeException.class));

            assertThatThrownBy(() -> paymentService.createPaymentIntent(
                    validRequest, idempotencyKey, userId, userEmail))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Failed to create payment");
        }
    }

    @Test
    void handleWebhook_withDuplicateEventId_skipsProcessing() {
        String eventId = "evt_test_001";
        WebhookPayload payload = WebhookPayload.builder()
                .eventId(eventId)
                .eventType("payment_intent.succeeded")
                .paymentIntentId("pi_test_123")
                .status("succeeded")
                .build();

        when(webhookEventRepository.save(any(WebhookEvent.class)))
                .thenThrow(new DataIntegrityViolationException("Duplicate key"));

        PaymentResponse response = paymentService.handleWebhook(payload);

        assertThat(response).isNull();
        verify(paymentRepository, never()).findByProviderReference(any());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void handleWebhook_withNewEventAndSucceededStatus_updatesPayment() {
        String eventId = "evt_test_002";
        WebhookPayload payload = WebhookPayload.builder()
                .eventId(eventId)
                .eventType("payment_intent.succeeded")
                .paymentIntentId("pi_test_123")
                .status("succeeded")
                .build();

        when(webhookEventRepository.save(any(WebhookEvent.class))).thenAnswer(i -> i.getArgument(0));
        when(paymentRepository.findByProviderReference("pi_test_123"))
                .thenReturn(Optional.of(pendingPayment));

        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));

        PaymentResponse response = paymentService.handleWebhook(payload);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(Payment.PaymentStatus.APPROVED);
        verify(ordersClient).updateOrderStatus(orderId, "PAID");
    }

    @Test
    void handleWebhook_withFailedStatus_callsHandlePaymentFailure() {
        String eventId = "evt_test_003";
        WebhookPayload payload = WebhookPayload.builder()
                .eventId(eventId)
                .eventType("payment_intent.payment_failed")
                .paymentIntentId("pi_test_fail")
                .status("failed")
                .failureReason("card_declined")
                .build();

        when(webhookEventRepository.save(any(WebhookEvent.class))).thenAnswer(i -> i.getArgument(0));
        when(paymentRepository.findByProviderReference("pi_test_fail"))
                .thenReturn(Optional.of(pendingPayment));

        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));

        PaymentResponse response = paymentService.handleWebhook(payload);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(Payment.PaymentStatus.FAILED);
        assertThat(response.getFailureReason()).isEqualTo("card_declined");
        verify(ordersClient).updateOrderStatus(orderId, "FAILED");
        verify(ticketsClient).releaseTicketsByOrderWithRetry(orderId, 3);
    }

    @Test
    void handleWebhook_paymentNotFound_logsWarning() {
        String eventId = "evt_test_004";
        WebhookPayload payload = WebhookPayload.builder()
                .eventId(eventId)
                .eventType("payment_intent.succeeded")
                .paymentIntentId("pi_not_found")
                .status("succeeded")
                .build();

        when(webhookEventRepository.save(any(WebhookEvent.class))).thenAnswer(i -> i.getArgument(0));
        when(paymentRepository.findByProviderReference("pi_not_found"))
                .thenReturn(Optional.empty());

        PaymentResponse response = paymentService.handleWebhook(payload);

        assertThat(response).isNull();
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void processRefund_withSameIdempotencyKey_returnsExisting() {
        String refundKey = "refund-key-001";
        RefundRequest request = RefundRequest.builder()
                .reason("requested_by_customer")
                .build();

        when(paymentRepository.findByIdWithLock(refundedPayment.getId()))
                .thenReturn(Optional.of(refundedPayment));

        PaymentResponse response = paymentService.processRefund(
                refundedPayment.getId(), refundKey, request);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(Payment.PaymentStatus.REFUNDED);
        assertThat(response.getRefundId()).isEqualTo("re_test_001");
        assertThat(response.getRefundIdempotencyKey()).isEqualTo(refundKey);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void processRefund_withNewKey_processesRefund() throws StripeException {
        String refundKey = "refund-key-new";
        UUID payId = UUID.randomUUID();
        Payment payment = Payment.builder()
                .id(payId)
                .orderId(orderId)
                .amount(BigDecimal.valueOf(50000))
                .currency("COP")
                .status(Payment.PaymentStatus.APPROVED)
                .providerReference("pi_test_approve")
                .build();

        RefundRequest request = RefundRequest.builder()
                .amount(BigDecimal.valueOf(25000))
                .reason("requested_by_customer")
                .build();

        when(paymentRepository.findByIdWithLock(payId)).thenReturn(Optional.of(payment));

        com.stripe.model.Refund mockRefund = mock(com.stripe.model.Refund.class);
        when(mockRefund.getId()).thenReturn("re_test_new");

        try (MockedStatic<com.stripe.model.Refund> refundStatic = mockStatic(com.stripe.model.Refund.class)) {
            refundStatic.when(() -> com.stripe.model.Refund.create(
                    any(RefundCreateParams.class), any(RequestOptions.class)))
                    .thenReturn(mockRefund);

            when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));

            PaymentResponse response = paymentService.processRefund(
                    payId, refundKey, request);

            assertThat(response).isNotNull();
            assertThat(response.getStatus()).isEqualTo(Payment.PaymentStatus.REFUNDED);
            assertThat(response.getRefundId()).isEqualTo("re_test_new");
            assertThat(response.getRefundIdempotencyKey()).isEqualTo(refundKey);
            verify(paymentRepository).save(any(Payment.class));
        }
    }

    @Test
    void processRefund_withNonApprovedPayment_throwsException() {
        String refundKey = "refund-key-002";
        RefundRequest request = RefundRequest.builder().build();

        when(paymentRepository.findByIdWithLock(pendingPayment.getId()))
                .thenReturn(Optional.of(pendingPayment));

        assertThatThrownBy(() -> paymentService.processRefund(
                pendingPayment.getId(), refundKey, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Only approved payments can be refunded");
    }

    @Test
    void processRefund_concurrentDataIntegrity_caught() throws StripeException {
        String refundKey = "refund-key-concurrent";
        UUID payId = UUID.randomUUID();
        Payment payment = Payment.builder()
                .id(payId)
                .orderId(orderId)
                .amount(BigDecimal.valueOf(50000))
                .currency("COP")
                .status(Payment.PaymentStatus.APPROVED)
                .providerReference("pi_test_approve")
                .build();

        Payment existingPayment = Payment.builder()
                .id(payId)
                .orderId(orderId)
                .amount(BigDecimal.valueOf(50000))
                .status(Payment.PaymentStatus.REFUNDED)
                .refundId("re_test_concurrent")
                .refundIdempotencyKey(refundKey)
                .build();

        RefundRequest request = RefundRequest.builder().build();

        when(paymentRepository.findByIdWithLock(payId))
                .thenReturn(Optional.of(payment), Optional.of(existingPayment));

        com.stripe.model.Refund mockRefund = mock(com.stripe.model.Refund.class);
        when(mockRefund.getId()).thenReturn("re_test_concurrent");

        try (MockedStatic<com.stripe.model.Refund> refundStatic = mockStatic(com.stripe.model.Refund.class)) {
            refundStatic.when(() -> com.stripe.model.Refund.create(
                    any(RefundCreateParams.class), any(RequestOptions.class)))
                    .thenReturn(mockRefund);

            when(paymentRepository.save(any(Payment.class)))
                    .thenThrow(new DataIntegrityViolationException("Unique constraint"));

            PaymentResponse response = paymentService.processRefund(
                    payId, refundKey, request);

            assertThat(response).isNotNull();
            assertThat(response.getStatus()).isEqualTo(Payment.PaymentStatus.REFUNDED);
            assertThat(response.getRefundIdempotencyKey()).isEqualTo(refundKey);
        }
    }

    @Test
    void processRefund_stripeException_throwsRuntimeException() throws StripeException {
        String refundKey = "refund-key-fail";
        UUID payId = UUID.randomUUID();
        Payment payment = Payment.builder()
                .id(payId)
                .orderId(orderId)
                .amount(BigDecimal.valueOf(50000))
                .currency("COP")
                .status(Payment.PaymentStatus.APPROVED)
                .providerReference("pi_test_approve")
                .build();

        RefundRequest request = RefundRequest.builder().build();

        when(paymentRepository.findByIdWithLock(payId)).thenReturn(Optional.of(payment));

        try (MockedStatic<com.stripe.model.Refund> refundStatic = mockStatic(com.stripe.model.Refund.class)) {
            refundStatic.when(() -> com.stripe.model.Refund.create(
                    any(RefundCreateParams.class), any(RequestOptions.class)))
                    .thenThrow(mock(StripeException.class));

            assertThatThrownBy(() -> paymentService.processRefund(
                    payId, refundKey, request))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Failed to process refund");
        }
    }
}
