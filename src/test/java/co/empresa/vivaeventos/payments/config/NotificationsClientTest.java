package co.empresa.vivaeventos.payments.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationsClientTest {

    private static final String TEST_SECRET = "dGhpc0lzQVZlcnlTZWNyZXRLZXlGb3JWYWlhRXZlbnRvc1RoYXROZWVkczUw";
    private NotificationsClient client;
    private RestTemplate mockRestTemplate;

    @BeforeEach
    void setUp() {
        mockRestTemplate = mock(RestTemplate.class);
        RestTemplateBuilder builder = mock(RestTemplateBuilder.class);
        when(builder.build()).thenReturn(mockRestTemplate);
        client = new NotificationsClient(builder, "http://localhost:18087", TEST_SECRET);
    }

    @Test
    void sendNotification_whenSuccessful_sendsRequest() {
        UUID userId = UUID.randomUUID();

        client.sendNotification(userId, "user@test.com", "PURCHASE", "EMAIL", Map.of("key", "value"));

        verify(mockRestTemplate).exchange(
                eq("http://localhost:18087/api/v1/notifications"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(Void.class));
    }

    @Test
    void sendNotification_whenException_doesNotThrow() {
        UUID userId = UUID.randomUUID();

        doThrow(new RuntimeException("Connection error"))
                .when(mockRestTemplate).exchange(
                        eq("http://localhost:18087/api/v1/notifications"),
                        eq(HttpMethod.POST),
                        any(HttpEntity.class),
                        eq(Void.class));

        client.sendNotification(userId, "user@test.com", "PURCHASE", "EMAIL", Map.of("key", "value"));
    }
}
