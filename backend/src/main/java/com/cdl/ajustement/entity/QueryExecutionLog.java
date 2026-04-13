package com.cdl.ajustement.entity;

import lombok.*;
import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "CDL_QUERY_EXECUTION_LOG")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QueryExecutionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "CONFIG_NAME", nullable = false)
    private String configName;

    @Column(name = "EXECUTED_BY", nullable = false)
    private String executedBy;

    @Column(name = "EXECUTION_DATE", nullable = false)
    private LocalDateTime executionDate;

    @Column(name = "STATUS")
    private String status;
}
