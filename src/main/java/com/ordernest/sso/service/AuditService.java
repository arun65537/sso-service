package com.ordernest.sso.service;

import com.ordernest.sso.model.AuditLog;
import com.ordernest.sso.model.User;
import com.ordernest.sso.repository.AuditLogRepository;
import org.springframework.stereotype.Service;

@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public void log(User user, String eventType, String ipAddress, String userAgent, String details) {
        AuditLog log = new AuditLog();
        log.setUser(user);
        log.setEventType(eventType);
        log.setIpAddress(ipAddress);
        log.setUserAgent(userAgent);
        log.setDetails(details);
        auditLogRepository.save(log);
    }
}
