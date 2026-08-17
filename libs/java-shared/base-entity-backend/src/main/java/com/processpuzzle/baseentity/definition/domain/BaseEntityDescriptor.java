package com.processpuzzle.baseentity.definition.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Descriptor bundling an entity's name and its layout of attribute / flexbox descriptors.
 * Mirrors libs/js-shared/base-entity-frontend BaseEntityDescriptor.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BaseEntityDescriptor {
    private String entityName;
    @Builder.Default
    private List<AbstractAttrDescriptor> attrDescriptors = new ArrayList<>();

    public void addAttrDescriptor(AbstractAttrDescriptor descriptor) {
        if (this.attrDescriptors == null) {
            this.attrDescriptors = new ArrayList<>();
        }
        this.attrDescriptors.add(descriptor);
    }
}
