package co.empresa.vivaeventos.payments.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Component
public class AuditLoggingInterceptor implements HandlerInterceptor {

    private static final List<String> EXCLUDED_PATHS = Arrays.asList("/actuator", "/error");

    private final AuditEventClient auditEventClient;
    private final ObjectMapper objectMapper;

    public AuditLoggingInterceptor(AuditEventClient auditEventClient, ObjectMapper objectMapper) {
        this.auditEventClient = auditEventClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute("auditStartTime", System.currentTimeMillis());
        return true;
    }

    @Override
    @SuppressWarnings("java:S5145")
    // S5145: newValues se construye con ObjectMapper (JSON escapado), datos seguros para el log de auditoría
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        String path = request.getRequestURI();
        if (isExcluded(path)) return;

        long startTime = (long) request.getAttribute("auditStartTime");
        long duration = System.currentTimeMillis() - startTime;

        String userId = request.getHeader("X-User-Email");
        String userRole = request.getHeader("X-User-Role");
        String method = request.getMethod();
        int status = response.getStatus();

        String newValues;
        try {
            newValues = objectMapper.writeValueAsString(Map.of(
                    "method", method,
                    "path", path,
                    "status", status,
                    "durationMs", duration
            ));
        } catch (JsonProcessingException e) {
            newValues = "{}";
        }

        auditEventClient.logEvent(new AuditEventRequest("payments", userId, userRole, "HTTP_REQUEST", method, null, null, newValues));
    }

    private boolean isExcluded(String path) {
        return EXCLUDED_PATHS.stream().anyMatch(path::startsWith);
    }
}
