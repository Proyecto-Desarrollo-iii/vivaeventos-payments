package co.empresa.vivaeventos.payments.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TicketsClientTest {

    private static final String TEST_SECRET = "dGhpc0lzQVZlcnlTZWNyZXRLZXlGb3JWYWlhRXZlbnRvc1RoYXROZWVkczUw";
    private TicketsClient client;
    private RestTemplate mockRestTemplate;

    @BeforeEach
    void setUp() throws Exception {
        client = new TicketsClient("http://localhost:18085", TEST_SECRET);
        mockRestTemplate = mock(RestTemplate.class);

        Field field = TicketsClient.class.getDeclaredField("restTemplate");
        field.setAccessible(true);
        field.set(client, mockRestTemplate);
    }

    @Test
    void issueTicket_whenSuccessful_sendsRequest() {
        Map<String, Object> ticketRequest = Map.of("orderId", UUID.randomUUID().toString());

        client.issueTicket(ticketRequest);

        verify(mockRestTemplate).postForEntity(
                eq("http://localhost:18085/api/v1/issued-tickets/issue"),
                any(HttpEntity.class),
                eq(Map.class));
    }

    @Test
    void issueTicket_whenException_throwsRuntimeException() {
        Map<String, Object> ticketRequest = Map.of("orderId", UUID.randomUUID().toString());

        doThrow(new RuntimeException("Connection error"))
                .when(mockRestTemplate).postForEntity(
                        eq("http://localhost:18085/api/v1/issued-tickets/issue"),
                        any(HttpEntity.class),
                        eq(Map.class));

        assertThatThrownBy(() -> client.issueTicket(ticketRequest))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to issue ticket");
    }

    @Test
    void releaseTicketsByOrder_whenSuccessful_sendsRequest() {
        UUID orderId = UUID.randomUUID();

        client.releaseTicketsByOrder(orderId);

        verify(mockRestTemplate).postForEntity(
                eq("http://localhost:18085/api/v1/issued-tickets/release-by-order/{orderId}"),
                any(HttpEntity.class),
                eq(Void.class),
                eq(orderId));
    }

    @Test
    void releaseTicketsByOrder_whenException_throwsRuntimeException() {
        UUID orderId = UUID.randomUUID();

        doThrow(new RuntimeException("Connection error"))
                .when(mockRestTemplate).postForEntity(
                        eq("http://localhost:18085/api/v1/issued-tickets/release-by-order/{orderId}"),
                        any(HttpEntity.class),
                        eq(Void.class),
                        eq(orderId));

        assertThatThrownBy(() -> client.releaseTicketsByOrder(orderId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to communicate with tickets service");
    }

    @Test
    void releaseTicketsByOrderWithRetry_succeedsOnFirstAttempt() {
        UUID orderId = UUID.randomUUID();

        client.releaseTicketsByOrderWithRetry(orderId, 3);

        verify(mockRestTemplate).postForEntity(
                eq("http://localhost:18085/api/v1/issued-tickets/release-by-order/{orderId}"),
                any(HttpEntity.class),
                eq(Void.class),
                eq(orderId));
    }

    @Test
    void releaseTicketsByOrderWithRetry_succeedsAfterRetry() {
        UUID orderId = UUID.randomUUID();

        when(mockRestTemplate.postForEntity(
                        eq("http://localhost:18085/api/v1/issued-tickets/release-by-order/{orderId}"),
                        any(HttpEntity.class),
                        eq(Void.class),
                        eq(orderId)))
                .thenThrow(new RuntimeException("First attempt failed"))
                .thenReturn(ResponseEntity.ok().build());

        client.releaseTicketsByOrderWithRetry(orderId, 3);

        verify(mockRestTemplate, times(2)).postForEntity(
                eq("http://localhost:18085/api/v1/issued-tickets/release-by-order/{orderId}"),
                any(HttpEntity.class),
                eq(Void.class),
                eq(orderId));
    }

    @Test
    void releaseTicketsByOrderWithRetry_failsAfterAllRetries_throwsException() {
        UUID orderId = UUID.randomUUID();

        doThrow(new RuntimeException("Always fails"))
                .when(mockRestTemplate).postForEntity(
                        eq("http://localhost:18085/api/v1/issued-tickets/release-by-order/{orderId}"),
                        any(HttpEntity.class),
                        eq(Void.class),
                        eq(orderId));

        assertThatThrownBy(() -> client.releaseTicketsByOrderWithRetry(orderId, 3))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to release tickets after 3 attempts");

        verify(mockRestTemplate, times(3)).postForEntity(
                eq("http://localhost:18085/api/v1/issued-tickets/release-by-order/{orderId}"),
                any(HttpEntity.class),
                eq(Void.class),
                eq(orderId));
    }
}
