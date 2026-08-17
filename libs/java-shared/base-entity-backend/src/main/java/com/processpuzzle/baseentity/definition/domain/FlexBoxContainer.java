package com.processpuzzle.baseentity.definition.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * Layout container descriptor for organizing attribute descriptors and nested flexbox
 * descriptors in containers, rows, or columns with optional CSS styles.
 * Mirrors libs/js-shared/base-entity-frontend FlexboxDescriptor.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@lombok.EqualsAndHashCode(callSuper = true)
@lombok.ToString(callSuper = true)
public class FlexBoxContainer extends AbstractAttrDescriptor {
    private FlexDirection direction;
    @lombok.Builder.Default
    private List<AbstractAttrDescriptor> attrDescriptors = new ArrayList<>();

    public FlexBoxContainer(List<AbstractAttrDescriptor> attrDescriptors, FlexDirection direction) {
        super("dummy", FormControlType.FLEX_BOX, false, null, null);
        this.direction = direction;
        this.attrDescriptors = attrDescriptors != null ? new ArrayList<>(attrDescriptors) : new ArrayList<>();
    }

    public void addAttrDescriptor(AbstractAttrDescriptor descriptor) {
        if (this.attrDescriptors == null) {
            this.attrDescriptors = new ArrayList<>();
        }
        this.attrDescriptors.add(descriptor);
    }
}
