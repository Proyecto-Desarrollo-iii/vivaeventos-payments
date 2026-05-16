package co.empresa.vivaeventos.payments.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

@Component
@Slf4j
public class OrdersClient {

    private final RestTemplate restTemplate;
    private final String ordersBaseUrl;

    public OrdersClient(
            @Value("${services.orders.url:http://localhost:8082}") String ordersBaseUrl) {
        this.restTemplate = new RestTemplate();
        this.ordersBaseUrl = ordersBaseUrl;
    }

    public void updateOrderStatus(UUID orderId, String status) {
        try {
            String url = ordersBaseUrl + "/api/v1/orders/{id}/status?status=" + status;
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Void> request = new HttpEntity<>(headers);

            restTemplate.exchange(url, HttpMethod.PATCH, request, Void.class, orderId);
            log.info("Order {} status updated to {}", orderId, status);
        } catch (Exception e) {
            log.error("Failed to update order {} status: {}", orderId, e.getMessage());
            throw new RuntimeException("Failed to communicate with orders service", e);
        }
    }

    public void cancelOrder(UUID orderId) {
        try {
            String url = ordersBaseUrl + "/api/v1/orders/{id}/cancel";
            HttpHeaders headers = new HttpHeaders();
            HttpEntity<Void> request = new HttpEntity<>(headers);

            restTemplate.postForEntity(url, request, Void.class, orderId);
            log.info("Order {} cancelled", orderId);
        } catch (Exception e) {
            log.error("Failed to cancel order {}: {}", orderId, e.getMessage());
            throw new RuntimeException("Failed to communicate with orders service", e);
        }
    }
}