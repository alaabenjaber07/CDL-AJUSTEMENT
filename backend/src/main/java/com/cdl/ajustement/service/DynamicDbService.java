package com.cdl.ajustement.service;

import com.cdl.ajustement.dto.ColumnDefinition;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class DynamicDbService {

    private final JdbcTemplate jdbcTemplate;
    private final AuditService auditService;

    public DynamicDbService(JdbcTemplate jdbcTemplate, AuditService auditService) {
        this.jdbcTemplate = jdbcTemplate;
        this.auditService = auditService;
    }

    public List<Map<String, Object>> getAllTables() {
        // Lecture depuis la table référentielle
        String query = "SELECT TABLE_NAME FROM CDL_MANAGED_TABLES ORDER BY TABLE_NAME";
        return jdbcTemplate.queryForList(query);
    }

    public List<ColumnDefinition> getTableColumns(String tableName) {
        String query = "SELECT c.COLUMN_NAME, c.DATA_TYPE, " +
                "CASE WHEN p.COLUMN_NAME IS NOT NULL THEN 'TRUE' ELSE 'FALSE' END as IS_PK " +
                "FROM USER_TAB_COLUMNS c " +
                "LEFT JOIN (SELECT cols.column_name, cons.table_name FROM all_constraints cons, all_cons_columns cols " +
                "WHERE cons.constraint_type = 'P' AND cons.constraint_name = cols.constraint_name AND cons.owner = cols.owner) p " +
                "ON c.TABLE_NAME = p.TABLE_NAME AND c.COLUMN_NAME = p.COLUMN_NAME " +
                "WHERE c.TABLE_NAME = ? ORDER BY c.COLUMN_ID";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(query, tableName.toUpperCase());
        
        List<ColumnDefinition> columns = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            ColumnDefinition col = new ColumnDefinition();
            col.setName((String) row.get("COLUMN_NAME"));
            col.setType((String) row.get("DATA_TYPE"));
            col.setPrimaryKey("TRUE".equals(row.get("IS_PK")));
            columns.add(col);
        }
        return columns;
    }

    public List<Map<String, Object>> getTableData(String tableName) {
        return jdbcTemplate.queryForList("SELECT * FROM " + tableName.toUpperCase());
    }

    @Transactional
    public void insertRow(String tableName, Map<String, Object> data, String user) {
        String cols = String.join(", ", data.keySet());
        String placeholders = data.keySet().stream().map(k -> "?").collect(Collectors.joining(", "));
        String sql = "INSERT INTO " + tableName.toUpperCase() + " (" + cols + ") VALUES (" + placeholders + ")";
        
        jdbcTemplate.update(sql, data.values().toArray());
        auditService.logAction(tableName, "INSERT", null, data, user);
    }

    @Transactional
    public void updateRow(String tableName, Map<String, Object> pkValues, Map<String, Object> newData, String user) {
        String whereClause = pkValues.keySet().stream().map(k -> k + " = ?").collect(Collectors.joining(" AND "));
        String selectSql = "SELECT * FROM " + tableName.toUpperCase() + " WHERE " + whereClause;
        Map<String, Object> oldData = jdbcTemplate.queryForList(selectSql, pkValues.values().toArray())
                .stream().findFirst().orElse(new HashMap<>());

        String setClause = newData.keySet().stream().map(k -> k + " = ?").collect(Collectors.joining(", "));
        String sql = "UPDATE " + tableName.toUpperCase() + " SET " + setClause + " WHERE " + whereClause;
        
        List<Object> args = new ArrayList<>(newData.values());
        args.addAll(pkValues.values());
        
        jdbcTemplate.update(sql, args.toArray());
        auditService.logAction(tableName, "UPDATE", oldData, newData, user);
    }

    @Transactional
    public void deleteRow(String tableName, Map<String, Object> pkValues, String user) {
        String whereClause = pkValues.keySet().stream().map(k -> k + " = ?").collect(Collectors.joining(" AND "));
        String selectSql = "SELECT * FROM " + tableName.toUpperCase() + " WHERE " + whereClause;
        Map<String, Object> oldData = jdbcTemplate.queryForList(selectSql, pkValues.values().toArray())
                .stream().findFirst().orElse(new HashMap<>());

        String sql = "DELETE FROM " + tableName.toUpperCase() + " WHERE " + whereClause;
        jdbcTemplate.update(sql, pkValues.values().toArray());
        auditService.logAction(tableName, "DELETE", oldData, null, user);

    }
    @Transactional
    public void deleteAll(String tableName, String user) {
        List<Map<String, Object>> oldData = jdbcTemplate.queryForList("SELECT * FROM " + tableName.toUpperCase());
        jdbcTemplate.execute("DELETE FROM " + tableName.toUpperCase());
        for (Map<String, Object> row : oldData) {
            auditService.logAction(tableName, "DELETE", row, null, user);
        }

    }
    @Transactional
    public void insertBulk(String tableName, List<Map<String, Object>> dataArray, String user) {
        if(dataArray == null || dataArray.isEmpty()) return;
        
        String cols = String.join(", ", dataArray.get(0).keySet());
        String placeholders = dataArray.get(0).keySet().stream().map(k -> "?").collect(Collectors.joining(", "));
        String sql = "INSERT INTO " + tableName.toUpperCase() + " (" + cols + ") VALUES (" + placeholders + ")";
        
        List<Object[]> batchArgs = new ArrayList<>();
        for (Map<String, Object> data : dataArray) {
            batchArgs.add(data.values().toArray());
            auditService.logAction(tableName, "INSERT_BULK", null, data, user);
        }
        
        jdbcTemplate.batchUpdate(sql, batchArgs);
    }
}
