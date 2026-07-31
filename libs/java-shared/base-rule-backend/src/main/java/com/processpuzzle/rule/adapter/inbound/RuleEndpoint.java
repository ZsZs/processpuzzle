package com.processpuzzle.rule.adapter.inbound;

import com.processpuzzle.core.logging.LogClass;
import com.processpuzzle.rule.api.BaseRuleApi;
import com.processpuzzle.rule.model.*;
import com.processpuzzle.rule.usecase.*;
import com.processpuzzle.shared.model.ImportResult;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;

@RestController
@LogClass
public class RuleEndpoint implements BaseRuleApi {
    private final CreateRule createRule;
    private final UpdateRule updateRule;
    private final DeleteRule deleteRule;
    private final FindRule findRule;
    private final FindAllRules findAllRules;
    private final ImportRules importRules;
    private final ExportRules exportRules;
    private final EvaluateObject evaluateObject;
    private final RuleMapper mapper;

    public RuleEndpoint(CreateRule createRule,
                        UpdateRule updateRule,
                        DeleteRule deleteRule,
                        FindRule findRule,
                        FindAllRules findAllRules,
                        ImportRules importRules,
                        ExportRules exportRules,
                        EvaluateObject evaluateObject,
                        RuleMapper mapper) {
        this.createRule = createRule;
        this.updateRule = updateRule;
        this.deleteRule = deleteRule;
        this.findRule = findRule;
        this.findAllRules = findAllRules;
        this.importRules = importRules;
        this.exportRules = exportRules;
        this.evaluateObject = evaluateObject;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<RuleDefinition> createRule(String orgKey, RuleDefinitionInput input) {
        com.processpuzzle.rule.domain.RuleDefinition created = createRule.execute(orgKey, input);
        return new ResponseEntity<>(mapper.toModel(created), HttpStatus.CREATED);
    }

    @Override
    public ResponseEntity<RuleDefinition> updateRule(String orgKey, String id, RuleDefinitionInput input) {
        com.processpuzzle.rule.domain.RuleDefinition updated = updateRule.execute(orgKey, id, input);
        return ResponseEntity.ok(mapper.toModel(updated));
    }

    @Override
    public ResponseEntity<Void> deleteRule(String orgKey, String id) {
        deleteRule.execute(orgKey, id);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<RuleDefinition> getRule(String orgKey, String id) {
        return ResponseEntity.ok(mapper.toModel(findRule.execute(orgKey, id)));
    }

    @Override
    public ResponseEntity<PageOfRuleDefinition> listRules(String orgKey, String context, String where,
                                                          String order, Integer page, Integer size) {
        return ResponseEntity.ok(mapper.toModel(findAllRules.execute(orgKey, context, where, order, page, size)));
    }

    @Override
    public ResponseEntity<ImportResult> importRules(String orgKey, MultipartFile file) {
        try {
            ImportOutcome outcome = importRules.execute(orgKey, file.getInputStream());
            return ResponseEntity.ok(mapper.toModel(outcome));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public ResponseEntity<EvaluationResult> evaluateObject(String orgKey, EvaluationRequest request) {
        EvaluationOutcome outcome = evaluateObject.execute(orgKey, request.getContext(), request.getEntity());
        return ResponseEntity.ok(mapper.toModel(outcome));
    }

    @Override
    public ResponseEntity<Resource> exportRules(String orgKey, String context) {
        try {
            byte[] yaml = exportRules.execute(orgKey, context);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + orgKey + "-rules-export.yaml\"")
                    .contentType(MediaType.parseMediaType("application/x-yaml"))
                    .body(new ByteArrayResource(yaml));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
