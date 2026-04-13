package com.cdl.ajustement.controller;

import com.cdl.ajustement.entity.AuditLog;
import com.cdl.ajustement.repository.AuditLogRepository;
import com.cdl.ajustement.service.AuditService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/audit")
@CrossOrigin(origins = "*")
public class AuditLogController {

    private final AuditLogRepository auditLogRepository;
    private final AuditService auditService;

    public AuditLogController(AuditLogRepository auditLogRepository, AuditService auditService) {
        this.auditLogRepository = auditLogRepository;
        this.auditService = auditService;
    }

    @GetMapping("/{tableName}")
    public List<AuditLog> getLogsForTable(@PathVariable String tableName) {
        return auditLogRepository.findByTableNameOrderByActionDateDesc(tableName.toUpperCase());
    }

    @GetMapping
    public List<AuditLog> getAllLogs() {
        return auditLogRepository.findAll();
    }

    @PostMapping("/revert/{id}")
    public ResponseEntity<String> revertAction(@PathVariable Long id) {
        try {
            auditService.revertAction(id);
            return ResponseEntity.ok("Action revertie avec succès");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Erreur lors du revert : " + e.getMessage());
        }
    }
}
