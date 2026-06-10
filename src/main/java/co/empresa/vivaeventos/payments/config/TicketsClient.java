package co.empresa.vivaeventos.payments.config;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
@Slf4j
public class TicketsClient {

    private final RestTemplate restTemplate;
    private final String ticketsBaseUrl;
    private final SecretKey signingKey;

    public TicketsClient(
            RestTemplateBuilder restTemplateBuilder,
            @Value("${services.tickets.url:http://localhost:8085}") String ticketsBaseUrl,
            @Value("${jwt.secret}") String jwtSecret) {
        this.restTemplate = restTemplateBuilder.build();
        this.ticketsBaseUrl = ticketsBaseUrl;
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

    public void issueTicket(Map<String, Object> ticketRequest) {
        try {
            String url = ticketsBaseUrl + "/api/v1/issued-tickets/issue";
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(ticketRequest, authHeaders());

            restTemplate.postForEntity(url, request, Map.class);
            log.info("Ticket issued for order {}", ticketRequest.get("orderId"));
        } catch (Exception e) {
            log.error("Failed to issue ticket: {}", e.getMessage());
            throw new RuntimeException("Failed to issue ticket", e);
        }
    }

    public void releaseTicketsByOrder(UUID orderId) {
        try {
            String url = ticketsBaseUrl + "/api/v1/issued-tickets/release-by-order/{orderId}";

            Map<String, String> body = new HashMap<>();
            body.put("reason", "Payment failed or timeout");
            HttpEntity<Map<String, String>> request = new HttpEntity<>(body, authHeaders());

            restTemplate.postForEntity(url, request, Void.class, orderId);
            log.info("Released tickets for order {}", orderId);
        } catch (Exception e) {
            log.error("Failed to release tickets for order {}: {}", orderId, e.getMessage());
            throw new RuntimeException("Failed to communicate with tickets service", e);
        }
    }

    public void releaseTicketsByOrderWithRetry(UUID orderId, int maxRetries) {
        int attempts = 0;
        Exception lastException = null;

        while (attempts < maxRetries) {
            try {
                releaseTicketsByOrder(orderId);
                return;
            } catch (Exception e) {
                attempts++;
                lastException = e;
                log.warn("Attempt {}/{} failed to release tickets for order {}", attempts, maxRetries, orderId);
                if (attempts < maxRetries) {
                    try {
                        Thread.sleep(1000 * attempts);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        log.error("All retry attempts failed for order {}", orderId, lastException);
        throw new RuntimeException("Failed to release tickets after " + maxRetries + " attempts", lastException);
    }
}