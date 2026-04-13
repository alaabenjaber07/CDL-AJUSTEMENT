package com.cdl.ajustement.repository;

import com.cdl.ajustement.entity.QueryExecutionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QueryExecutionLogRepository extends JpaRepository<QueryExecutionLog, Long> {
    List<QueryExecutionLog> findByConfigNameOrderByExecutionDateDesc(String configName);
    List<QueryExecutionLog> findAllByOrderByExecutionDateDesc();
}
