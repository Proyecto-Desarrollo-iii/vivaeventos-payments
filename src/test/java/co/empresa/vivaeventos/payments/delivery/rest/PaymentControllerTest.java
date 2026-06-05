package co.empresa.vivaeventos.payments.delivery.rest;

import co.empresa.vivaeventos.payments.config.JwtUtil;
import co.empresa.vivaeventos.payments.config.StripeConfig;
import co.empresa.vivaeventos.payments.domain.model.Dto.PaymentResponse;
import co.empresa.vivaeventos.payments.domain.model.Payment;
import co.empresa.vivaeventos.payments.domain.service.IPaymentService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PaymentController.class)
class PaymentControllerTest {

    private static final String CANCEL_URL = "/api/v1/payments/order/{orderId}/cancel";
    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final String VALID_TOKEN = "Bearer valid.jwt.token";
    private static final String AUTH_HEADER = "Authorization";
    private static final String ERROR_JSON_PATH = "$.error";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IPaymentService paymentService;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private StripeConfig stripeConfig;

    @Test
    void cancelPaymentSuccessReturns200WithPaymentResponse() throws Exception {
        when(jwtUtil.validateToken(anyString())).thenReturn(Optional.of(mockClaims()));

        PaymentResponse response = PaymentResponse.fromEntity(
                Payment.builder()
                        .id(UUID.randomUUID())
                        .orderId(ORDER_ID)
                        .amount(BigDecimal.valueOf(50000))
                        .status(Payment.PaymentStatus.CANCELLED)
                        .build());
        when(paymentService.cancelPayment(ORDER_ID)).thenReturn(response);

        mockMvc.perform(post(CANCEL_URL, ORDER_ID)
                        .header(AUTH_HEADER, VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void cancelPaymentPaidPaymentReturns409Conflict() throws Exception {
        when(jwtUtil.validateToken(anyString())).thenReturn(Optional.of(mockClaims()));
        when(paymentService.cancelPayment(ORDER_ID))
                .thenThrow(new IllegalStateException("Cannot cancel a paid payment"));

        mockMvc.perform(post(CANCEL_URL, ORDER_ID)
                        .header(AUTH_HEADER, VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath(ERROR_JSON_PATH).value("Cannot cancel a paid payment"));
    }

    @Test
    void cancelPaymentAlreadyCancelledReturns409Conflict() throws Exception {
        when(jwtUtil.validateToken(anyString())).thenReturn(Optional.of(mockClaims()));
        when(paymentService.cancelPayment(ORDER_ID))
                .thenThrow(new IllegalStateException("Payment already cancelled"));

        mockMvc.perform(post(CANCEL_URL, ORDER_ID)
                        .header(AUTH_HEADER, VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath(ERROR_JSON_PATH).value("Payment already cancelled"));
    }

    @Test
    void cancelPaymentNotFoundReturns404NotFound() throws Exception {
        when(jwtUtil.validateToken(anyString())).thenReturn(Optional.of(mockClaims()));
        when(paymentService.cancelPayment(ORDER_ID))
                .thenThrow(new IllegalArgumentException("No payment found"));

        mockMvc.perform(post(CANCEL_URL, ORDER_ID)
                        .header(AUTH_HEADER, VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath(ERROR_JSON_PATH).value("No payment found"));
    }

    @Test
    void cancelPaymentGenericExceptionReturns500InternalServerError() throws Exception {
        when(jwtUtil.validateToken(anyString())).thenReturn(Optional.of(mockClaims()));
        when(paymentService.cancelPayment(ORDER_ID))
                .thenThrow(new RuntimeException("Unexpected error"));

        mockMvc.perform(post(CANCEL_URL, ORDER_ID)
                        .header(AUTH_HEADER, VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath(ERROR_JSON_PATH).value("Error interno al cancelar la orden"));
    }

    @Test
    void cancelPaymentMissingAuthHeaderReturns401() throws Exception {
        mockMvc.perform(post(CANCEL_URL, ORDER_ID)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());

        verify(paymentService, never()).cancelPayment(any());
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "InvalidToken", "Basic abc123" })
    void cancelPaymentInvalidAuthHeaderReturns401(String token) throws Exception {
        mockMvc.perform(post(CANCEL_URL, ORDER_ID)
                        .header(AUTH_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());

        verify(paymentService, never()).cancelPayment(any());
    }

    @Test
    void cancelPaymentInvalidJwtReturns401() throws Exception {
        when(jwtUtil.validateToken(anyString())).thenReturn(Optional.empty());

        mockMvc.perform(post(CANCEL_URL, ORDER_ID)
                        .header(AUTH_HEADER, "Bearer invalid.jwt.token")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());

        verify(paymentService, never()).cancelPayment(any());
    }

    private Claims mockClaims() {
        return org.mockito.Mockito.mock(Claims.class);
    }
}
