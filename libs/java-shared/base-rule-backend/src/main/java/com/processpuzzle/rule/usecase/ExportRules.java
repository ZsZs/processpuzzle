package com.processpuzzle.rule.usecase;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.processpuzzle.rule.adapter.inbound.dto.RuleYamlDocument;
import com.processpuzzle.rule.adapter.inbound.dto.RuleYamlEntry;
import com.processpuzzle.rule.domain.RuleDefinition;
import com.processpuzzle.rule.domain.RuleDefinitionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class ExportRules {

    private final RuleDefinitionRepository repository;
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory())
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    public ExportRules(RuleDefinitionRepository repository) {
        this.repository = repository;
    }

    public byte[] execute(String context) throws IOException {
        List<RuleDefinition> rules = context != null
                ? repository.findByContext(context)
                : repository.findAll();

        List<RuleYamlEntry> entries = rules.stream()
                .map(r -> new RuleYamlEntry(
                        r.getId(), r.getName(), r.getDescription(), r.getContext(),
                        r.getExpression(), r.getSeverity().name(),
                        r.getMessage(), r.getTranslocoId(), r.getExtendsRuleId(),
                        r.isOverride() ? Boolean.TRUE : null,
                        r.isEnabled() ? null : Boolean.FALSE,
                        r.getFields().isEmpty() ? null : r.getFields()))
                .toList();

        return yamlMapper.writeValueAsBytes(new RuleYamlDocument(entries));
    }
}
