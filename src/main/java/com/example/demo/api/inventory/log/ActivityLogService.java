package com.example.demo.api.inventory.log;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ActivityLogService {

    private final ActivityLogRepository activityLogRepository;

    @Transactional
    public void log(String action, String targetType, Long targetId, String targetName, String detail) {
        String username = getCurrentUsername();
        String ipAddress = getClientIpAddress();

        ActivityLog log = ActivityLog.builder()
                .username(username)
                .action(action)
                .targetType(targetType)
                .targetId(targetId)
                .targetName(targetName)
                .detail(detail)
                .ipAddress(ipAddress)
                .build();

        activityLogRepository.save(log);
    }

    public void logCreate(String targetType, Long targetId, String targetName) {
        log("CREATE", targetType, targetId, targetName, null);
    }

    public void logUpdate(String targetType, Long targetId, String targetName, String detail) {
        log("UPDATE", targetType, targetId, targetName, detail);
    }

    public void logDelete(String targetType, Long targetId, String targetName) {
        log("DELETE", targetType, targetId, targetName, null);
    }

    public Page<ActivityLogDTO> getLogs(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return activityLogRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(ActivityLogDTO::fromEntity);
    }

    public Map<String, Object> searchLogs(String username, String action, String targetType, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);

        String user = (username == null || username.isEmpty()) ? null : username;
        String act = (action == null || action.isEmpty()) ? null : action;
        String target = (targetType == null || targetType.isEmpty()) ? null : targetType;

        Page<ActivityLog> logPage = activityLogRepository.searchLogs(user, act, target, pageable);

        List<ActivityLogDTO> content = logPage.getContent().stream()
                .map(ActivityLogDTO::fromEntity)
                .collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("content", content);
        result.put("currentPage", logPage.getNumber());
        result.put("totalPages", logPage.getTotalPages());
        result.put("totalElements", logPage.getTotalElements());
        result.put("hasNext", logPage.hasNext());
        result.put("hasPrevious", logPage.hasPrevious());

        return result;
    }

    @Transactional
    public int deleteOldLogs(int daysToKeep) {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(daysToKeep);
        List<ActivityLog> oldLogs = activityLogRepository.findByCreatedAtBefore(cutoffDate);
        int count = oldLogs.size();
        activityLogRepository.deleteAll(oldLogs);
        return count;
    }

    private String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            return auth.getName();
        }
        return "SYSTEM";
    }

    private String getClientIpAddress() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                String ip = request.getHeader("X-Forwarded-For");
                if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                    ip = request.getHeader("X-Real-IP");
                }
                if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                    ip = request.getRemoteAddr();
                }
                return ip;
            }
        } catch (Exception e) {
            // ignore
        }
        return "UNKNOWN";
    }
}
