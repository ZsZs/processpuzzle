package com.processpuzzle.calculation.adapter.inbound;

import com.processpuzzle.calculation.api.CalculationDefinitionsApi;
import com.processpuzzle.calculation.model.CalculationDefinition;
import com.processpuzzle.calculation.model.CalculationDefinitionInput;
import com.processpuzzle.calculation.model.CalculationSource;
import com.processpuzzle.calculation.model.ReleaseState;
import com.processpuzzle.calculation.model.ValidationReport;
import com.processpuzzle.calculation.usecase.CalculationValidator;
import com.processpuzzle.calculation.usecase.CreateCalculationDefinition;
import com.processpuzzle.calculation.usecase.DeleteCalculationDefinition;
import com.processpuzzle.calculation.usecase.FindCalculationDefinition;
import com.processpuzzle.calculation.usecase.ImportCalculationDefinitions;
import com.processpuzzle.calculation.usecase.ReplaceCalculationDefinition;
import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.shared.model.ImportResult;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Implements the generated {@code CalculationDefinitionsApi}. */
@RestController
public class CalculationDefinitionsEndpoint implements CalculationDefinitionsApi {

    private final CreateCalculationDefinition create;
    private final ReplaceCalculationDefinition replace;
    private final DeleteCalculationDefinition delete;
    private final FindCalculationDefinition find;
    private final ImportCalculationDefinitions importer;
    private final CalculationValidator validator;
    private final CalculationMapper mapper;
    private final CalculationReleaseParticipant releaseParticipant;

    public CalculationDefinitionsEndpoint(CreateCalculationDefinition create, ReplaceCalculationDefinition replace,
                                          DeleteCalculationDefinition delete, FindCalculationDefinition find,
                                          ImportCalculationDefinitions importer, CalculationValidator validator,
                                          CalculationMapper mapper, CalculationReleaseParticipant releaseParticipant) {
        this.create = create;
        this.replace = replace;
        this.delete = delete;
        this.find = find;
        this.importer = importer;
        this.validator = validator;
        this.mapper = mapper;
        this.releaseParticipant = releaseParticipant;
    }

    @Override
    public ResponseEntity<List<CalculationDefinition>> listCalculationDefinitions(String orgKey, String revision) {
        Channel channel = Channel.ofRevision(revision);
        List<CalculationDefinition> definitions = find.findAll(orgKey, channel).stream().map(mapper::toModel).toList();
        if (channel == Channel.DRAFT) {
            Map<String, com.processpuzzle.core.definition.ReleaseState> states =
                    com.processpuzzle.core.definition.ReleaseState.ofDrafts(releaseParticipant, orgKey);
            definitions.forEach(definition -> definition.setReleaseState(releaseStateOf(states, definition.getId())));
        }
        return ResponseEntity.ok(definitions);
    }

    @Override
    public ResponseEntity<CalculationDefinition> createCalculationDefinition(String orgKey, CalculationDefinitionInput input) {
        return new ResponseEntity<>(mapper.toModel(create.create(orgKey, input.getSource())), HttpStatus.CREATED);
    }

    @Override
    public ResponseEntity<CalculationDefinition> getCalculationDefinition(String orgKey, String calculationId, String revision) {
        Channel channel = Channel.ofRevision(revision);
        CalculationDefinition definition = mapper.toModel(find.find(orgKey, calculationId, channel));
        if (channel == Channel.DRAFT) {
            definition.setReleaseState(releaseStateOf(
                    com.processpuzzle.core.definition.ReleaseState.ofDrafts(releaseParticipant, orgKey), calculationId));
        }
        return ResponseEntity.ok(definition);
    }

    @Override
    public ResponseEntity<CalculationDefinition> updateCalculationDefinition(String orgKey, String calculationId,
                                                                             CalculationDefinitionInput input) {
        return ResponseEntity.ok(mapper.toModel(replace.replace(orgKey, calculationId, input.getSource(), input.getVersion())));
    }

    @Override
    public ResponseEntity<Void> deleteCalculationDefinition(String orgKey, String calculationId) {
        delete.delete(orgKey, calculationId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<ValidationReport> validateCalculationDefinition(String orgKey, CalculationSource source) {
        return ResponseEntity.ok(mapper.toReport(validator.validate(source.getSource())));
    }

    @Override
    public ResponseEntity<ImportResult> importCalculationDefinitions(String orgKey, MultipartFile file) {
        try (InputStream input = file.getInputStream()) {
            return ResponseEntity.ok(mapper.toImportResult(importer.execute(orgKey, new String(input.readAllBytes(), StandardCharsets.UTF_8))));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** A draft's release state, as the designer's list and form show it; only drafts have one. */
    private static ReleaseState releaseStateOf(Map<String, com.processpuzzle.core.definition.ReleaseState> states, String id) {
        com.processpuzzle.core.definition.ReleaseState state = states.get(id);
        return state == null ? null : ReleaseState.fromValue(state.name());
    }
}
