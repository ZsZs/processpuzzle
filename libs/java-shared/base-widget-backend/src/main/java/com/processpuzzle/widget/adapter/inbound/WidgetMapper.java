package com.processpuzzle.widget.adapter.inbound;

import com.processpuzzle.shared.model.AttributeVisibility;
import com.processpuzzle.shared.model.InputPort;
import com.processpuzzle.shared.model.OutputPort;
import com.processpuzzle.shared.model.PortType;
import com.processpuzzle.widget.domain.Port;
import com.processpuzzle.widget.usecase.WidgetDefinitionCrud.WidgetDefinitionDraft;
import org.springframework.stereotype.Component;

import java.time.ZoneOffset;
import java.util.List;

/**
 * Converts between the generated contract models and the domain.
 *
 * <p>The one asymmetry worth knowing: the contract has separate {@code InputPort} and
 * {@code OutputPort} schemas, while the domain has a single {@link Port} record with the
 * input-only fields left null on an output. This class is where that distinction is re-established;
 * see {@link Port} for why the domain does not carry two records.
 */
@Component
public class WidgetMapper {

    public WidgetDefinitionDraft toDraft(com.processpuzzle.widget.model.WidgetDefinitionInput input) {
        return new WidgetDefinitionDraft(
                input.getKey(),
                input.getName(),
                input.getTranslocoId(),
                input.getDescription(),
                input.getCategory(),
                input.getIcon(),
                input.getPropsSchema(),
                toDomainInputPorts(input.getInputPorts()),
                toDomainOutputPorts(input.getOutputPorts()));
    }

    public com.processpuzzle.widget.model.WidgetDefinition toModel(com.processpuzzle.widget.domain.WidgetDefinition definition) {
        com.processpuzzle.widget.model.WidgetDefinition model = new com.processpuzzle.widget.model.WidgetDefinition();
        model.setKey(definition.getKey());
        model.setName(definition.getName());
        model.setTranslocoId(definition.getTranslocoId());
        model.setDescription(definition.getDescription());
        model.setCategory(definition.getCategory());
        model.setIcon(definition.getIcon());
        model.setPropsSchema(definition.getPropsSchema());
        model.setInputPorts(toModelInputPorts(definition.getInputPorts()));
        model.setOutputPorts(toModelOutputPorts(definition.getOutputPorts()));
        model.setOrgKey(definition.getOrgKey());
        model.setStatus(com.processpuzzle.widget.model.WidgetDefinitionStatus.fromValue(definition.status().name()));
        model.setVersion(definition.getVersion());
        model.setPublishedVersion(definition.getPublishedVersion());
        model.setCreatedAt(definition.getCreatedAt() == null ? null : definition.getCreatedAt().atOffset(ZoneOffset.UTC));
        model.setUpdatedAt(definition.getUpdatedAt() == null ? null : definition.getUpdatedAt().atOffset(ZoneOffset.UTC));
        return model;
    }

    private List<Port> toDomainInputPorts(List<InputPort> ports) {
        if (ports == null) {
            return null;
        }
        return ports.stream()
                .map(port -> new Port(
                        port.getName(),
                        toDomainPortType(port.getType()),
                        port.getRequired(),
                        port.getDescription(),
                        port.getDefaultValue(),
                        port.getEntityType(),
                        toDomainVisibility(port.getAttributeVisibility()),
                        port.getDefaultRsqlFilter()))
                .toList();
    }

    private List<Port> toDomainOutputPorts(List<OutputPort> ports) {
        if (ports == null) {
            return null;
        }
        return ports.stream()
                .map(port -> new Port(
                        port.getName(),
                        toDomainPortType(port.getType()),
                        null,
                        port.getDescription(),
                        null,
                        port.getEntityType(),
                        toDomainVisibility(port.getAttributeVisibility()),
                        null))
                .toList();
    }

    private List<InputPort> toModelInputPorts(List<Port> ports) {
        if (ports == null) {
            return null;
        }
        return ports.stream().map(port -> {
            InputPort model = new InputPort(port.name(), toModelPortType(port.type()));
            model.setRequired(port.required());
            model.setDescription(port.description());
            model.setDefaultValue(port.defaultValue());
            model.setEntityType(port.entityType());
            model.setAttributeVisibility(toModelVisibility(port.attributeVisibility()));
            model.setDefaultRsqlFilter(port.defaultRsqlFilter());
            return model;
        }).toList();
    }

    private List<OutputPort> toModelOutputPorts(List<Port> ports) {
        if (ports == null) {
            return null;
        }
        return ports.stream().map(port -> {
            OutputPort model = new OutputPort(port.name(), toModelPortType(port.type()));
            model.setDescription(port.description());
            model.setEntityType(port.entityType());
            model.setAttributeVisibility(toModelVisibility(port.attributeVisibility()));
            return model;
        }).toList();
    }

    private Port.PortType toDomainPortType(PortType type) {
        return type == null ? Port.PortType.STRING : Port.PortType.valueOf(type.name());
    }

    private PortType toModelPortType(Port.PortType type) {
        return type == null ? PortType.STRING : PortType.fromValue(type.name());
    }

    private Port.AttributeVisibility toDomainVisibility(AttributeVisibility visibility) {
        if (visibility == null) {
            return null;
        }
        Port.AttributeVisibility.Mode mode = visibility.getMode() == null
                ? Port.AttributeVisibility.Mode.ALL
                : Port.AttributeVisibility.Mode.valueOf(visibility.getMode().name());
        return new Port.AttributeVisibility(mode, visibility.getAttributes());
    }

    private AttributeVisibility toModelVisibility(Port.AttributeVisibility visibility) {
        if (visibility == null) {
            return null;
        }
        AttributeVisibility model = new AttributeVisibility();
        model.setMode(visibility.mode() == null
                ? AttributeVisibility.ModeEnum.ALL
                : AttributeVisibility.ModeEnum.fromValue(visibility.mode().name()));
        model.setAttributes(visibility.attributes());
        return model;
    }
}
