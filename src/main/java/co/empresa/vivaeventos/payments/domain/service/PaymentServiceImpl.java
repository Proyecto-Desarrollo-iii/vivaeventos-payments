package co.empresa.vivaeventos.payments.domain.service;

import co.empresa.vivaeventos.payments.config.EventsClient;
import co.empresa.vivaeventos.payments.config.NotificationsClient;
import co.empresa.vivaeventos.payments.config.OrdersClient;
import co.empresa.vivaeventos.payments.config.TicketsClient;
import co.empresa.vivaeventos.payments.domain.model.Dto.CreatePaymentRequest;
import co.empresa.vivaeventos.payments.domain.model.Dto.PaymentResponse;
import co.empresa.vivaeventos.payments.domain.model.Dto.RefundRequest;
import co.empresa.vivaeventos.payments.domain.model.Dto.WebhookPayload;
import co.empresa.vivaeventos.payments.domain.model.Payment;
import co.empresa.vivaeventos.payments.domain.model.Promotion;
import co.empresa.vivaeventos.payments.domain.model.WebhookEvent;
import co.empresa.vivaeventos.payments.domain.repository.IPaymentRepository;
import co.empresa.vivaeventos.payments.domain.repository.IPromotionRepository;
import co.empresa.vivaeventos.payments.domain.repository.IWebhookEventRepository;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.PaymentIntentRetrieveParams;
import com.stripe.param.PaymentIntentUpdateParams;
import com.stripe.param.RefundCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceImpl implements IPaymentService {

    private final IPaymentRepository paymentRepository;
    private final IWebhookEventRepository webhookEventRepository;
    private final OrdersClient ordersClient;
    private final TicketsClient ticketsClient;
    private final NotificationsClient notificationsClient;
    private final EventsClient eventsClient;
    private final IPromotionRepository promotionRepository;

    @Override
    @Transactional
    public PaymentResponse createPaymentIntent(CreatePaymentRequest request, String idempotencyKey, String userId, String userEmail) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            idempotencyKey = UUID.randomUUID().toString();
        }
        log.info("Creating payment intent for order: {} by user: {}, idempotencyKey: {}", request.getOrderId(), userId, idempotencyKey);

        Optional<Payment> existing = paymentRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            log.info("Idempotency key already used: {}, returning existing payment: {}", idempotencyKey, existing.get().getId());
            String clientSecret = retrieveClientSecret(existing.get().getProviderReference());
            return PaymentResponse.fromEntity(existing.get(), clientSecret);
        }

        Optional<Payment> existingByOrder = paymentRepository.findByOrderIdWithLock(request.getOrderId());
        if (existingByOrder.isPresent()) {
            Payment existingPayment = existingByOrder.get();
            if (existingPayment.getIdempotencyKey() != null && existingPayment.getIdempotencyKey().equals(idempotencyKey)) {
                log.info("Idempotency key found after acquiring lock: {}, returning existing payment", idempotencyKey);
                String clientSecret = retrieveClientSecret(existingPayment.getProviderReference());
                return PaymentResponse.fromEntity(existingPayment, clientSecret);
            }
            if (existingPayment.getStatus() == Payment.PaymentStatus.PENDING ||
                existingPayment.getStatus() == Payment.PaymentStatus.PROCESSING ||
                existingPayment.getStatus() == Payment.PaymentStatus.APPROVED ||
                existingPayment.getStatus() == Payment.PaymentStatus.PAID) {
                throw new IllegalStateException("Payment already exists for this order");
            }
        }

        String currency = request.getCurrency() != null ? request.getCurrency() : "COP";
        String stripeIdempotencyKey = "payment_create_" + request.getOrderId() + "_" + idempotencyKey;

        PaymentIntentCreateParams.Builder paramsBuilder = PaymentIntentCreateParams.builder()
                .setAmount(request.getAmount().multiply(java.math.BigDecimal.valueOf(100)).longValue())
                .setCurrency(currency.toLowerCase())
                .putMetadata("order_id", request.getOrderId().toString())
                .putMetadata("idempotency_key", idempotencyKey)
                .putMetadata("user_id", userId != null ? userId : "")
                .setAutomaticPaymentMethods(
                        PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                .setEnabled(true)
                        .build()
                );

        PaymentIntentCreateParams params = paramsBuilder.build();

        try {
            RequestOptions requestOptions = RequestOptions.builder()
                    .setIdempotencyKey(stripeIdempotencyKey)
                    .build();

            PaymentIntent paymentIntent = PaymentIntent.create(params, requestOptions);

            Payment payment = Payment.builder()
                    .orderId(request.getOrderId())
                    .idempotencyKey(idempotencyKey)
                    .userId(userId != null ? UUID.fromString(userId) : (request.getUserId() != null ? UUID.fromString(request.getUserId().toString()) : null))
                    .userEmail(userEmail)
                    .providerReference(paymentIntent.getId())
                    .amount(request.getAmount())
                    .currency(currency)
                    .status(Payment.PaymentStatus.PENDING)
                    .paymentMethod(request.getPaymentMethod())
                    .paymentProvider("STRIPE")
                    .build();

            try {
                payment = paymentRepository.save(payment);
            } catch (DataIntegrityViolationException e) {
                Payment existingPayment = paymentRepository.findByIdempotencyKey(idempotencyKey)
                        .orElseThrow(() -> new RuntimeException("Idempotency conflict, but no payment found", e));
                log.info("Idempotency key conflict resolved, returning existing payment: {}", existingPayment.getId());
                String clientSecret = retrieveClientSecret(existingPayment.getProviderReference());
                return PaymentResponse.fromEntity(existingPayment, clientSecret);
            }

            log.info("Payment created with ID: {} for idempotencyKey: {}", payment.getId(), idempotencyKey);

            String clientSecret = paymentIntent.getClientSecret();
            return PaymentResponse.fromEntity(payment, clientSecret);

        } catch (StripeException e) {
            log.error("Failed to create payment intent: {}", e.getMessage());
            throw new RuntimeException("Failed to create payment: " + e.getMessage(), e);
        }
    }

    private String retrieveClientSecret(String providerReference) {
        try {
            PaymentIntent paymentIntent = PaymentIntent.retrieve(providerReference);
            return paymentIntent.getClientSecret();
        } catch (StripeException e) {
            log.warn("Failed to retrieve client secret for {}: {}", providerReference, e.getMessage());
            return null;
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
        Payment payment = paymentRepository.findByProviderReferenceWithLock(paymentIntentId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + paymentIntentId));

        if (payment.getStatus() == Payment.PaymentStatus.PAID) {
            log.info("Payment {} already confirmed as PAID, skipping", paymentIntentId);
            return PaymentResponse.fromEntity(payment);
        }

        try {
            PaymentIntentRetrieveParams params = PaymentIntentRetrieveParams.builder().build();
            PaymentIntent paymentIntent = PaymentIntent.retrieve(paymentIntentId, params, null);

            Payment.PaymentStatus newStatus = mapStripeStatus(paymentIntent.getStatus());
            payment.setStatus(newStatus);

            if (newStatus == Payment.PaymentStatus.PAID && payment.getProcessedAt() == null) {
                payment.setProcessedAt(Instant.now());
                processSuccessfulPayment(payment, "confirmPayment");
            } else if (newStatus == Payment.PaymentStatus.FAILED) {
                payment.setFailureReason(paymentIntent.getLastPaymentError() != null
                        ? paymentIntent.getLastPaymentError().getMessage()
                        : "Payment failed");
                handlePaymentFailure(payment);
            } else {
                payment.setStatus(newStatus);
            }

            payment = paymentRepository.save(payment);
            log.info("Payment confirmed with status: {}", newStatus);
            return PaymentResponse.fromEntity(payment);

        } catch (StripeException e) {
            log.error("Failed to confirm payment: {}", e.getMessage());
            throw new RuntimeException("Failed to confirm payment: " + e.getMessage(), e);
        }
    }

    private void processSuccessfulPayment(Payment payment, String source) {
        log.info("Processing successful payment for order: {} from {}", payment.getOrderId(), source);

        // Obtener datos de la orden para placeholders
        Map<String, Object> orderData = null;
        try {
            orderData = ordersClient.getOrderById(payment.getOrderId());
        } catch (Exception e) {
            log.warn("Could not fetch order data for placeholders: {}", e.getMessage());
        }

        try {
            issueTicketsForOrder(payment.getOrderId(), payment.getUserEmail(), payment.getUserId());
        } catch (Exception e) {
            log.error("Failed to issue tickets for order {}: {}", payment.getOrderId(), e.getMessage());
        }

        try {
            ordersClient.updateOrderStatus(payment.getOrderId(), "PAID");
            log.info("Order {} status updated to PAID", payment.getOrderId());
        } catch (Exception e) {
            log.error("Failed to update order status to PAID: {}", e.getMessage());
        }

        try {
            Map<String, String> placeholders = new java.util.HashMap<>();
            placeholders.put("nombre", payment.getUserEmail());
            placeholders.put("evento", "");
            placeholders.put("fecha", "");
            placeholders.put("lugar", "");
            placeholders.put("cantidad", "0");
            placeholders.put("total", "0");
            placeholders.put("codigo_qr", "Disponible en tu perfil");

            if (orderData != null) {
                Object totalObj = orderData.get("total");
                if (totalObj instanceof Number totalNum) {
                    placeholders.put("total", totalNum.toString());
                }
                Object itemsObj = orderData.get("items");
                if (itemsObj instanceof List<?> itemsList && !itemsList.isEmpty()) {
                    StringBuilder eventNames = new StringBuilder();
                    int totalQuantity = 0;
                    UUID firstEventId = null;

                    for (Object itemObj : itemsList) {
                        if (itemObj instanceof Map<?, ?> item) {
                            String eventName = (String) item.get("eventName");
                            String holderName = item.get("holderName") instanceof String hn ? hn : "";
                            int quantity = item.get("quantity") instanceof Number n ? n.intValue() : 0;
                            String eventIdStr = item.get("eventId") instanceof String s ? s : null;

                            if (eventName != null) {
                                eventNames.append(eventNames.length() > 0 ? ", " : "").append(eventName);
                            }
                            totalQuantity += quantity;

                            if (!holderName.isEmpty()) {
                                placeholders.put("nombre", holderName);
                            }

                            if (firstEventId == null && eventIdStr != null) {
                                try {
                                    firstEventId = UUID.fromString(eventIdStr);
                                } catch (IllegalArgumentException ignored) {}
                            }
                        }
                    }
                    placeholders.put("evento", eventNames.toString());
                    placeholders.put("cantidad", String.valueOf(totalQuantity));

                    if (firstEventId != null) {
                        try {
                            Map<String, Object> eventData = eventsClient.getEventById(firstEventId);
                            if (eventData != null && eventData.get("evento") instanceof Map<?, ?> evento) {
                                String eventDateTime = (String) evento.get("eventDateTime");
                                if (eventDateTime != null) {
                                    try {
                                        java.time.LocalDateTime dt = java.time.LocalDateTime.parse(eventDateTime);
                                        placeholders.put("fecha", dt.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
                                    } catch (Exception e) {
                                        placeholders.put("fecha", eventDateTime.substring(0, Math.min(10, eventDateTime.length())));
                                    }
                                }
                                String venueName = (String) evento.get("venueName");
                                String address = (String) evento.get("address");
                                StringBuilder lugar = new StringBuilder();
                                if (venueName != null) lugar.append(venueName);
                                if (address != null) {
                                    if (!lugar.isEmpty()) lugar.append(" - ");
                                    lugar.append(address);
                                }
                                if (!lugar.isEmpty()) {
                                    placeholders.put("lugar", lugar.toString());
                                }
                            }
                        } catch (Exception e) {
                            log.warn("Could not fetch event details for fecha/lugar: {}", e.getMessage());
                        }
                    }
                }
            }

            notificationsClient.sendNotification(
                payment.getUserId(), 
                payment.getUserEmail(),
                "PURCHASE",
                "EMAIL",
                placeholders
            );
        } catch (Exception e) {
            log.error("Failed to send purchase notification: {}", e.getMessage());
        }

        // Promocion por cantidad de boletas
        if (orderData != null) {
            try {
                Object itemsObj = orderData.get("items");
                int totalTickets = 0;
                String eventName = "";
                if (itemsObj instanceof List<?> itemsList) {
                    for (Object itemObj : itemsList) {
                        if (itemObj instanceof Map<?, ?> item) {
                            int qty = item.get("quantity") instanceof Number n ? n.intValue() : 0;
                            totalTickets += qty;
                            if (eventName.isEmpty() && item.get("eventName") instanceof String en) {
                                eventName = en;
                            }
                        }
                    }
                }

                if (totalTickets >= 4) {
                    String code = "PROMO-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
                    String discount = "15% de descuento";
                    Instant expiresAt = Instant.now().plus(java.time.Duration.ofDays(30));

                    Promotion promotion = Promotion.builder()
                            .userId(payment.getUserId())
                            .code(code)
                            .discount(discount)
                            .eventName(eventName)
                            .expiresAt(expiresAt)
                            .build();
                    promotionRepository.save(promotion);

                    String holderName = payment.getUserEmail();
                    if (payment.getUserEmail() != null) {
                        Object itemsObj2 = orderData.get("items");
                        if (itemsObj2 instanceof List<?> itemsList2 && !itemsList2.isEmpty()) {
                            Object first = itemsList2.get(0);
                            if (first instanceof Map<?, ?> item) {
                                String hn = item.get("holderName") instanceof String s ? s : null;
                                if (hn != null && !hn.isBlank()) holderName = hn;
                            }
                        }
                    }

                    Map<String, String> promoPlaceholders = new java.util.HashMap<>();
                    promoPlaceholders.put("nombre", holderName);
                    promoPlaceholders.put("evento", eventName);
                    promoPlaceholders.put("descuento", discount);
                    promoPlaceholders.put("codigo_promocion", code);
                    promoPlaceholders.put("fecha_expiracion", java.time.LocalDateTime.now().plusDays(30).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")));

                    notificationsClient.sendNotification(
                        payment.getUserId(),
                        payment.getUserEmail(),
                        "PROMOTION",
                        "EMAIL",
                        promoPlaceholders
                    );

                    log.info("Promotion {} created for user {} ({} tickets)", code, payment.getUserId(), totalTickets);
                }
            } catch (Exception e) {
                log.error("Failed to send promotion notification: {}", e.getMessage());
            }
        }

    }

    private void issueTicketsForOrder(UUID orderId, String userEmail, UUID userId) {
        Map<String, Object> orderData = ordersClient.getOrderById(orderId);
        if (orderData == null) {
            log.warn("No order data found for order: {}", orderId);
            return;
        }
        Object itemsObj = orderData.get("items");
        if (!(itemsObj instanceof List<?> itemsList)) {
            log.warn("No items found in order: {}", orderId);
            return;
        }
        for (Object itemObj : itemsList) {
            if (!(itemObj instanceof Map<?, ?> item)) continue;

            String eventId = String.valueOf(item.get("eventId"));
            String eventName = (String) item.get("eventName");
            String ticketTypeId = String.valueOf(item.get("ticketTypeId"));
            String ticketTypeName = (String) item.get("ticketTypeName");
            Object quantityObj = item.get("quantity");
            Object unitPriceObj = item.get("unitPrice");
            String holderName = item.get("holderName") instanceof String hn ? hn : userEmail;
            String holderEmail = item.get("holderEmail") instanceof String he ? he : userEmail;

            int quantity = (quantityObj instanceof Number n) ? n.intValue() : 1;
            double unitPrice = (unitPriceObj instanceof Number n) ? n.doubleValue() : 0.0;

            for (int i = 0; i < quantity; i++) {
                Map<String, Object> ticketRequest = new java.util.HashMap<>();
                ticketRequest.put("orderId", orderId.toString());
                ticketRequest.put("eventId", eventId);
                ticketRequest.put("ticketTypeId", ticketTypeId);
                ticketRequest.put("ticketType", ticketTypeName);
                ticketRequest.put("eventName", eventName);
                ticketRequest.put("holderName", holderName);
                ticketRequest.put("holderEmail", holderEmail);
                ticketRequest.put("price", unitPrice);
                ticketRequest.put("userId", userId != null ? userId.toString() : "");

                try {
                    ticketsClient.issueTicket(ticketRequest);
                } catch (Exception e) {
                    log.error("Failed to issue ticket for order {}: {}", orderId, e.getMessage());
                }
            }
        }
        log.info("Tickets issued for order: {}", orderId);
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

        WebhookEvent webhookEvent = WebhookEvent.builder()
                .eventId(payload.getEventId())
                .paymentIntentId(payload.getPaymentIntentId())
                .build();
        try {
            webhookEventRepository.save(webhookEvent);
        } catch (DataIntegrityViolationException e) {
            log.info("Webhook event {} already processed (concurrent), skipping", payload.getEventId());
            return null;
        }

        Optional<Payment> optionalPayment = paymentRepository.findByProviderReferenceWithLock(payload.getPaymentIntentId());
        if (optionalPayment.isEmpty()) {
            log.warn("Payment not found for webhook: {}", payload.getPaymentIntentId());
            return null;
        }

        Payment payment = optionalPayment.get();
        Payment.PaymentStatus previousStatus = payment.getStatus();
        Payment.PaymentStatus newStatus = mapWebhookStatus(payload.getStatus());
        payment.setStatus(newStatus);

        if (newStatus == Payment.PaymentStatus.PAID && payment.getProcessedAt() == null) {
            payment.setProcessedAt(Instant.now());
            if (previousStatus != Payment.PaymentStatus.PAID) {
                processSuccessfulPayment(payment, "webhook");
            }
        }

        if (newStatus == Payment.PaymentStatus.FAILED && payload.getFailureReason() != null) {
            payment.setFailureReason(payload.getFailureReason());
            if (previousStatus != Payment.PaymentStatus.FAILED) {
                handlePaymentFailure(payment);
            }
        }

        if (newStatus == Payment.PaymentStatus.CANCELLED && previousStatus != Payment.PaymentStatus.CANCELLED) {
            try {
                ordersClient.updateOrderStatus(payment.getOrderId(), "CANCELLED");
            } catch (Exception e) {
                log.error("Failed to update order status to CANCELLED: {}", e.getMessage());
            }
        }

        payment = paymentRepository.save(payment);
        log.info("Webhook processed, payment status: {}", payment.getStatus());
        return PaymentResponse.fromEntity(payment);
    }

    @Override
    @Transactional
    public PaymentResponse retryPayment(UUID orderId, String userId, String userEmail) {
        log.info("Retrying payment for order: {} by user: {}", orderId, userId);

        Payment payment = paymentRepository.findByOrderIdWithLock(orderId)
                .orElseThrow(() -> new IllegalArgumentException("No payment found for order: " + orderId));

        if (payment.getStatus() == Payment.PaymentStatus.PAID) {
            throw new IllegalStateException("Payment already completed, cannot retry");
        }
        if (payment.getStatus() == Payment.PaymentStatus.CANCELLED) {
            throw new IllegalStateException("Payment was cancelled, cannot retry");
        }

        try {
            String oldIntentId = payment.getProviderReference();
            if (oldIntentId != null) {
                try {
                    PaymentIntent oldIntent = PaymentIntent.retrieve(oldIntentId);
                    if (!"canceled".equals(oldIntent.getStatus()) && !"succeeded".equals(oldIntent.getStatus())) {
                        oldIntent.cancel();
                        log.info("Cancelled old PaymentIntent: {}", oldIntentId);
                    }
                } catch (StripeException e) {
                    log.warn("Failed to cancel old PaymentIntent {}: {}", oldIntentId, e.getMessage());
                }
            }

            String currency = payment.getCurrency() != null ? payment.getCurrency() : "COP";
            String stripeIdempotencyKey = "payment_retry_" + orderId + "_" + UUID.randomUUID();

            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                    .setAmount(payment.getAmount().multiply(java.math.BigDecimal.valueOf(100)).longValue())
                    .setCurrency(currency.toLowerCase())
                    .putMetadata("order_id", orderId.toString())
                    .putMetadata("retry", "true")
                    .putMetadata("user_id", userId != null ? userId : "")
                    .setAutomaticPaymentMethods(
                            PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                    .setEnabled(true)
                            .build()
                    )
                    .build();

            RequestOptions requestOptions = RequestOptions.builder()
                    .setIdempotencyKey(stripeIdempotencyKey)
                    .build();

            PaymentIntent newPaymentIntent = PaymentIntent.create(params, requestOptions);

            payment.setProviderReference(newPaymentIntent.getId());
            payment.setStatus(Payment.PaymentStatus.PENDING);
            payment.setFailureReason(null);
            payment.setProcessedAt(null);
            payment.setIdempotencyKey(UUID.randomUUID().toString());

            payment = paymentRepository.save(payment);

            log.info("Retry payment created for order: {}, new PaymentIntent: {}", orderId, newPaymentIntent.getId());
            return PaymentResponse.fromEntity(payment, newPaymentIntent.getClientSecret());

        } catch (StripeException e) {
            log.error("Failed to retry payment for order {}: {}", orderId, e.getMessage());
            throw new RuntimeException("Failed to retry payment: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public PaymentResponse cancelPayment(UUID orderId) {
        log.info("Cancelling payment for order: {}", orderId);

        Payment payment = paymentRepository.findByOrderIdWithLock(orderId)
                .orElseThrow(() -> new IllegalArgumentException("No payment found for order: " + orderId));

        if (payment.getStatus() == Payment.PaymentStatus.PAID) {
            throw new IllegalStateException("Cannot cancel a paid payment");
        }
        if (payment.getStatus() == Payment.PaymentStatus.CANCELLED) {
            throw new IllegalStateException("Payment already cancelled");
        }

        try {
            String intentId = payment.getProviderReference();
            if (intentId != null) {
                try {
                    PaymentIntent paymentIntent = PaymentIntent.retrieve(intentId);
                    if (!"canceled".equals(paymentIntent.getStatus()) && !"succeeded".equals(paymentIntent.getStatus())) {
                        paymentIntent.cancel();
                        log.info("Cancelled PaymentIntent: {}", intentId);
                    }
                } catch (StripeException e) {
                    log.warn("Failed to cancel PaymentIntent {}: {}", intentId, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("Error cancelling Stripe PaymentIntent: {}", e.getMessage());
        }

        payment.setStatus(Payment.PaymentStatus.CANCELLED);
        payment = paymentRepository.save(payment);

        try {
            ordersClient.updateOrderStatus(orderId, "CANCELLED");
            log.info("Order {} status updated to CANCELLED", orderId);
            
            // Notificación de cancelación de orden — placeholders para CANCELLATION_EMAIL
            Map<String, String> placeholders = new java.util.HashMap<>();
            placeholders.put("nombre", payment.getUserEmail());
            placeholders.put("evento", "");
            placeholders.put("fecha", "");
            placeholders.put("motivo", "Cancelado por el usuario");
            placeholders.put("total", payment.getAmount() != null ? payment.getAmount().toString() : "0");

            notificationsClient.sendNotification(
                payment.getUserId(), 
                payment.getUserEmail(),
                "CANCELLATION",
                "EMAIL",
                placeholders
            );
        } catch (Exception e) {
            log.error("Failed to update order status to CANCELLED: {}", e.getMessage());
        }


        try {
            ticketsClient.releaseTicketsByOrderWithRetry(orderId, 3);
            log.info("Released tickets for cancelled order: {}", orderId);
        } catch (Exception e) {
            log.error("Failed to release tickets for order {}: {}", orderId, e.getMessage());
        }

        log.info("Payment cancelled for order: {}", orderId);
        return PaymentResponse.fromEntity(payment);
    }

    private Payment.PaymentStatus mapStripeStatus(String stripeStatus) {
        return switch (stripeStatus) {
            case "requires_payment_method", "requires_confirmation", "requires_action" -> Payment.PaymentStatus.PROCESSING;
            case "processing" -> Payment.PaymentStatus.PROCESSING;
            case "succeeded" -> Payment.PaymentStatus.PAID;
            case "canceled" -> Payment.PaymentStatus.CANCELLED;
            default -> Payment.PaymentStatus.PENDING;
        };
    }

    private Payment.PaymentStatus mapWebhookStatus(String webhookStatus) {
        if (webhookStatus == null) return Payment.PaymentStatus.PENDING;
        return switch (webhookStatus.toLowerCase()) {
            case "succeeded" -> Payment.PaymentStatus.PAID;
            case "processing" -> Payment.PaymentStatus.PROCESSING;
            case "requires_payment_method", "requires_confirmation", "requires_action" -> Payment.PaymentStatus.PROCESSING;
            case "canceled" -> Payment.PaymentStatus.CANCELLED;
            case "failed" -> Payment.PaymentStatus.FAILED;
            default -> Payment.PaymentStatus.PENDING;
        };
    }

    @Override
    @Transactional
    public PaymentResponse processRefund(UUID paymentId, String idempotencyKey, RefundRequest request) {
        log.info("Processing refund for payment: {} with idempotencyKey: {}", paymentId, idempotencyKey);

        Payment payment = paymentRepository.findByIdWithLock(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + paymentId));

        if (payment.getRefundIdempotencyKey() != null && payment.getRefundIdempotencyKey().equals(idempotencyKey)) {
            log.info("Refund idempotency key already used: {}, returning existing refund", idempotencyKey);
            return PaymentResponse.fromEntity(payment);
        }

        if (payment.getStatus() != Payment.PaymentStatus.APPROVED &&
            payment.getStatus() != Payment.PaymentStatus.PAID) {
            throw new IllegalStateException("Only approved/paid payments can be refunded");
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

            RefundCreateParams params = paramsBuilder.build();

            String stripeIdempotencyKey = "refund_" + paymentId + "_" + idempotencyKey;
            RequestOptions requestOptions = RequestOptions.builder()
                    .setIdempotencyKey(stripeIdempotencyKey)
                    .build();

            com.stripe.model.Refund refund = com.stripe.model.Refund.create(params, requestOptions);

            payment.setStatus(Payment.PaymentStatus.REFUNDED);
            payment.setRefundId(refund.getId());
            payment.setRefundIdempotencyKey(idempotencyKey);

            try {
                payment = paymentRepository.save(payment);
            } catch (DataIntegrityViolationException e) {
                Payment existingPayment = paymentRepository.findByIdWithLock(paymentId)
                        .orElseThrow(() -> new RuntimeException("Refund conflict, but no payment found", e));
                log.info("Refund idempotency key conflict resolved, returning existing payment: {}", existingPayment.getId());
                return PaymentResponse.fromEntity(existingPayment);
            }

            log.info("Refund processed: {} with idempotencyKey: {}", refund.getId(), idempotencyKey);
            return PaymentResponse.fromEntity(payment);

        } catch (StripeException e) {
            log.error("Failed to process refund: {}", e.getMessage());
            throw new RuntimeException("Failed to process refund: " + e.getMessage(), e);
        }
    }
}
