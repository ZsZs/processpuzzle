package com.processpuzzle.state.adapter.inbound;

import com.processpuzzle.state.api.BaseStateDiagramsApi;
import com.processpuzzle.state.domain.DiagramDefinition;
import com.processpuzzle.state.model.DiagramDefinitionInput;
import com.processpuzzle.state.model.PageOfDiagramDefinition;
import com.processpuzzle.state.usecase.DeleteDiagramDefinition;
import com.processpuzzle.state.usecase.FindAllDiagramDefinitions;
import com.processpuzzle.state.usecase.FindDiagramDefinition;
import com.processpuzzle.state.usecase.SaveDiagramDefinition;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Thin adapter over the diagram use cases, same discipline as {@link StateEndpoint}: it validates
 * nothing and computes nothing itself, only translating between the generated shapes (via {@link
 * DiagramDefinitionMapper}) and use-case calls.
 *
 * <p>A separate controller from {@link StateEndpoint} because the diagram operations are tagged
 * {@code Base State Diagrams} in the contract and therefore land on their own generated interface.
 * That separation is load-bearing rather than cosmetic: two {@code @RestController}s implementing
 * the same generated interface would register the same request mappings twice and fail the
 * application context at startup.
 *
 * <p>{@code orgKey}/path-vs-JWT verification is the application's security filter chain's job,
 * exactly as for {@link StateEndpoint} — nothing here re-checks it.
 */
@RestController
public class DiagramDefinitionEndpoint implements BaseStateDiagramsApi {

    private final SaveDiagramDefinition saveDiagramDefinition;
    private final FindDiagramDefinition findDiagramDefinition;
    private final FindAllDiagramDefinitions findAllDiagramDefinitions;
    private final DeleteDiagramDefinition deleteDiagramDefinition;
    private final DiagramDefinitionMapper mapper;

    public DiagramDefinitionEndpoint(SaveDiagramDefinition saveDiagramDefinition,
                                     FindDiagramDefinition findDiagramDefinition,
                                     FindAllDiagramDefinitions findAllDiagramDefinitions,
                                     DeleteDiagramDefinition deleteDiagramDefinition,
                                     DiagramDefinitionMapper mapper) {
        this.saveDiagramDefinition = saveDiagramDefinition;
        this.findDiagramDefinition = findDiagramDefinition;
        this.findAllDiagramDefinitions = findAllDiagramDefinitions;
        this.deleteDiagramDefinition = deleteDiagramDefinition;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<PageOfDiagramDefinition> listDiagramDefinitions(
            String orgKey, String where, String order, Integer page, Integer size) {
        Page<DiagramDefinition> result = findAllDiagramDefinitions.execute(orgKey, where, order, page, size);
        return ResponseEntity.ok(mapper.toModel(result));
    }

    @Override
    public ResponseEntity<com.processpuzzle.state.model.DiagramDefinition> getDiagramDefinition(
            String orgKey, String entityName) {
        return ResponseEntity.ok(mapper.toModel(findDiagramDefinition.execute(orgKey, entityName)));
    }

    /** {@code 201} the first time this entityName is arranged, {@code 200} every time after. */
    @Override
    public ResponseEntity<com.processpuzzle.state.model.DiagramDefinition> saveDiagramDefinition(
            String orgKey, String entityName, DiagramDefinitionInput input) {
        SaveDiagramDefinition.Result result =
                saveDiagramDefinition.execute(mapper.toDomain(orgKey, entityName, input));
        return ResponseEntity.status(result.created() ? 201 : 200).body(mapper.toModel(result.definition()));
    }

    @Override
    public ResponseEntity<Void> deleteDiagramDefinition(String orgKey, String entityName) {
        deleteDiagramDefinition.execute(orgKey, entityName);
        return ResponseEntity.noContent().build();
    }
}
