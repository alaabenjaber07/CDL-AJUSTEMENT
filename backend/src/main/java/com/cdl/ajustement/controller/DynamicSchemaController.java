package com.cdl.ajustement.controller;

import com.cdl.ajustement.dto.TableDefinition;
import com.cdl.ajustement.dto.ColumnDefinition;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/schema")
@CrossOrigin(origins = "*")
public class DynamicSchemaController {

    private final JdbcTemplate jdbcTemplate;

    public DynamicSchemaController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostMapping("/create-table")
    public ResponseEntity<String> createTable(@RequestBody TableDefinition tableDef) {
        StringBuilder sql = new StringBuilder("CREATE TABLE ");
        sql.append(tableDef.getTableName().toUpperCase()).append(" (");

        for (int i = 0; i < tableDef.getColumns().size(); i++) {
            ColumnDefinition col = tableDef.getColumns().get(i);
            sql.append(col.getName().toUpperCase()).append(" ").append(col.getType().toUpperCase());
            if (col.isPrimaryKey()) {
                sql.append(" PRIMARY KEY");
            }
            if (i < tableDef.getColumns().size() - 1) {
                sql.append(", ");
            }
        }
        sql.append(")");

        try {
            jdbcTemplate.execute(sql.toString());
            // Ajout au référentiel Managed Tables
            try {
                jdbcTemplate.update("INSERT INTO CDL_MANAGED_TABLES (TABLE_NAME) VALUES (?)", tableDef.getTableName().toUpperCase());
            } catch(Exception ignored) {}
            
            return ResponseEntity.ok("Table " + tableDef.getTableName() + " created successfully.");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error creating table: " + e.getMessage());
        }
    }
}
