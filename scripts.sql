-- Script SQL d'initialisation pour l'application Ajustement CDL

-- Table d'Audit Centrale
CREATE TABLE CDL_AUDIT_LOG (
    ID NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    TABLE_NAME VARCHAR2(100) NOT NULL,
    ACTION_TYPE VARCHAR2(20) NOT NULL,
    OLD_VALUE CLOB,
    NEW_VALUE CLOB,
    USERNAME VARCHAR2(100),
    ACTION_DATE TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Les autres tables cdl_ajustement, cdl_fne, cdl_fng, etc. sont supposées existantes.
-- Ce script peut être exécuté par un DBA et n'est pas strictement requis car Spring Boot (Hibernate) crééra CDL_AUDIT_LOG automatiquement via ddl-auto=update.
