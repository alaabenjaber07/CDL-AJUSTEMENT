package com.cdl.ajustement.controller;

import com.cdl.ajustement.dto.ColumnDefinition;
import com.cdl.ajustement.dto.UpdateRequest;
import com.cdl.ajustement.service.DynamicDbService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dynamic")
@CrossOrigin(origins = "*")
public class DynamicCrudController {

    private final DynamicDbService dynamicDbService;

    public DynamicCrudController(DynamicDbService dynamicDbService) {
        this.dynamicDbService = dynamicDbService;
    }

    private String getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth != null && auth.getName() != null) ? auth.getName() : "ANONYMOUS";
    }

    @GetMapping("/tables")
    public List<Map<String, Object>> getTables() {
        return dynamicDbService.getAllTables();
    }

    @GetMapping("/{tableName}/columns")
    public List<ColumnDefinition> getColumns(@PathVariable String tableName) {
        return dynamicDbService.getTableColumns(tableName);
    }

    @GetMapping("/{tableName}")
    public List<Map<String, Object>> getTableData(@PathVariable String tableName) {
        return dynamicDbService.getTableData(tableName);
    }

    @PostMapping("/{tableName}")
    public ResponseEntity<Void> insertRow(@PathVariable String tableName, @RequestBody Map<String, Object> data) {
        dynamicDbService.insertRow(tableName, data, getCurrentUser());
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{tableName}")
    public ResponseEntity<Void> updateRow(@PathVariable String tableName, @RequestBody UpdateRequest request) {
        dynamicDbService.updateRow(tableName, request.getKeys(), request.getData(), getCurrentUser());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{tableName}")
    public ResponseEntity<Void> deleteRow(@PathVariable String tableName, @RequestBody Map<String, Object> keys) {
        dynamicDbService.deleteRow(tableName, keys, getCurrentUser());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{tableName}/all")
    public ResponseEntity<Void> deleteAll(@PathVariable String tableName) {
        dynamicDbService.deleteAll(tableName, getCurrentUser());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{tableName}/bulk")
    public ResponseEntity<Void> insertBulk(@PathVariable String tableName, @RequestBody List<Map<String, Object>> dataArray) {
        dynamicDbService.insertBulk(tableName, dataArray, getCurrentUser());
        return ResponseEntity.ok().build();
    }
}
