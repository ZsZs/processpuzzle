package com.processpuzzle.state.adapter.inbound;

import com.processpuzzle.shared.model.ImportResult;
import com.processpuzzle.state.api.BaseStateApi;
import com.processpuzzle.state.domain.StateMachineDefinition;
import com.processpuzzle.state.model.EntityObjectStateView;
import com.processpuzzle.state.model.PageOfStateMachineDefinition;
import com.processpuzzle.state.model.StateMachineDefinitionInput;
import com.processpuzzle.state.model.TransitionRequest;
import com.processpuzzle.state.model.TransitionResult;
import com.processpuzzle.state.usecase.CreateStateMachineDefinition;
import com.processpuzzle.state.usecase.DeleteStateMachineDefinition;
import com.processpuzzle.state.usecase.ExportStateMachineDefinitions;
import com.processpuzzle.state.usecase.FindAllStateMachineDefinitions;
import com.processpuzzle.state.usecase.FindStateMachineDefinition;
import com.processpuzzle.state.usecase.FireStateTransition;
import com.processpuzzle.state.usecase.GetEntityObjectState;
import com.processpuzzle.state.usecase.ImportStateMachineDefinitions;
import com.processpuzzle.state.usecase.UpdateStateMachineDefinition;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.UUID;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Thin adapter over the use cases: every method validates nothing and computes nothing itself,
 * it only translates between the generated {@link BaseStateApi} shapes ({@link StateMapper}) and
 * use-case calls — same discipline as {@code RuleEndpoint}/{@code DocumentEndpoint}.
 *
 * <p>{@code orgKey}/path-vs-JWT verification (the 403 case in base-state-api.yaml's
 * {@code OrgKeyParam}) is expected to be enforced by the application's security filter chain
 * ahead of this controller, the same way it is for every other feature module — nothing here
 * re-checks it.
 */
@RestController
public class StateEndpoint implements BaseStateApi {

    private final CreateStateMachineDefinition createStateMachineDefinition;
    private final UpdateStateMachineDefinition updateStateMachineDefinition;
    private final DeleteStateMachineDefinition deleteStateMachineDefinition;
    private final FindStateMachineDefinition findStateMachineDefinition;
    private final FindAllStateMachineDefinitions findAllStateMachineDefinitions;
    private final ImportStateMachineDefinitions importStateMachineDefinitions;
    private final ExportStateMachineDefinitions exportStateMachineDefinitions;
    private final GetEntityObjectState getEntityObjectState;
    private final FireStateTransition fireStateTransition;
    private final StateMapper mapper;

    public StateEndpoint(CreateStateMachineDefinition createStateMachineDefinition,
                         UpdateStateMachineDefinition updateStateMachineDefinition,
                         DeleteStateMachineDefinition deleteStateMachineDefinition,
                         FindStateMachineDefinition findStateMachineDefinition,
                         FindAllStateMachineDefinitions findAllStateMachineDefinitions,
                         ImportStateMachineDefinitions importStateMachineDefinitions,
                         ExportStateMachineDefinitions exportStateMachineDefinitions,
                         GetEntityObjectState getEntityObjectState,
                         FireStateTransition fireStateTransition,
                         StateMapper mapper) {
        this.createStateMachineDefinition = createStateMachineDefinition;
        this.updateStateMachineDefinition = updateStateMachineDefinition;
        this.deleteStateMachineDefinition = deleteStateMachineDefinition;
        this.findStateMachineDefinition = findStateMachineDefinition;
        this.findAllStateMachineDefinitions = findAllStateMachineDefinitions;
        this.importStateMachineDefinitions = importStateMachineDefinitions;
        this.exportStateMachineDefinitions = exportStateMachineDefinitions;
        this.getEntityObjectState = getEntityObjectState;
        this.fireStateTransition = fireStateTransition;
        this.mapper = mapper;
    }

    // ── Knowledge layer ────────────────────────────────────────

    @Override
    public ResponseEntity<PageOfStateMachineDefinition> listStateMachineDefinitions(
            String orgKey, String where, String order, Integer page, Integer size) {
        Page<StateMachineDefinition> result = findAllStateMachineDefinitions.execute(orgKey, where, order, page, size);
        return ResponseEntity.ok(mapper.toModel(result));
    }

    @Override
    public ResponseEntity<com.processpuzzle.state.model.StateMachineDefinition> createStateMachineDefinition(
            String orgKey, StateMachineDefinitionInput input) {
        StateMachineDefinition domain = mapper.toDomain(orgKey, input);
        StateMachineDefinition created = createStateMachineDefinition.execute(domain);
        return ResponseEntity.status(201).body(mapper.toModel(created));
    }

    @Override
    public ResponseEntity<com.processpuzzle.state.model.StateMachineDefinition> getStateMachineDefinition(
            String orgKey, String entityName) {
        StateMachineDefinition definition = findStateMachineDefinition.execute(orgKey, entityName);
        return ResponseEntity.ok(mapper.toModel(definition));
    }

    @Override
    public ResponseEntity<com.processpuzzle.state.model.StateMachineDefinition> updateStateMachineDefinition(
            String orgKey, String entityName, StateMachineDefinitionInput input) {
        StateMachineDefinition domain = mapper.toDomain(orgKey, input);
        StateMachineDefinition updated = updateStateMachineDefinition.execute(orgKey, entityName, domain);
        return ResponseEntity.ok(mapper.toModel(updated));
    }

    @Override
    public ResponseEntity<Void> deleteStateMachineDefinition(String orgKey, String entityName) {
        deleteStateMachineDefinition.execute(orgKey, entityName);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<ImportResult> importStateMachineDefinitions(String orgKey, MultipartFile file) {
        try {
            return ResponseEntity.ok(mapper.toModel(importStateMachineDefinitions.execute(orgKey, file.getInputStream())));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read uploaded state machine import file", e);
        }
    }

    @Override
    public ResponseEntity<Resource> exportStateMachineDefinitions(String orgKey, String entityName) {
        try {
            byte[] yaml = exportStateMachineDefinitions.execute(orgKey, entityName);
            return ResponseEntity.ok(new ByteArrayResource(yaml));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write state machine export file", e);
        }
    }

    // ── Operation layer ────────────────────────────────────────

    @Override
    public ResponseEntity<EntityObjectStateView> getEntityObjectState(String orgKey, String entityName, UUID objectId) {
        return ResponseEntity.ok(mapper.toModel(getEntityObjectState.execute(orgKey, entityName, objectId)));
    }

    @Override
    public ResponseEntity<TransitionResult> fireStateTransition(
            String orgKey, String entityName, UUID objectId, TransitionRequest request) {
        FireStateTransition.Result result = fireStateTransition.execute(
                orgKey, entityName, objectId, request.getTriggerKey(), request.getContext(), request.getVersion());
        return ResponseEntity.ok(mapper.toModel(result));
    }
}
