package co.empresa.vivaeventos.payments.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
@Slf4j
public class TicketsClient {

    private final RestTemplate restTemplate;
    private final String ticketsBaseUrl;
    private final String jwtSecret;

    public TicketsClient(
            @Value("${services.tickets.url:http://localhost:8085}") String ticketsBaseUrl,
            @Value("${jwt.secret}") String jwtSecret) {
        this.restTemplate = new RestTemplate();
        this.ticketsBaseUrl = ticketsBaseUrl;
        this.jwtSecret = jwtSecret;
    }

    public void releaseTicketsByOrder(UUID orderId) {
        try {
            String url = ticketsBaseUrl + "/api/v1/issued-tickets/release-by-order/{orderId}";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + jwtSecret);

            Map<String, String> body = new HashMap<>();
            body.put("reason", "Payment failed or timeout");
            HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);

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