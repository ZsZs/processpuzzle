package com.processpuzzle.baseentity.definition.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;
import java.util.Map;

/**
 * Descriptor for a single entity attribute in form and table rendering.
 * Mirrors libs/js-shared/base-entity-frontend BaseEntityAttrDescriptor.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@lombok.EqualsAndHashCode(callSuper = true)
@lombok.ToString(callSuper = true)
public class AttributeDescriptor extends AbstractAttrDescriptor {
    private String label;
    private String description;
    private String styleClass;
    private String labelClass;
    private String format;
    @lombok.Builder.Default
    private boolean isLinkToDetails = false;
    private List<Selectable> selectables;
    @lombok.Builder.Default
    private Boolean visible = true;
    @lombok.Builder.Default
    private Boolean showThumbnail = true;
    @lombok.Builder.Default
    private Boolean hideInTable = false;
    private Boolean isHeading;
    private String placeholder;
    private Integer lines;
    private Map<String, Object> options;
    @lombok.Builder.Default
    private boolean required = false;
    private String pattern;
    @lombok.Builder.Default
    private String referenceIdField = "id";
    private String linkedEntityType;

    public AttributeDescriptor(String attrName, FormControlType formControlType) {
        super(attrName, formControlType, false, null, null);
        this.visible = true;
        this.showThumbnail = true;
        this.hideInTable = false;
        this.referenceIdField = "id";
    }

    public AttributeDescriptor(String attrName, FormControlType formControlType, String label) {
        this(attrName, formControlType);
        this.label = label;
    }
}
