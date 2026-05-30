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
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

@Component
@Slf4j
public class EventsClient {

    private final RestTemplate restTemplate;
    private final String eventsBaseUrl;
    private final SecretKey signingKey;

    public EventsClient(
            @Value("${services.events.url:http://localhost:8081}") String eventsBaseUrl,
            @Value("${jwt.secret}") String jwtSecret) {
        this.restTemplate = new RestTemplate();
        this.eventsBaseUrl = eventsBaseUrl;
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

    public Map<String, Object> getEventById(UUID eventId) {
        try {
            String url = eventsBaseUrl + "/api/v1/events/{id}";
            HttpEntity<Void> request = new HttpEntity<>(authHeaders());

            var response = restTemplate.exchange(
                url, HttpMethod.GET, request,
                new ParameterizedTypeReference<Map<String, Object>>() {},
                eventId
            );
            return response.getBody();
        } catch (Exception e) {
            log.error("Failed to get event {}: {}", eventId, e.getMessage());
            return null;
        }
    }
}
