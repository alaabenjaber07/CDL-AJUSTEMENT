package com.cdl.ajustement.controller;

import com.cdl.ajustement.service.QueryExecutionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.annotation.PostConstruct;
import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping("/api/queries")
@CrossOrigin(origins = "*")
public class QueryExecutionController {

    @Autowired
    private QueryExecutionService executionService;

    @PostMapping("/reset-default")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> resetDefaultConfig() {
        executionService.updateDefaultConfig();
        return ResponseEntity.ok(Collections.singletonMap("status", "Default configuration reset"));
    }

    @GetMapping("/configs")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public java.util.List<com.cdl.ajustement.entity.QueryConfig> getAllConfigs() {
        return executionService.getAllConfigs();
    }

    @PostMapping("/configs")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public com.cdl.ajustement.entity.QueryConfig createConfig(@RequestBody com.cdl.ajustement.entity.QueryConfig config) {
        return executionService.saveConfig(config);
    }

    @PutMapping("/configs/{id}")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public com.cdl.ajustement.entity.QueryConfig updateConfig(@PathVariable Long id, @RequestBody com.cdl.ajustement.entity.QueryConfig config) {
        config.setId(id);
        return executionService.saveConfig(config);
    }

    @DeleteMapping("/configs/{id}")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteConfig(@PathVariable Long id) {
        executionService.deleteConfig(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/logs")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public java.util.List<com.cdl.ajustement.entity.QueryExecutionLog> getExecutionLogs() {
        return executionService.getAllExecutionLogs();
    }

    @PostMapping("/execute/{configName}")
    public ResponseEntity<Map<String, String>> executeQuery(@PathVariable String configName) {
        executionService.executeConfiguredQuery(configName);
        return ResponseEntity.ok(Collections.singletonMap("status", "Execution started"));
    }

    @GetMapping("/progress")
    public ResponseEntity<Map<String, Object>> getProgress() {
        return ResponseEntity.ok(executionService.getExecutionProgress());
    }

    @GetMapping("/extract/{configName}")
    public ResponseEntity<byte[]> extractExcel(@PathVariable String configName) {
        byte[] excelBytes = executionService.extractToExcel(configName);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDispositionFormData("attachment", configName + "_extraction.xlsx");
        headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");

        return new ResponseEntity<>(excelBytes, headers, HttpStatus.OK);
    }
}
