package com.cdl.ajustement.dto;
import lombok.Data;
import java.util.List;
@Data
public class TableDefinition {
    private String tableName;
    private List<ColumnDefinition> columns;
}
