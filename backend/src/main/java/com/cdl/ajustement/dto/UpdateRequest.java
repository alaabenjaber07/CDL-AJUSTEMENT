package com.cdl.ajustement.dto;

import lombok.Data;
import java.util.Map;

@Data
public class UpdateRequest {
    private Map<String, Object> keys;
    private Map<String, Object> data;
}
