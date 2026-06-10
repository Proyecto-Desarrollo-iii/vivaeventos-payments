package co.empresa.vivaeventos.payments.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EventsClientTest {

    private static final String TEST_SECRET = "dGhpc0lzQVZlcnlTZWNyZXRLZXlGb3JWYWlhRXZlbnRvc1RoYXROZWVkczUw";
    private EventsClient client;
    private RestTemplate mockRestTemplate;

    @BeforeEach
    void setUp() {
        mockRestTemplate = mock(RestTemplate.class);
        RestTemplateBuilder builder = mock(RestTemplateBuilder.class);
        when(builder.build()).thenReturn(mockRestTemplate);
        client = new EventsClient(builder, "http://localhost:18080", TEST_SECRET);
    }

    @Test
    void getEventById_whenSuccessful_returnsEventData() {
        UUID eventId = UUID.randomUUID();
        Map<String, Object> expected = Map.of("id", eventId.toString(), "name", "Test Event");

        when(mockRestTemplate.exchange(
                eq("http://localhost:18080/api/v1/events/{id}"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class),
                eq(eventId)))
                .thenReturn(ResponseEntity.ok(expected));

        Map<String, Object> result = client.getEventById(eventId);

        assertThat(result).isNotNull();
        assertThat(result.get("id")).isEqualTo(eventId.toString());
    }

    @Test
    void getEventById_whenException_returnsNull() {
        UUID eventId = UUID.randomUUID();

        when(mockRestTemplate.exchange(
                eq("http://localhost:18080/api/v1/events/{id}"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class),
                eq(eventId)))
                .thenThrow(new RuntimeException("Connection error"));

        Map<String, Object> result = client.getEventById(eventId);

        assertThat(result).isNull();
    }
}
