package co.empresa.vivaeventos.payments.config;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

@Component
@Slf4j
public class OrdersClient {

    private final RestTemplate restTemplate;
    private final String ordersBaseUrl;
    private final SecretKey signingKey;

    public OrdersClient(
            RestTemplateBuilder restTemplateBuilder,
            @Value("${services.orders.url:http://localhost:8083}") String ordersBaseUrl,
            @Value("${jwt.secret}") String jwtSecret) {
        this.restTemplate = restTemplateBuilder.build();
        this.ordersBaseUrl = ordersBaseUrl;
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret));
    }

    private String generateServiceToken() {
        return Jwts.builder()
                .subject("payments-service")
                .claim("role", "SYSTEM")
                .claim("userId", "00000000-0000-0000-0000-000000000000")
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(60)))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + generateServiceToken());
        return headers;
    }

    public void updateOrderStatus(UUID orderId, String status) {
        try {
            String url = ordersBaseUrl + "/api/v1/orders/{id}/status?status=" + status;
            HttpEntity<Void> request = new HttpEntity<>(authHeaders());

            restTemplate.exchange(url, HttpMethod.PUT, request, Void.class, orderId);
            log.info("Order {} status updated to {}", orderId, status);
        } catch (Exception e) {
            log.error("Failed to update order {} status: {}", orderId, e.getMessage());
            throw new RuntimeException("Failed to communicate with orders service", e);
        }
    }

    public void cancelOrder(UUID orderId) {
        try {
            String url = ordersBaseUrl + "/api/v1/orders/{id}/cancel";
            HttpEntity<Void> request = new HttpEntity<>(authHeaders());

            restTemplate.postForEntity(url, request, Void.class, orderId);
            log.info("Order {} cancelled", orderId);
        } catch (Exception e) {
            log.error("Failed to cancel order {}: {}", orderId, e.getMessage());
            throw new RuntimeException("Failed to communicate with orders service", e);
        }
    }

    public Map<String, Object> getOrderById(UUID orderId) {
        try {
            String url = ordersBaseUrl + "/api/v1/orders/{id}";
            HttpEntity<Void> request = new HttpEntity<>(authHeaders());

            var response = restTemplate.exchange(
                url, HttpMethod.GET, request,
                new ParameterizedTypeReference<Map<String, Object>>() {},
                orderId
            );
            return response.getBody();
        } catch (Exception e) {
            log.error("Failed to get order {}: {}", orderId, e.getMessage());
            throw new RuntimeException("Failed to communicate with orders service", e);
        }
    }
}