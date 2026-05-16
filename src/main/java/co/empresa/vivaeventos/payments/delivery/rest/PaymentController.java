package co.empresa.vivaeventos.payments.delivery.rest;

import co.empresa.vivaeventos.payments.config.JwtUtil;
import co.empresa.vivaeventos.payments.config.StripeConfig;
import co.empresa.vivaeventos.payments.domain.model.Dto.CreatePaymentRequest;
import co.empresa.vivaeventos.payments.domain.model.Dto.PaymentResponse;
import co.empresa.vivaeventos.payments.domain.model.Dto.RefundRequest;
import co.empresa.vivaeventos.payments.domain.model.Dto.WebhookPayload;
import co.empresa.vivaeventos.payments.domain.service.IPaymentService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Slf4j
public class PaymentController {

    private final IPaymentService paymentService;
    private final StripeConfig stripeConfig;
    private final ObjectMapper objectMapper;
    private final JwtUtil jwtUtil;

    @PostMapping
    public ResponseEntity<PaymentResponse> createPaymentIntent(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @Valid @RequestBody CreatePaymentRequest request) {

        Optional<Claims> claimsOpt = validateAndExtractJwt(authHeader);
        if (claimsOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Claims claims = claimsOpt.get();
        String userIdFromJwt = jwtUtil.extractUserId(claims);
        String userEmail = jwtUtil.extractEmail(claims);
        
        String userId = userIdFromJwt != null ? userIdFromJwt : request.getUserId();

        log.info("Payment request from user: {} ({}) for order: {}", userId, userEmail, request.getOrderId());
        
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(paymentService.createPaymentIntent(request, userId, userEmail));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PaymentResponse> getPaymentById(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable UUID id) {

        Optional<Claims> claimsOpt = validateAndExtractJwt(authHeader);
        if (claimsOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return ResponseEntity.ok(paymentService.getPaymentById(id));
    }

    @GetMapping("/order/{orderId}")
    public ResponseEntity<PaymentResponse> getPaymentByOrderId(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable UUID orderId) {

        Optional<Claims> claimsOpt = validateAndExtractJwt(authHeader);
        if (claimsOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return ResponseEntity.ok(paymentService.getPaymentByOrderId(orderId));
    }

    @PostMapping("/confirm/{paymentIntentId}")
    public ResponseEntity<PaymentResponse> confirmPayment(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable String paymentIntentId) {

        Optional<Claims> claimsOpt = validateAndExtractJwt(authHeader);
        if (claimsOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return ResponseEntity.ok(paymentService.confirmPayment(paymentIntentId));
    }

    @PostMapping("/{id}/refund")
    public ResponseEntity<PaymentResponse> refundPayment(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable UUID id,
            @Valid @RequestBody RefundRequest request) {

        Optional<Claims> claimsOpt = validateAndExtractJwt(authHeader);
        if (claimsOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return ResponseEntity.ok(paymentService.processRefund(id, request));
    }

    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String signature) {

        log.info("Received Stripe webhook");

        if (!stripeConfig.validateWebhookSignature(payload, signature)) {
            log.warn("Invalid webhook signature");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid signature");
        }

        try {
            JsonNode jsonNode = objectMapper.readTree(payload);
            String eventType = jsonNode.get("type").asText();
            JsonNode data = jsonNode.get("data").get("object");

            WebhookPayload webhookPayload = WebhookPayload.builder()
                    .eventId(jsonNode.get("id").asText())
                    .eventType(eventType)
                    .stripeId(data.has("id") ? data.get("id").asText() : null)
                    .paymentIntentId(data.has("payment_intent") ? data.get("payment_intent").asText() : null)
                    .chargeId(data.has("charge") ? data.get("charge").asText() : null)
                    .status(data.has("status") ? data.get("status").asText() : null)
                    .failureReason(data.has("last_payment_error")
                            ? data.get("last_payment_error").get("message").asText() : null)
                    .amount(data.has("amount") ? data.get("amount").asLong() : null)
                    .currency(data.has("currency") ? data.get("currency").asText() : null)
                    .build();

            paymentService.handleWebhook(webhookPayload);

            return ResponseEntity.ok("Webhook processed");
        } catch (Exception e) {
            log.error("Failed to process webhook: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Processing failed");
        }
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }

    private Optional<Claims> validateAndExtractJwt(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("Missing or invalid Authorization header");
            return Optional.empty();
        }

        String token = authHeader.substring(7);
        return jwtUtil.validateToken(token);
    }
}