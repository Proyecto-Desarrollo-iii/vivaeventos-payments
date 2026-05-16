package co.empresa.vivaeventos.payments.domain.service;

import co.empresa.vivaeventos.payments.config.OrdersClient;
import co.empresa.vivaeventos.payments.config.TicketsClient;
import co.empresa.vivaeventos.payments.domain.model.Payment;
import co.empresa.vivaeventos.payments.domain.repository.IPaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentTimeoutService {

    private final IPaymentRepository paymentRepository;
    private final OrdersClient ordersClient;
    private final TicketsClient ticketsClient;

    @Value("${payment.timeout.minutes:15}")
    private int timeoutMinutes;

    @Scheduled(fixedRateString = "${payment.timeout.check.interval.ms:60000}")
    @Transactional
    public void processTimedOutPayments() {
        log.debug("Checking for timed out payments (timeout: {} minutes)", timeoutMinutes);

        Instant cutoffTime = Instant.now().minus(timeoutMinutes, ChronoUnit.MINUTES);

        List<Payment> timedOutPayments = paymentRepository.findPaymentsTimedOut(
                List.of(Payment.PaymentStatus.PENDING, Payment.PaymentStatus.PROCESSING),
                cutoffTime
        );

        if (!timedOutPayments.isEmpty()) {
            log.info("Found {} timed out payments to process", timedOutPayments.size());
        }

        for (Payment payment : timedOutPayments) {
            processTimeout(payment);
        }
    }

    private void processTimeout(Payment payment) {
        log.info("Processing timeout for payment: {}", payment.getId());

        payment.setStatus(Payment.PaymentStatus.TIMEOUT);
        payment.setFailureReason("Payment timeout - no response received");
        paymentRepository.save(payment);

        try {
            ordersClient.updateOrderStatus(payment.getOrderId(), "FAILED");
            log.info("Order {} status updated to FAILED due to timeout", payment.getOrderId());
        } catch (Exception e) {
            log.error("Failed to update order status to FAILED: {}", e.getMessage());
        }

        try {
            ticketsClient.releaseTicketsByOrderWithRetry(payment.getOrderId(), 3);
            log.info("Released tickets for order {} due to timeout", payment.getOrderId());
        } catch (Exception e) {
            log.error("Failed to release tickets for order {}: {}", payment.getOrderId(), e.getMessage());
        }
    }
}