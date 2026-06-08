package co.empresa.vivaeventos.payments.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditLoggingInterceptorTest {

    @Mock
    private AuditEventClient auditEventClient;
    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private AuditLoggingInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new AuditLoggingInterceptor(auditEventClient, objectMapper);
    }

    @Test
    void preHandleShouldSetStartTimeAttribute() {
        interceptor.preHandle(request, response, null);
        verify(request).setAttribute(eq("auditStartTime"), anyLong());
    }

    @Test
    void preHandleShouldReturnTrue() {
        boolean result = interceptor.preHandle(request, response, null);
        assert result;
    }

    @Test
    void afterCompletionShouldLogHttpRequestWithHeaders() {
        when(request.getAttribute("auditStartTime")).thenReturn(100L);
        when(request.getRequestURI()).thenReturn("/api/v1/payments");
        when(request.getMethod()).thenReturn("POST");
        when(request.getHeader("X-User-Email")).thenReturn("user@test.com");
        when(request.getHeader("X-User-Role")).thenReturn("CLIENT");
        when(response.getStatus()).thenReturn(200);

        interceptor.afterCompletion(request, response, null, null);

        verify(auditEventClient).logEvent(
                argThat(req ->
                        "payments".equals(req.serviceName()) &&
                        "user@test.com".equals(req.userId()) &&
                        "CLIENT".equals(req.userRole()) &&
                        "HTTP_REQUEST".equals(req.action()) &&
                        "POST".equals(req.entityType()) &&
                        req.oldValues() == null &&
                        req.newValues().contains("\"method\":\"POST\"")
                )
        );
    }

    @Test
    void afterCompletionShouldSkipExcludedPaths() {
        when(request.getRequestURI()).thenReturn("/actuator/health");

        interceptor.afterCompletion(request, response, null, null);

        verify(auditEventClient, never()).logEvent(any());
    }
}
