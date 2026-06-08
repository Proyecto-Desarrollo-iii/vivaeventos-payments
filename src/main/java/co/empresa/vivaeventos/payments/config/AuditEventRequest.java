package co.empresa.vivaeventos.payments.config;

public record AuditEventRequest(
        String serviceName,
        String userId,
        String userRole,
        String action,
        String entityType,
        String entityId,
        String oldValues,
        String newValues
) {}
