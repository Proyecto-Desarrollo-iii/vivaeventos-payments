package co.empresa.vivaeventos.payments.config;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

@Component
@Slf4j
public class NotificationsClient {

    private final RestTemplate restTemplate;
    private final String notificationsBaseUrl;
    private final SecretKey signingKey;

    public NotificationsClient(
            @Value("${services.notifications.url:http://localhost:8087}") String notificationsBaseUrl,
            @Value("${jwt.secret}") String jwtSecret) {
        this.restTemplate = new RestTemplate();
        this.notificationsBaseUrl = notificationsBaseUrl;
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

    public void sendNotification(UUID userId, String recipient, String eventType, String channel, Map<String, String> variables) {
        try {
            String url = notificationsBaseUrl + "/api/v1/notifications";
            
            Map<String, Object> requestBody = new java.util.HashMap<>();
            requestBody.put("userId", userId);
            requestBody.put("recipient", recipient);
            requestBody.put("eventType", eventType);
            requestBody.put("channel", channel);
            requestBody.put("variables", variables);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, authHeaders());

            restTemplate.exchange(url, HttpMethod.POST, request, Void.class);
            log.info("Notification {} sent to user {} via {}", eventType, userId, channel);
        } catch (Exception e) {
            log.error("Failed to send notification {}: {}", eventType, e.getMessage());
        }
    }
}
