package com.drivesphere.service;

import com.drivesphere.entity.AuditLog;
import com.drivesphere.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    @Transactional
    public AuditLog logAction(String action, String resourceType, String resourceId, String details, String ipAddress) {
        String actorEmail = "SYSTEM";
        String actorRole = "ANONYMOUS";

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            actorEmail = auth.getName();
            actorRole = auth.getAuthorities().isEmpty() ? "USER" : auth.getAuthorities().iterator().next().getAuthority();
        }

        AuditLog entry = AuditLog.builder()
                .actorEmail(actorEmail)
                .actorRole(actorRole)
                .action(action)
                .resourceType(resourceType)
                .resourceId(resourceId)
                .details(details)
                .ipAddress(ipAddress)
                .build();

        log.info("[AUDIT] Actor: {} ({}) | Action: {} | Resource: {}#{} | Details: {}",
                actorEmail, actorRole, action, resourceType, resourceId, details);

        return auditLogRepository.save(entry);
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> getAuditLogs(Pageable pageable) {
        return auditLogRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    @Transactional(readOnly = true)
    public List<AuditLog> getRecentAuditLogs() {
        return auditLogRepository.findTop50ByOrderByCreatedAtDesc();
    }
}
