package com.processpuzzle.baseentity.definition.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.Map;

/**
 * Base abstract descriptor for entity form attributes and layout items.
 * Mirrors libs/js-shared/base-entity-frontend AbstractAttrDescriptor.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public abstract class AbstractAttrDescriptor {
    private String attrName;
    private FormControlType formControlType;
    @Builder.Default
    private boolean disabled = false;
    private Map<String, Object> style;
    private String labelKey;
}
