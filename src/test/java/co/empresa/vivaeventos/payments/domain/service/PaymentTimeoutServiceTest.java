package co.empresa.vivaeventos.payments.domain.service;

import co.empresa.vivaeventos.payments.config.OrdersClient;
import co.empresa.vivaeventos.payments.config.TicketsClient;
import co.empresa.vivaeventos.payments.domain.model.Payment;
import co.empresa.vivaeventos.payments.domain.repository.IPaymentRepository;
import co.empresa.vivaeventos.payments.domain.repository.IWebhookEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentTimeoutServiceTest {

    @Mock
    private IPaymentRepository paymentRepository;

    @Mock
    private IWebhookEventRepository webhookEventRepository;

    @Mock
    private OrdersClient ordersClient;

    @Mock
    private TicketsClient ticketsClient;

    @Captor
    private ArgumentCaptor<Payment> paymentCaptor;

    private PaymentTimeoutService timeoutService;

    @BeforeEach
    void setUp() {
        timeoutService = new PaymentTimeoutService(
                paymentRepository, webhookEventRepository, ordersClient, ticketsClient);
    }

    @Test
    void processTimedOutPayments_withNoTimedOut_doesNothing() {
        when(paymentRepository.findPaymentsTimedOut(anyList(), any(Instant.class)))
                .thenReturn(List.of());

        timeoutService.processTimedOutPayments();

        verify(paymentRepository).findPaymentsTimedOut(anyList(), any(Instant.class));
        verify(paymentRepository, never()).save(any());
        verify(ordersClient, never()).updateOrderStatus(any(), any());
    }

    @Test
    void processTimedOutPayments_withTimedOutPayment_marksAsTimeoutAndUpdatesOrder() {
        UUID orderId = UUID.randomUUID();
        Payment payment = Payment.builder()
                .id(UUID.randomUUID())
                .orderId(orderId)
                .status(Payment.PaymentStatus.PENDING)
                .build();

        when(paymentRepository.findPaymentsTimedOut(anyList(), any(Instant.class)))
                .thenReturn(List.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenReturn(payment);

        timeoutService.processTimedOutPayments();

        verify(paymentRepository).save(paymentCaptor.capture());
        Payment saved = paymentCaptor.getValue();
        assertThat(saved.getStatus()).isEqualTo(Payment.PaymentStatus.TIMEOUT);
        assertThat(saved.getFailureReason()).isEqualTo("Payment timeout - no response received");

        verify(ordersClient).updateOrderStatus(orderId, "FAILED");
        verify(ticketsClient).releaseTicketsByOrderWithRetry(orderId, 3);
    }

    @Test
    void processTimedOutPayments_withMultipleTimedOut_processesAll() {
        Payment payment1 = Payment.builder()
                .id(UUID.randomUUID())
                .orderId(UUID.randomUUID())
                .status(Payment.PaymentStatus.PENDING)
                .build();
        Payment payment2 = Payment.builder()
                .id(UUID.randomUUID())
                .orderId(UUID.randomUUID())
                .status(Payment.PaymentStatus.PROCESSING)
                .build();

        when(paymentRepository.findPaymentsTimedOut(anyList(), any(Instant.class)))
                .thenReturn(List.of(payment1, payment2));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));

        timeoutService.processTimedOutPayments();

        verify(paymentRepository, times(2)).save(paymentCaptor.capture());
        assertThat(paymentCaptor.getAllValues()).hasSize(2);
        assertThat(paymentCaptor.getAllValues().get(0).getStatus()).isEqualTo(Payment.PaymentStatus.TIMEOUT);
        assertThat(paymentCaptor.getAllValues().get(1).getStatus()).isEqualTo(Payment.PaymentStatus.TIMEOUT);
        verify(ordersClient).updateOrderStatus(payment1.getOrderId(), "FAILED");
        verify(ordersClient).updateOrderStatus(payment2.getOrderId(), "FAILED");
        verify(ticketsClient).releaseTicketsByOrderWithRetry(payment1.getOrderId(), 3);
        verify(ticketsClient).releaseTicketsByOrderWithRetry(payment2.getOrderId(), 3);
    }

    @Test
    void processTimedOutPayments_whenOrderUpdateFails_stillReleasesTickets() {
        UUID orderId = UUID.randomUUID();
        Payment payment = Payment.builder()
                .id(UUID.randomUUID())
                .orderId(orderId)
                .status(Payment.PaymentStatus.PENDING)
                .build();

        when(paymentRepository.findPaymentsTimedOut(anyList(), any(Instant.class)))
                .thenReturn(List.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenReturn(payment);
        doThrow(new RuntimeException("Connection failed"))
                .when(ordersClient).updateOrderStatus(orderId, "FAILED");

        timeoutService.processTimedOutPayments();

        verify(paymentRepository).save(any(Payment.class));
        verify(ticketsClient).releaseTicketsByOrderWithRetry(orderId, 3);
    }

    @Test
    void cleanupOldWebhookEvents_deletesOldEvents() {
        timeoutService.cleanupOldWebhookEvents();

        verify(webhookEventRepository).deleteByCreatedAtBefore(any(Instant.class));
    }
}
