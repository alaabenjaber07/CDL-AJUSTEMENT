package com.cdl.ajustement.entity;

import lombok.Data;
import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "CDL_AUDIT_LOG")
@Data
public class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "TABLE_NAME", nullable = false)
    private String tableName;

    @Column(name = "ACTION_TYPE", nullable = false)
    private String actionType; // INSERT, UPDATE, DELETE

    @Column(name = "OLD_VALUE", columnDefinition = "CLOB")
    private String oldValue;

    @Column(name = "NEW_VALUE", columnDefinition = "CLOB")
    private String newValue;

    @Column(name = "USERNAME")
    private String username;

    @Column(name = "ACTION_DATE")
    private LocalDateTime actionDate;
}
