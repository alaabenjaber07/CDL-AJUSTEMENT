package com.cdl.ajustement.service;

import com.cdl.ajustement.entity.QueryConfig;
import com.cdl.ajustement.entity.QueryExecutionLog;
import com.cdl.ajustement.repository.QueryConfigRepository;
import com.cdl.ajustement.repository.QueryExecutionLogRepository;
import com.cdl.ajustement.repository.QueryExtractionLogRepository;
import com.cdl.ajustement.entity.QueryExtractionLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class QueryExecutionService {

    @Autowired
    private QueryConfigRepository configRepository;

    @Autowired
    private QueryExecutionLogRepository logRepository;

    @Autowired
    private QueryExtractionLogRepository extractionLogRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ConcurrentHashMap<String, String> activeRuns = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CompletableFuture<?>> runFutures = new ConcurrentHashMap<>();
    // Track extraction jobs by configName + "_" + index
    private final ConcurrentHashMap<String, CompletableFuture<?>> extractionFutures = new ConcurrentHashMap<>();

    public void executeConfiguredQuery(String configName) {
        logRepository.findAllByOrderByExecutionDateDesc().stream()
                .filter(l -> l.getConfigName().equals(configName) && "STARTED".equals(l.getStatus()))
                .findFirst()
                .ifPresent(l -> {
                    throw new RuntimeException("Un traitement est déjà en cours pour cette configuration.");
                });

        QueryConfig config = configRepository.findByConfigName(configName)
                .orElseThrow(() -> new RuntimeException("Configuration not found: " + configName));

        String username = org.springframework.security.core.context.SecurityContextHolder.getContext()
                .getAuthentication().getName();
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        String runId = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(now);
        this.activeRuns.put(configName, runId);

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

        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            try {
                ensureConfigSchemaUpToDate();
                ensureSuiviTraceExists();
                String query = config.getExecutionQuery();
                // Improved SQL cleaning: remove all variants of comments
                String queryToExecute = query.replaceAll("(?m)^\\s*--.*$", "")
                        .replaceAll("(?s)/\\*.*?\\*/", "")
                        .trim();

                String runIdMarker = ":run_id";
                String runIdValue = "TO_DATE('" + runId + "', 'YYYY-MM-DD HH24:MI:SS')";
                queryToExecute = queryToExecute.replaceAll("(?i)" + java.util.regex.Pattern.quote(runIdMarker),
                        runIdValue);

                jdbcTemplate.execute(queryToExecute);

                executionLog.setStatus("SUCCESS");
                logRepository.save(executionLog);
            } catch (Exception e) {
                String errorMsg = e.getMessage() != null ? e.getMessage() : "Unknown execution error";
                if (errorMsg.length() > 240) {
                    errorMsg = errorMsg.substring(0, 240) + "...";
                }
                executionLog.setStatus("FAILED: " + errorMsg);
                logRepository.save(executionLog);
            } finally {
                activeRuns.remove(configName);
                runFutures.remove(configName);
            }
        });

        runFutures.put(configName, future);
    }

    public List<QueryExecutionLog> getAllExecutionLogs() {
        return logRepository.findAllByOrderByExecutionDateDesc();
    }

    public List<QueryExtractionLog> getAllExtractionLogs() {
        return extractionLogRepository.findAllByOrderByExtractionDateDesc();
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

    public Map<String, Object> getExecutionProgress(String configName) {
        final int TOTAL_ROWS = 440;
        try {
            ensureSuiviTraceExists();
            String runId = activeRuns.get(configName);
            if (runId == null) {
                List<QueryExecutionLog> logs = logRepository.findAllByOrderByExecutionDateDesc();
                QueryExecutionLog latest = logs.stream()
                        .filter(l -> l.getConfigName().equals(configName))
                        .findFirst()
                        .orElse(null);

                if (latest != null && "STARTED".equals(latest.getStatus())) {
                    runId = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                            .format(latest.getExecutionDate());
                } else {
                    if (latest != null && "SUCCESS".equals(latest.getStatus())) {
                        Map<String, Object> res = new java.util.HashMap<>();
                        res.put("count", TOTAL_ROWS);
                        res.put("total", TOTAL_ROWS);
                        res.put("progress", 100.0);
                        res.put("message", "Terminé avec succès (100%)");
                        res.put("status", "SUCCESS");
                        return res;
                    }
                    Map<String, Object> idle = new java.util.HashMap<>();
                    idle.put("count", 0);
                    idle.put("total", TOTAL_ROWS);
                    idle.put("progress", 0.0);
                    idle.put("message", "Prêt...");
                    idle.put("status", latest != null ? latest.getStatus() : "IDLE");
                    return idle;
                }
            }

            Integer countNum = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM suivi_trace WHERE SITUATION = TO_DATE(?, 'YYYY-MM-DD HH24:MI:SS')",
                    Integer.class, runId);

            if (countNum == null || countNum == 0) {
                countNum = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM suivi_trace WHERE EXECUTION >= TO_TIMESTAMP(?, 'YYYY-MM-DD HH24:MI:SS')",
                        Integer.class, runId);
            }
            if (countNum == null)
                countNum = 0;

            String msg = "Initialisation...";
            try {
                String lastMsgQuery = "SELECT TYPE FROM (" +
                        "  SELECT TYPE FROM suivi_trace " +
                        "  WHERE (SITUATION = TO_DATE(?, 'YYYY-MM-DD HH24:MI:SS') " +
                        "     OR EXECUTION >= TO_TIMESTAMP(?, 'YYYY-MM-DD HH24:MI:SS'))" +
                        "    AND TYPE IS NOT NULL " +
                        "    AND TYPE != 'MAJ_FICTIVE_SANS_IMPACT' " +
                        "  ORDER BY EXECUTION DESC, PROG DESC" +
                        ") WHERE ROWNUM = 1";
                msg = jdbcTemplate.queryForObject(lastMsgQuery, String.class, runId, runId);
            } catch (Exception e) {
            }

            double percent = Math.min(100.0, Math.round(countNum * 100.0 / TOTAL_ROWS * 100.0) / 100.0);
            Map<String, Object> response = new java.util.HashMap<>();
            response.put("count", countNum);
            response.put("total", TOTAL_ROWS);
            response.put("progress", percent);
            response.put("message", msg);
            response.put("runId", runId);
            return response;
        } catch (Exception e) {
            Map<String, Object> error = new java.util.HashMap<>();
            error.put("count", 0);
            error.put("total", TOTAL_ROWS);
            error.put("progress", 0.0);
            error.put("message", "Erreur suivi: " + e.getMessage());
            return error;
        }
    }

    public void cancelExecution(String configName) {
        CompletableFuture<?> future = runFutures.remove(configName);
        if (future != null)
            future.cancel(true);
        activeRuns.remove(configName);
        logRepository.findAllByOrderByExecutionDateDesc().stream()
                .filter(l -> l.getConfigName().equals(configName) && "STARTED".equals(l.getStatus()))
                .findFirst()
                .ifPresent(l -> {
                    l.setStatus("CANCELLED");
                    logRepository.save(l);
                });
    }

    public void startExtractionBackground(String configName, int queryIndex, String username) {
        String jobKey = configName + "_" + queryIndex;
        if (extractionFutures.containsKey(jobKey)) {
            throw new RuntimeException("Une extraction est déjà en cours pour cet index.");
        }

        QueryExtractionLog extractionLog = QueryExtractionLog.builder()
                .configName(configName)
                .extractedBy(username)
                .extractionDate(java.time.LocalDateTime.now())
                .extractionIndex(queryIndex)
                .status("STARTED")
                .processedRows(0L)
                .totalRows(0L)
                .build();
        final Long logId = extractionLogRepository.save(extractionLog).getId();

        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            try {
                QueryExtractionLog currentLog = extractionLogRepository.findById(logId)
                        .orElseThrow(() -> new RuntimeException("Log non trouvé"));

                String finalSelect = getFinalSelect(configName, queryIndex);

                try {
                    String countQuery = "SELECT COUNT(*) FROM (" + finalSelect + ")";
                    Long total = jdbcTemplate.queryForObject(countQuery, Long.class);
                    currentLog.setTotalRows(total);
                    extractionLogRepository.save(currentLog);
                } catch (Exception e) {
                }

                java.io.File tempFile = java.io.File.createTempFile("cdl_ext_" + configName + "_" + queryIndex + "_",
                        ".csv");
                try (java.io.FileOutputStream fos = new java.io.FileOutputStream(tempFile)) {
                    generateCsvStreamWithTracking(finalSelect, fos, logId);
                }

                currentLog = extractionLogRepository.findById(logId)
                        .orElseThrow(() -> new RuntimeException("Log non trouvé"));
                currentLog.setStatus("SUCCESS");
                currentLog.setFilePath(tempFile.getAbsolutePath());
                extractionLogRepository.save(currentLog);
            } catch (Exception e) {
                if (!(e instanceof java.util.concurrent.CancellationException)) {
                    e.printStackTrace();
                }
                extractionLogRepository.findById(logId).ifPresent(l -> {
                    l.setStatus(Thread.currentThread().isInterrupted()
                            || e instanceof java.util.concurrent.CancellationException ? "CANCELLED" : "FAILED");
                    extractionLogRepository.save(l);
                });
            } finally {
                extractionFutures.remove(jobKey);
            }
        });

        extractionFutures.put(jobKey, future);
    }

    public void cancelExtraction(String configName, int index) {
        String jobKey = configName + "_" + index;
        CompletableFuture<?> future = extractionFutures.remove(jobKey);
        if (future != null) {
            future.cancel(true);
        }

        // Update database status
        extractionLogRepository.findTopByConfigNameAndExtractionIndexOrderByExtractionDateDesc(configName, index)
                .ifPresent(log -> {
                    if ("STARTED".equals(log.getStatus())) {
                        log.setStatus("CANCELLED");
                        extractionLogRepository.save(log);
                    }
                });
    }

    private String getFinalSelect(String configName, int queryIndex) {
        QueryConfig config = configRepository.findByConfigName(configName)
                .orElseThrow(() -> new RuntimeException("Configuration not found: " + configName));
        String query = (queryIndex == 2) ? config.getExtractionQuery2() : config.getExtractionQuery();
        if (query == null || query.trim().isEmpty()) {
            throw new RuntimeException("Recherche d'extraction " + queryIndex + " non configurée.");
        }
        // Robust cleaning: multiple replaceAll to catch all comment types
        String cleanQuery = query.replaceAll("(?m)^\\s*--.*$", "")
                .replaceAll("(?s)/\\*.*?\\*/", "")
                .trim();

        String[] statements = cleanQuery.split(";");
        String finalSelect = null;
        for (String stmt : statements) {
            String s = stmt.trim();
            if (s.isEmpty())
                continue;
            if (s.toLowerCase().startsWith("select")) {
                finalSelect = s;
                break;
            } else {
                jdbcTemplate.execute(s);
            }
        }
        return (finalSelect != null) ? finalSelect : query;
    }

    private void generateCsvStreamWithTracking(String finalSelect, java.io.OutputStream out, Long logId) {
        try (java.io.PrintWriter writer = new java.io.PrintWriter(
                new java.io.OutputStreamWriter(out, java.nio.charset.StandardCharsets.UTF_8))) {
            writer.print('\ufeff');
            jdbcTemplate.query(finalSelect, rs -> {
                java.sql.ResultSetMetaData metaData = rs.getMetaData();
                int columnCount = metaData.getColumnCount();
                for (int i = 1; i <= columnCount; i++) {
                    writer.print("\"" + metaData.getColumnName(i) + "\"");
                    if (i < columnCount)
                        writer.print(";");
                }
                writer.println();
                long count = 0;
                while (rs.next()) {
                    if (Thread.currentThread().isInterrupted()) {
                        throw new RuntimeException("Job annulé par l'utilisateur");
                    }
                    for (int i = 1; i <= columnCount; i++) {
                        Object val = rs.getObject(i);
                        if (val != null) {
                            String s = val.toString().replace("\"", "\"\"");
                            writer.print("\"" + s + "\"");
                        }
                        if (i < columnCount)
                            writer.print(";");
                    }
                    writer.println();
                    count++;
                    if (logId != null && count % 2000 == 0) {
                        final long currentCount = count;
                        extractionLogRepository.findById(logId).ifPresent(l -> {
                            l.setProcessedRows(currentCount);
                            extractionLogRepository.save(l);
                        });
                        writer.flush();
                    }
                }
                if (logId != null) {
                    final long finalCount = count;
                    extractionLogRepository.findById(logId).ifPresent(l -> {
                        l.setProcessedRows(finalCount);
                        extractionLogRepository.save(l);
                    });
                }
                return null;
            });
            writer.flush();
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("annulé")) {
                // Cancellation already handled
            } else {
                throw new RuntimeException("Error generating CSV file", e);
            }
        }
    }

    public QueryExtractionLog getExtractionStatus(String configName, int queryIndex) {
        return extractionLogRepository
                .findTopByConfigNameAndExtractionIndexOrderByExtractionDateDesc(configName, queryIndex)
                .orElse(null);
    }

    public QueryExtractionLog getExtractionLogById(Long id) {
        return extractionLogRepository.findById(id).orElse(null);
    }

    private void ensureSuiviTraceExists() {
        try {
            jdbcTemplate.execute("SELECT 1 FROM suivi_trace WHERE ROWNUM = 1");
        } catch (Exception e) {
        }
    }

    private void ensureConfigSchemaUpToDate() {
        try {
            jdbcTemplate.execute("SELECT EXTRACTION_QUERY_2 FROM CDL_QUERY_CONFIG WHERE ROWNUM = 1");
        } catch (Exception e) {
            try {
                jdbcTemplate.execute("ALTER TABLE CDL_QUERY_CONFIG ADD EXTRACTION_QUERY_2 CLOB");
            } catch (Exception ex) {
            }
        }
    }

    public void updateDefaultConfig() {
        System.out.println("Opération ignorée : La requête est configurée via l'interface.");
    }
}
