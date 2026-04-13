package com.cdl.ajustement.service;

import com.cdl.ajustement.entity.AuditLog;
import com.cdl.ajustement.repository.AuditLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;

    public AuditService(AuditLogRepository auditLogRepository, ObjectMapper objectMapper, JdbcTemplate jdbcTemplate) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    public void logAction(String tableName, String actionType, Map<String, Object> oldValue, Map<String, Object> newValue, String username) {
        try {
            AuditLog log = new AuditLog();
            log.setTableName(tableName.toUpperCase());
            log.setActionType(actionType.toUpperCase());
            log.setUsername(username != null ? username : "USER_MOCK");
            log.setActionDate(LocalDateTime.now());

            if (oldValue != null) log.setOldValue(objectMapper.writeValueAsString(oldValue));
            if (newValue != null) log.setNewValue(objectMapper.writeValueAsString(newValue));

            auditLogRepository.save(log);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Transactional
    public void revertAction(Long id) throws Exception {
        AuditLog log = auditLogRepository.findById(id).orElseThrow(() -> new RuntimeException("Audit Log not found"));
        String tableName = log.getTableName();
        String action = log.getActionType();
        
        // Impossible de revert un revert
        if (action.contains("REVERT")) return;
        
        Map<String, Object> oldData = null;
        Map<String, Object> newData = null;
        
        if (log.getOldValue() != null) oldData = objectMapper.readValue(log.getOldValue(), Map.class);
        if (log.getNewValue() != null) newData = objectMapper.readValue(log.getNewValue(), Map.class);
        
        if ("INSERT".equals(action)) {
            String whereClause = newData.keySet().stream().map(k -> k + " = ?").collect(Collectors.joining(" AND "));
            String sql = "DELETE FROM " + tableName + " WHERE " + whereClause;
            jdbcTemplate.update(sql, newData.values().toArray());
            logAction(tableName, "REVERT_INSERT", newData, null, "SYSTEM_REVERT");
        } else if ("DELETE".equals(action)) {
            String cols = String.join(", ", oldData.keySet());
            String placeholders = oldData.keySet().stream().map(k -> "?").collect(Collectors.joining(", "));
            String sql = "INSERT INTO " + tableName + " (" + cols + ") VALUES (" + placeholders + ")";
            jdbcTemplate.update(sql, oldData.values().toArray());
            logAction(tableName, "REVERT_DELETE", null, oldData, "SYSTEM_REVERT");
        } else if ("UPDATE".equals(action)) {
            String whereClause = newData.keySet().stream().map(k -> k + " = ?").collect(Collectors.joining(" AND "));
            String setClause = oldData.keySet().stream().map(k -> k + " = ?").collect(Collectors.joining(", "));
            String sql = "UPDATE " + tableName + " SET " + setClause + " WHERE " + whereClause;
            List<Object> args = new ArrayList<>(oldData.values());
            args.addAll(newData.values());
            jdbcTemplate.update(sql, args.toArray());
            logAction(tableName, "REVERT_UPDATE", newData, oldData, "SYSTEM_REVERT");
        }
    }
}
