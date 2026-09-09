package com.processpuzzle.baseentity.definition.domain;

import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;

/**
 * Alias of {@link FlexBoxContainer} mirroring OpenAPI FlexBoxDescriptor and frontend FlexboxDescriptor.
 */
@SuperBuilder
@NoArgsConstructor
public class FlexBoxDescriptor extends FlexBoxContainer {

    public FlexBoxDescriptor(List<AbstractAttrDescriptor> attrDescriptors, FlexDirection direction) {
        super(attrDescriptors, direction);
    }
}
