package com.cdl.ajustement.service;

import com.cdl.ajustement.entity.QueryConfig;
import com.cdl.ajustement.entity.QueryExecutionLog;
import com.cdl.ajustement.repository.QueryConfigRepository;
import com.cdl.ajustement.repository.QueryExecutionLogRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
public class QueryExecutionService {

    @Autowired
    private QueryConfigRepository configRepository;

    @Autowired
    private QueryExecutionLogRepository logRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private volatile String currentRunId = null;

    /**
     * Executes the query asynchronously so that the client can poll for progress.
     */
    public void executeConfiguredQuery(String configName) {
        QueryConfig config = configRepository.findByConfigName(configName)
                .orElseThrow(() -> new RuntimeException("Configuration not found: " + configName));

        String username = org.springframework.security.core.context.SecurityContextHolder.getContext()
                .getAuthentication().getName();
        java.time.LocalDateTime now = java.time.LocalDateTime.now();

        // Generate a unique ID for this specific run formatted as a timestamp that
        // Oracle DATE can digest
        this.currentRunId = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(now);

        config.setExecutedBy(username);
        config.setLastExecutedAt(now);
        configRepository.save(config);

        QueryExecutionLog executionLog = QueryExecutionLog.builder()
                .configName(configName)
                .executedBy(username)
                .executionDate(now)
                .status("STARTED")
                .build();
        logRepository.save(executionLog);

        CompletableFuture.runAsync(() -> {
            try {
                ensureSuiviTraceExists();
                String query = config.getExecutionQuery();

                // Inject the currentRunId into the PL/SQL block if possible,
                // or assume the query uses a variable we can bind.
                // we'll replace a placeholder in the query string if it exists.
                if (query.contains(":run_id")) {
                    query = query.replace(":run_id", "TO_DATE('" + currentRunId + "', 'YYYY-MM-DD HH24:MI:SS')");
                }

                jdbcTemplate.execute(query);

                executionLog.setStatus("SUCCESS");
                logRepository.save(executionLog);
            } catch (Exception e) {
                String errorMsg = e.getMessage() != null ? e.getMessage() : "Unknown execution error";
                if (errorMsg.length() > 240) {
                    errorMsg = errorMsg.substring(0, 240) + "...";
                }
                executionLog.setStatus("FAILED: " + errorMsg);
                logRepository.save(executionLog);
            }
        });
    }

    public List<QueryExecutionLog> getAllExecutionLogs() {
        return logRepository.findAllByOrderByExecutionDateDesc();
    }

    public List<QueryConfig> getAllConfigs() {
        return configRepository.findAll();
    }

    public QueryConfig saveConfig(QueryConfig config) {
        return configRepository.save(config);
    }

    public void deleteConfig(Long id) {
        configRepository.deleteById(id);
    }

    public Map<String, Object> getExecutionProgress() {
        final int TOTAL_ROWS = 440;

        try {
            ensureSuiviTraceExists();

            if (currentRunId == null) {
                Map<String, Object> idleResponse = new java.util.HashMap<>();
                idleResponse.put("count", 0);
                idleResponse.put("total", TOTAL_ROWS);
                idleResponse.put("progress", 0.0);
                idleResponse.put("message", "Prêt...");
                return idleResponse;
            }

            Integer countNum = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM suivi_trace WHERE SITUATION = TO_DATE(?, 'YYYY-MM-DD HH24:MI:SS')",
                    Integer.class, currentRunId);

            if (countNum == null)
                countNum = 0;

            String msg = "Initialisation...";
            try {
                msg = jdbcTemplate.queryForObject(
                        "SELECT TYPE FROM (" +
                                "  SELECT TYPE FROM suivi_trace " +
                                "  WHERE SITUATION = TO_DATE(?, 'YYYY-MM-DD HH24:MI:SS') " +
                                "    AND TYPE IS NOT NULL " +
                                "    AND TYPE != 'MAJ_FICTIVE_SANS_IMPACT' " +
                                "  ORDER BY EXECUTION DESC, PROG DESC" +
                                ") WHERE ROWNUM = 1",
                        String.class, currentRunId);
            } catch (org.springframework.dao.EmptyResultDataAccessException e) {
                // Keep default message if no result
            }

            double percent = Math.min(100.0, Math.round(countNum * 100.0 / TOTAL_ROWS * 100.0) / 100.0);

            Map<String, Object> response = new java.util.HashMap<>();
            response.put("count", countNum);
            response.put("total", TOTAL_ROWS);
            response.put("progress", percent);
            response.put("message", msg);
            return response;

        } catch (Exception e) {
            e.printStackTrace(); // Log error for tracking
            Map<String, Object> errorResponse = new java.util.HashMap<>();
            errorResponse.put("count", 0);
            errorResponse.put("total", TOTAL_ROWS);
            errorResponse.put("progress", 0.0);
            errorResponse.put("message", "En attente...");
            return errorResponse;
        }
    }

    public byte[] extractToExcel(String configName) {
        QueryConfig config = configRepository.findByConfigName(configName)
                .orElseThrow(() -> new RuntimeException("Configuration not found: " + configName));

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(config.getExtractionQuery());

        try (Workbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Extraction Result");

            if (!rows.isEmpty()) {
                // Header row
                Row headerRow = sheet.createRow(0);
                int colIdx = 0;
                for (String key : rows.get(0).keySet()) {
                    headerRow.createCell(colIdx++).setCellValue(key);
                }

                // Data rows
                int rowIdx = 1;
                for (Map<String, Object> map : rows) {
                    Row row = sheet.createRow(rowIdx++);
                    colIdx = 0;
                    for (Object value : map.values()) {
                        row.createCell(colIdx++).setCellValue(value != null ? value.toString() : "");
                    }
                }
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Error generating Excel file", e);
        }
    }

    // Creating table here just to avoid crashes if it doesn't already exist in DB
    private void ensureSuiviTraceExists() {
        try {
            jdbcTemplate.execute("SELECT 1 FROM suivi_trace WHERE ROWNUM = 1");
        } catch (Exception e) {
            /*
             * jdbcTemplate.execute("CREATE TABLE suivi_trace (" +
             * "ID NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY, " +
             * "PROG NUMBER, " +
             * "TYPE VARCHAR2(255), " +
             * "SITUATION DATE, " +
             * "EXECUTION TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
             */
        }
    }

    public void updateDefaultConfig() {
        // L'application n'utilise plus de requête figée dans le code.
        // La requête est désormais exclusivement lue et gérée depuis la base de données
        // via l'interface utilisateur.
        System.out.println("Opération ignorée : La requête est configurée via l'interface.");
    }

}
