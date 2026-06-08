package co.empresa.vivaeventos.payments.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;
import java.util.List;

@Component
public class AuditLoggingInterceptor implements HandlerInterceptor {

    private static final List<String> EXCLUDED_PATHS = Arrays.asList("/actuator", "/error");

    private final AuditEventClient auditEventClient;

    public AuditLoggingInterceptor(AuditEventClient auditEventClient) {
        this.auditEventClient = auditEventClient;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute("auditStartTime", System.currentTimeMillis());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        String path = request.getRequestURI();
        if (isExcluded(path)) return;

        long startTime = (long) request.getAttribute("auditStartTime");
        long duration = System.currentTimeMillis() - startTime;

        String userId = request.getHeader("X-User-Email");
        String userRole = request.getHeader("X-User-Role");
        String method = request.getMethod();
        int status = response.getStatus();

        String newValues = "{\"method\":\"" + method + "\",\"path\":\"" + path + "\",\"status\":" + status + ",\"durationMs\":" + duration + "}";

        auditEventClient.logEvent(new AuditEventRequest("payments", userId, userRole, "HTTP_REQUEST", method, null, null, newValues));
    }

    private boolean isExcluded(String path) {
        return EXCLUDED_PATHS.stream().anyMatch(path::startsWith);
    }
}
