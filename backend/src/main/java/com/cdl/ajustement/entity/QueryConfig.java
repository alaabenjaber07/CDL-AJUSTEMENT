package com.cdl.ajustement.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;

@Entity
@Table(name = "CDL_QUERY_CONFIG")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QueryConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "CONFIG_NAME", unique = true, nullable = false)
    private String configName;

    @Lob
    @Column(name = "EXECUTION_QUERY", nullable = false)
    private String executionQuery;

    @Lob
    @Column(name = "EXTRACTION_QUERY", nullable = false)
    private String extractionQuery;

    @Lob
    @Column(name = "EXTRACTION_QUERY_2")
    private String extractionQuery2;

    @Column(name = "EXECUTED_BY")
    private String executedBy;

    @Column(name = "LAST_EXECUTED_AT")
    private java.time.LocalDateTime lastExecutedAt;
}
