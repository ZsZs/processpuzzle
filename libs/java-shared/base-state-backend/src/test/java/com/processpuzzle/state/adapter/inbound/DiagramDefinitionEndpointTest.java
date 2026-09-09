package com.processpuzzle.state.adapter.inbound;

import com.processpuzzle.state.domain.DiagramDefinition;
import com.processpuzzle.state.model.DiagramDefinitionInput;
import com.processpuzzle.state.model.PageOfDiagramDefinition;
import com.processpuzzle.state.usecase.DeleteDiagramDefinition;
import com.processpuzzle.state.usecase.FindAllDiagramDefinitions;
import com.processpuzzle.state.usecase.FindDiagramDefinition;
import com.processpuzzle.state.usecase.SaveDiagramDefinition;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DiagramDefinitionEndpointTest {

    private static final String ORG = "org-1";
    private static final String ENTITY = "invoice";

    private SaveDiagramDefinition saveUseCase;
    private FindDiagramDefinition findUseCase;
    private FindAllDiagramDefinitions findAllUseCase;
    private DeleteDiagramDefinition deleteUseCase;
    private DiagramDefinitionEndpoint endpoint;

    @BeforeEach
    void setUp() {
        saveUseCase = mock(SaveDiagramDefinition.class);
        findUseCase = mock(FindDiagramDefinition.class);
        findAllUseCase = mock(FindAllDiagramDefinitions.class);
        deleteUseCase = mock(DeleteDiagramDefinition.class);
        endpoint = new DiagramDefinitionEndpoint(
                saveUseCase, findUseCase, findAllUseCase, deleteUseCase, new DiagramDefinitionMapper());
    }

    private static DiagramDefinition domainLayout() {
        return DiagramDefinition.builder().orgKey(ORG).entityName(ENTITY).build();
    }

    @Test
    void list_shouldReturnThePage() {
        when(findAllUseCase.execute(ORG, "entityName==invoice", "entityName,asc", 1, 5))
                .thenReturn(new PageImpl<>(List.of(domainLayout()), PageRequest.of(1, 5), 6));

        ResponseEntity<PageOfDiagramDefinition> response =
                endpoint.listDiagramDefinitions(ORG, "entityName==invoice", "entityName,asc", 1, 5);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getContent()).hasSize(1);
    }

    @Test
    void get_shouldReturnTheLayout() {
        when(findUseCase.execute(ORG, ENTITY)).thenReturn(domainLayout());

        ResponseEntity<com.processpuzzle.state.model.DiagramDefinition> response =
                endpoint.getDiagramDefinition(ORG, ENTITY);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getEntityName()).isEqualTo(ENTITY);
    }

    @Test
    void save_shouldAnswer201OnTheFirstArrangement() {
        when(saveUseCase.execute(any())).thenReturn(new SaveDiagramDefinition.Result(domainLayout(), true));

        ResponseEntity<com.processpuzzle.state.model.DiagramDefinition> response =
                endpoint.saveDiagramDefinition(ORG, ENTITY, new DiagramDefinitionInput(ENTITY));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
    }

    @Test
    void save_shouldAnswer200OnEveryLaterArrangement() {
        when(saveUseCase.execute(any())).thenReturn(new SaveDiagramDefinition.Result(domainLayout(), false));

        ResponseEntity<com.processpuzzle.state.model.DiagramDefinition> response =
                endpoint.saveDiagramDefinition(ORG, ENTITY, new DiagramDefinitionInput(ENTITY));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
    }

    /** The path is authoritative even when the body disagrees. */
    @Test
    void save_shouldPassThePathEntityNameToTheUseCase() {
        when(saveUseCase.execute(any())).thenReturn(new SaveDiagramDefinition.Result(domainLayout(), true));

        endpoint.saveDiagramDefinition(ORG, ENTITY, new DiagramDefinitionInput("some-other-entity"));

        verify(saveUseCase).execute(org.mockito.ArgumentMatchers.argThat(
                d -> ORG.equals(d.getOrgKey()) && ENTITY.equals(d.getEntityName())));
    }

    @Test
    void delete_shouldAnswer204() {
        ResponseEntity<Void> response = endpoint.deleteDiagramDefinition(ORG, ENTITY);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(deleteUseCase).execute(eq(ORG), eq(ENTITY));
    }
}
