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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
@Slf4j
public class AuditEventClient {

    private final RestTemplate restTemplate;
    private final String auditBaseUrl;
    private final SecretKey signingKey;

    public AuditEventClient(
            @Value("${services.audit.url:http://audit:8089}") String auditBaseUrl,
            @Value("${jwt.secret}") String jwtSecret) {
        this.restTemplate = new RestTemplate();
        this.auditBaseUrl = auditBaseUrl;
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret));
    }

    private String generateServiceToken() {
        return Jwts.builder()
                .subject("payments-service")
                .claim("role", "SYSTEM")
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

    public void logEvent(String serviceName, String userId, String userRole,
                         String action, String entityType, String entityId,
                         String oldValues, String newValues) {
        try {
            String url = auditBaseUrl + "/api/v1/audit/log";
            Map<String, Object> body = new HashMap<>();
            body.put("serviceName", serviceName);
            if (userId != null) body.put("userId", UUID.fromString(userId));
            if (userRole != null) body.put("userRole", userRole);
            body.put("action", action);
            if (entityType != null) body.put("entityType", entityType);
            if (entityId != null) body.put("entityId", UUID.fromString(entityId));
            if (oldValues != null) body.put("oldValues", oldValues);
            if (newValues != null) body.put("newValues", newValues);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, authHeaders());
            restTemplate.exchange(url, HttpMethod.POST, request, Void.class);
            log.info("Audit event sent: {} {} {}", serviceName, action, entityId);
        } catch (Exception e) {
            log.error("Failed to send audit event: {}", e.getMessage());
        }
    }
}
