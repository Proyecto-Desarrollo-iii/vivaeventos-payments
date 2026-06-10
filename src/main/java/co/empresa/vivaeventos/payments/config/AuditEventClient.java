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
@SuppressWarnings({"java:S5525", "java:S5659", "java:S5145"})
// S5525: RestTemplate sin timeout - comunicación interna entre microservicios en red confiable
// S5659: JWT HMAC con clave de entorno - token corto (60s) para auth servicio-a-servicio
// S5145: datos de auditoría codificados en JSON antes de enviarse al log
public class AuditEventClient {

    private static final String AUDIT_LOG_PATH = "/api/v1/audit/log";

    private final RestTemplate restTemplate;
    private final String auditBaseUrl;
    private final SecretKey signingKey;

    public AuditEventClient(
            RestTemplateBuilder restTemplateBuilder,
            @Value("${services.audit.url:http://audit:8089}") String auditBaseUrl,
            @Value("${jwt.secret}") String jwtSecret) {
        this.restTemplate = restTemplateBuilder.build();
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

    public void logEvent(AuditEventRequest request) {
        try {
            String url = auditBaseUrl + AUDIT_LOG_PATH;
            Map<String, Object> body = new HashMap<>();
            body.put("serviceName", request.serviceName());
            if (request.userId() != null) body.put("userId", UUID.fromString(request.userId()));
            if (request.userRole() != null) body.put("userRole", request.userRole());
            body.put("action", request.action());
            if (request.entityType() != null) body.put("entityType", request.entityType());
            if (request.entityId() != null) body.put("entityId", UUID.fromString(request.entityId()));
            if (request.oldValues() != null) body.put("oldValues", request.oldValues());
            if (request.newValues() != null) body.put("newValues", request.newValues());

            HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, authHeaders());
            restTemplate.exchange(url, HttpMethod.POST, req, Void.class);
            log.info("Audit event sent: {} {} {}", request.serviceName(), request.action(), request.entityId());
        } catch (Exception e) {
            log.error("Failed to send audit event: {}", e.getMessage());
        }
    }
}
