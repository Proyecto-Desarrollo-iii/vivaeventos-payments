package co.empresa.vivaeventos.payments.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditEventClientTest {

    @Mock
    private RestTemplate restTemplate;

    private AuditEventClient auditEventClient;

    @Captor
    private ArgumentCaptor<HttpEntity<Map<String, Object>>> entityCaptor;

    @BeforeEach
    void setUp() {
        String jwtSecret = "dGhpc0lzQVZlcnlTZWNyZXRLZXlGb3JWYWlhRXZlbnRvc1RoYXROZWVkczUw";
        auditEventClient = new AuditEventClient("http://audit:8089", jwtSecret);
        ReflectionTestUtils.setField(auditEventClient, "restTemplate", restTemplate);
    }

    @Test
    void shouldSendAuditEvent() {
        auditEventClient.logEvent(new AuditEventRequest("payments", "550e8400-e29b-41d4-a716-446655440000", "CLIENT",
                "PAYMENT", "payment", "550e8400-e29b-41d4-a716-446655440000",
                "{\"prev\":\"pending\"}", "{\"amount\":100}"));

        verify(restTemplate).exchange(
                eq("http://audit:8089/api/v1/audit/log"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(Void.class)
        );
    }

    @Test
    void shouldIncludeAuthorizationHeader() {
        auditEventClient.logEvent(new AuditEventRequest("payments", null, null,
                "HTTP_REQUEST", "GET", null, null, null));

        verify(restTemplate).exchange(
                anyString(),
                eq(HttpMethod.POST),
                entityCaptor.capture(),
                eq(Void.class)
        );

        HttpEntity<Map<String, Object>> entity = entityCaptor.getValue();
        assertNotNull(entity.getHeaders());
        assertTrue(entity.getHeaders().get("Authorization").get(0).startsWith("Bearer "));
    }

    @Test
    void shouldIncludeRequestBodyFields() {
        auditEventClient.logEvent(new AuditEventRequest("orders", "660e8400-e29b-41d4-a716-446655440001", "ADMIN",
                "CREATE", "order", "660e8400-e29b-41d4-a716-446655440001",
                null, "{\"total\":100}"));

        verify(restTemplate).exchange(
                anyString(),
                eq(HttpMethod.POST),
                entityCaptor.capture(),
                eq(Void.class)
        );

        Map<String, Object> body = entityCaptor.getValue().getBody();
        assertEquals("orders", body.get("serviceName"));
        assertEquals("660e8400-e29b-41d4-a716-446655440001", body.get("userId").toString());
        assertEquals("ADMIN", body.get("userRole"));
        assertEquals("{\"total\":100}", body.get("newValues"));
    }

    @Test
    void shouldHandleRestTemplateExceptionGracefully() {
        when(restTemplate.exchange(anyString(), any(HttpMethod.class), any(), eq(Void.class)))
                .thenThrow(new RuntimeException("Connection refused"));

        assertDoesNotThrow(() ->
                auditEventClient.logEvent(new AuditEventRequest("payments", null, null,
                        "TEST", "test", null, null, null))
        );
    }
}
