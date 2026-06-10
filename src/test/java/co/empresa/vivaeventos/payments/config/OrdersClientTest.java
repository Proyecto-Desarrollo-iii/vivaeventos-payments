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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrdersClientTest {

    private static final String TEST_SECRET = "dGhpc0lzQVZlcnlTZWNyZXRLZXlGb3JWYWlhRXZlbnRvc1RoYXROZWVkczUw";
    private OrdersClient client;
    private RestTemplate mockRestTemplate;

    @BeforeEach
    void setUp() {
        mockRestTemplate = mock(RestTemplate.class);
        RestTemplateBuilder builder = mock(RestTemplateBuilder.class);
        when(builder.build()).thenReturn(mockRestTemplate);
        client = new OrdersClient(builder, "http://localhost:18082", TEST_SECRET);
    }

    @Test
    void updateOrderStatus_whenSuccessful_sendsRequest() {
        UUID orderId = UUID.randomUUID();

        client.updateOrderStatus(orderId, "PAID");

        verify(mockRestTemplate).exchange(
                eq("http://localhost:18082/api/v1/orders/{id}/status?status=PAID"),
                eq(HttpMethod.PUT),
                any(HttpEntity.class),
                eq(Void.class),
                eq(orderId));
    }

    @Test
    void updateOrderStatus_whenException_throwsRuntimeException() {
        UUID orderId = UUID.randomUUID();

        doThrow(new RuntimeException("Connection error"))
                .when(mockRestTemplate).exchange(
                        eq("http://localhost:18082/api/v1/orders/{id}/status?status=PAID"),
                        eq(HttpMethod.PUT),
                        any(HttpEntity.class),
                        eq(Void.class),
                        eq(orderId));

        assertThatThrownBy(() -> client.updateOrderStatus(orderId, "PAID"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to communicate with orders service");
    }

    @Test
    void cancelOrder_whenSuccessful_sendsRequest() {
        UUID orderId = UUID.randomUUID();

        client.cancelOrder(orderId);

        verify(mockRestTemplate).postForEntity(
                eq("http://localhost:18082/api/v1/orders/{id}/cancel"),
                any(HttpEntity.class),
                eq(Void.class),
                eq(orderId));
    }

    @Test
    void cancelOrder_whenException_throwsRuntimeException() {
        UUID orderId = UUID.randomUUID();

        doThrow(new RuntimeException("Connection error"))
                .when(mockRestTemplate).postForEntity(
                        eq("http://localhost:18082/api/v1/orders/{id}/cancel"),
                        any(HttpEntity.class),
                        eq(Void.class),
                        eq(orderId));

        assertThatThrownBy(() -> client.cancelOrder(orderId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to communicate with orders service");
    }

    @Test
    void getOrderById_whenSuccessful_returnsOrderData() {
        UUID orderId = UUID.randomUUID();
        Map<String, Object> expected = Map.of("id", orderId.toString(), "status", "PENDING");

        when(mockRestTemplate.exchange(
                eq("http://localhost:18082/api/v1/orders/{id}"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class),
                eq(orderId)))
                .thenReturn(ResponseEntity.ok(expected));

        Map<String, Object> result = client.getOrderById(orderId);

        assertThat(result).isNotNull();
        assertThat(result.get("id")).isEqualTo(orderId.toString());
    }

    @Test
    void getOrderById_whenException_throwsRuntimeException() {
        UUID orderId = UUID.randomUUID();

        when(mockRestTemplate.exchange(
                eq("http://localhost:18082/api/v1/orders/{id}"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class),
                eq(orderId)))
                .thenThrow(new RuntimeException("Connection error"));

        assertThatThrownBy(() -> client.getOrderById(orderId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to communicate with orders service");
    }
}
