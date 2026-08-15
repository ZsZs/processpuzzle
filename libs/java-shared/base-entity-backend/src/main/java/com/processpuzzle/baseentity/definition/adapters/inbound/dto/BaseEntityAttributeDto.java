package com.processpuzzle.baseentity.definition.adapters.inbound.dto;

import com.processpuzzle.baseentity.definition.domain.FormControlType;
import com.processpuzzle.baseentity.definition.domain.ValueKind;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BaseEntityAttributeDto {
    private UUID id;
    private String code;
    private String name;
    private int displayOrder;
    private ValueKind valueKind;
    private FormControlType formControlType;
    private boolean isMultiValued;
    private boolean required;
    private boolean indexed;
    private Object defaultValue;
    private List<String> enumValues;
    private String linkedEntityType;
    private boolean isLinkToDetails;
    private Object validationRules;
}
