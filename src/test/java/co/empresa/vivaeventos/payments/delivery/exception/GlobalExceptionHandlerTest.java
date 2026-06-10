package co.empresa.vivaeventos.payments.delivery.exception;

import com.stripe.exception.StripeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    void handleIllegalArgument_returnsBadRequest() {
        IllegalArgumentException ex = new IllegalArgumentException("Invalid argument");

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = handler.handleIllegalArgument(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(400);
        assertThat(response.getBody().message()).isEqualTo("Invalid argument");
        assertThat(response.getBody().timestamp()).isNotNull();
    }

    @Test
    void handleIllegalState_returnsConflict() {
        IllegalStateException ex = new IllegalStateException("Conflict state");

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = handler.handleIllegalState(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(409);
        assertThat(response.getBody().message()).isEqualTo("Conflict state");
    }

    @Test
    void handleStripeException_withCardError_returnsBadGatewayWithSpanishMessage() {
        StripeException ex = mock(StripeException.class);
        doReturn("card_error").when(ex).getCode();
        doReturn("Your card was declined").when(ex).getMessage();

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = handler.handleStripeException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(502);
        assertThat(response.getBody().message()).contains("Error con la tarjeta");
    }

    @Test
    void handleStripeException_withApiConnectionError_returnsBadGateway() {
        StripeException ex = mock(StripeException.class);
        when(ex.getCode()).thenReturn("api_connection_error");
        when(ex.getMessage()).thenReturn("Connection failed");


        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = handler.handleStripeException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Error de conexión con el servicio de pagos");
    }

    @Test
    void handleStripeException_withApiKeyMissing_returnsBadGateway() {
        StripeException ex = mock(StripeException.class);
        when(ex.getCode()).thenReturn("api_key_missing");
        when(ex.getMessage()).thenReturn("No API key provided");


        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = handler.handleStripeException(ex);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Falta la configuración de Stripe");
    }

    @Test
    void handleStripeException_withAuthenticationError_returnsBadGateway() {
        StripeException ex = mock(StripeException.class);
        when(ex.getCode()).thenReturn("authentication_error");
        when(ex.getMessage()).thenReturn("Invalid API key");


        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = handler.handleStripeException(ex);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Error de autenticación con Stripe");
    }

    @Test
    void handleStripeException_withInvalidRequest_returnsBadGateway() {
        StripeException ex = mock(StripeException.class);
        when(ex.getCode()).thenReturn("invalid_request_error");
        when(ex.getMessage()).thenReturn("Invalid parameter");


        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = handler.handleStripeException(ex);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).contains("Solicitud inválida");
    }

    @Test
    void handleStripeException_withRateLimit_returnsBadGateway() {
        StripeException ex = mock(StripeException.class);
        when(ex.getCode()).thenReturn("rate_limit_error");
        when(ex.getMessage()).thenReturn("Too many requests");


        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = handler.handleStripeException(ex);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Demasiadas solicitudes, intenta más tarde");
    }

    @Test
    void handleStripeException_withUnknownCode_returnsDefaultMessage() {
        StripeException ex = mock(StripeException.class);
        when(ex.getCode()).thenReturn("unknown_code");
        when(ex.getMessage()).thenReturn("Something went wrong");


        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = handler.handleStripeException(ex);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).contains("Error procesando el pago");
    }

    @Test
    void handleValidation_returnsBadRequestWithFieldErrors() {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "test");
        bindingResult.addError(new FieldError("test", "amount", "must be positive"));
        bindingResult.addError(new FieldError("test", "orderId", "is required"));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);

        ResponseEntity<GlobalExceptionHandler.ValidationErrorResponse> response = handler.handleValidation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(400);
        assertThat(response.getBody().message()).isEqualTo("Validation failed");
        assertThat(response.getBody().errors()).containsKey("amount");
        assertThat(response.getBody().errors()).containsKey("orderId");
        assertThat(response.getBody().timestamp()).isNotNull();
    }

    @Test
    void handleRuntime_returnsInternalServerError() {
        RuntimeException ex = new RuntimeException("Unexpected error");

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = handler.handleRuntime(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(500);
        assertThat(response.getBody().message()).isEqualTo("Internal server error");
    }
}
