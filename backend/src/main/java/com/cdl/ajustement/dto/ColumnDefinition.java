package com.cdl.ajustement.dto;
import lombok.Data;
@Data
public class ColumnDefinition {
    private String name;
    private String type;
    private boolean isPrimaryKey;
}
