package com.processpuzzle.baseentity.definition.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Key-value option for selectable controls (dropdown, radio, etc.).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Selectable {
    private String key;
    private Object value;
}
