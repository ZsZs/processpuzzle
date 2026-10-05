package com.processpuzzle.baseentity.definition.usecases.inbound;

import com.processpuzzle.baseentity.definition.adapters.inbound.EntityDefinitionMapper;
import com.processpuzzle.baseentity.definition.domain.BaseEntityDefinition;
import com.processpuzzle.baseentity.definition.domain.EntityDefinitionRepository;
import com.processpuzzle.baseentity.definition.domain.EntityDefinitionValidator;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The import is all or nothing: every problem in the file is collected before anything is written, and
 * a single problem means neither the create nor the replace use case is ever called. The validator and
 * mapper are the real ones, so a structural rule the REST endpoint enforces is enforced here too.
 */
@ExtendWith(MockitoExtension.class)
class ImportEntityDefinitionsTest {

    private static final String ORG = "acme";

    @Mock
    private EntityDefinitionRepository repository;

    @Mock
    private CreateEntityDefinitionUseCase createUseCase;

    @Mock
    private ReplaceEntityDefinitionUseCase replaceUseCase;

    private ImportEntityDefinitions importer;

    @BeforeEach
    void setUp() {
        importer = new ImportEntityDefinitions(repository, new EntityDefinitionValidator(),
                new EntityDefinitionMapper(), createUseCase, replaceUseCase);
        lenient().when(repository.existsByOrgKeyAndCode(anyString(), anyString())).thenReturn(false);
    }

    @Test
    void newDefinitionsAreCreatedAndExistingOnesReplaced() throws IOException {
        when(repository.existsByOrgKeyAndCode(ORG, "partner")).thenReturn(true);

        ImportEntityDefinitions.Outcome outcome = importer.execute(ORG, yaml("""
                entityDefinitions:
                  - code: partner
                    name: Partner
                  - code: invoice
                    name: Invoice
                """));

        assertThat(outcome.errors()).isEmpty();
        assertThat(outcome.created()).containsExactly("invoice");
        assertThat(outcome.updated()).containsExactly("partner");
        ArgumentCaptor<BaseEntityDefinition> replaced = ArgumentCaptor.forClass(BaseEntityDefinition.class);
        verify(replaceUseCase).replace(eq(ORG), eq("partner"), replaced.capture());
        assertThat(replaced.getValue().getName()).isEqualTo("Partner");
        ArgumentCaptor<BaseEntityDefinition> created = ArgumentCaptor.forClass(BaseEntityDefinition.class);
        verify(createUseCase).create(eq(ORG), created.capture());
        assertThat(created.getValue().getCode()).isEqualTo("invoice");
    }

    @Test
    void anEntryWithoutCodeRejectsTheFile() throws IOException {
        ImportEntityDefinitions.Outcome outcome = importer.execute(ORG, yaml("""
                entityDefinitions:
                  - code: partner
                    name: Partner
                  - name: Nameless
                  - code: "  "
                    name: Blank
                """));

        assertThat(outcome.errors()).containsExactly(
                "An entity definition entry is missing 'code'.",
                "An entity definition entry is missing 'code'.");
        assertNothingWritten(outcome);
    }

    @Test
    void aDuplicateCodeRejectsTheFile() throws IOException {
        ImportEntityDefinitions.Outcome outcome = importer.execute(ORG, yaml("""
                entityDefinitions:
                  - code: partner
                    name: Partner
                  - code: partner
                    name: Partner again
                """));

        assertThat(outcome.errors())
                .containsExactly("Duplicate entity definition code within the import file: 'partner'.");
        assertNothingWritten(outcome);
    }

    @Test
    void anUnknownComponentParentRejectsTheFile() throws IOException {
        ImportEntityDefinitions.Outcome outcome = importer.execute(ORG, yaml("""
                entityDefinitions:
                  - code: address
                    name: Address
                    isEmbedded: true
                    componentParents: [ customer ]
                """));

        assertThat(outcome.errors()).containsExactly("'address' names unknown componentParent 'customer'.");
        assertNothingWritten(outcome);
    }

    @Test
    void aComponentParentMayArriveInTheSameFile() throws IOException {
        ImportEntityDefinitions.Outcome outcome = importer.execute(ORG, yaml("""
                entityDefinitions:
                  - code: address
                    name: Address
                    isEmbedded: true
                    componentParents: [ customer ]
                  - code: customer
                    name: Customer
                """));

        assertThat(outcome.errors()).isEmpty();
        assertThat(outcome.created()).containsExactly("address", "customer");
    }

    @Test
    void aComponentParentMayAlreadyExistInTheOrganization() throws IOException {
        when(repository.existsByOrgKeyAndCode(ORG, "customer")).thenReturn(true);

        ImportEntityDefinitions.Outcome outcome = importer.execute(ORG, yaml("""
                entityDefinitions:
                  - code: address
                    name: Address
                    isEmbedded: true
                    componentParents: [ customer ]
                """));

        assertThat(outcome.errors()).isEmpty();
        assertThat(outcome.created()).containsExactly("address");
    }

    @Test
    void aComponentParentInAnotherOrganizationDoesNotCount() throws IOException {
        lenient().when(repository.existsByOrgKeyAndCode("globex", "customer")).thenReturn(true);

        ImportEntityDefinitions.Outcome outcome = importer.execute(ORG, yaml("""
                entityDefinitions:
                  - code: address
                    name: Address
                    isEmbedded: true
                    componentParents: [ customer ]
                """));

        assertThat(outcome.errors()).containsExactly("'address' names unknown componentParent 'customer'.");
        assertNothingWritten(outcome);
    }

    @Test
    void aStructuralViolationRejectsTheWholeFile() throws IOException {
        ImportEntityDefinitions.Outcome outcome = importer.execute(ORG, yaml("""
                entityDefinitions:
                  - code: partner
                    name: Partner
                  - code: order
                    name: Order
                    attributes:
                      - code: priority
                        name: Priority
                        valueKind: ENUM
                        formControlType: ENUM_SELECT
                """));

        assertThat(outcome.errors())
                .containsExactly("'order': priority: valueKind=ENUM requires at least one enumValues entry");
        assertNothingWritten(outcome);
    }

    @Test
    void aDefinitionLevelViolationIsReportedWithoutAnAttributeCode() throws IOException {
        // isEmbedded without a componentParent is the validator's rule, not the parent lookup's.
        ImportEntityDefinitions.Outcome outcome = importer.execute(ORG, yaml("""
                entityDefinitions:
                  - code: address
                    name: Address
                    isEmbedded: true
                """));

        assertThat(outcome.errors()).singleElement().asString()
                .startsWith("'address': 'address' declares isEmbedded without a componentParent");
        assertNothingWritten(outcome);
    }

    @Test
    void everyProblemInTheFileIsReported() throws IOException {
        ImportEntityDefinitions.Outcome outcome = importer.execute(ORG, yaml("""
                entityDefinitions:
                  - name: Nameless
                  - code: address
                    name: Address
                    isEmbedded: true
                    componentParents: [ ghost ]
                  - code: order
                    name: Order
                    attributes:
                      - code: priority
                        valueKind: ENUM
                """));

        assertThat(outcome.errors()).hasSize(3);
        assertNothingWritten(outcome);
    }

    @Test
    void anEmptyDefinitionListWritesNothing() throws IOException {
        ImportEntityDefinitions.Outcome outcome = importer.execute(ORG, yaml("entityDefinitions: []\n"));

        assertThat(outcome.errors()).isEmpty();
        assertNothingWritten(outcome);
    }

    @Test
    void anAbsentDefinitionListWritesNothing() throws IOException {
        ImportEntityDefinitions.Outcome outcome = importer.execute(ORG, yaml("""
                entities:
                  - entityDefinitionCode: partner
                """));

        assertThat(outcome.errors()).isEmpty();
        assertNothingWritten(outcome);
    }

    @Test
    void aNullDocumentWritesNothing() throws IOException {
        ImportEntityDefinitions.Outcome outcome = importer.execute(ORG, yaml("~\n"));

        assertThat(outcome.errors()).isEmpty();
        assertNothingWritten(outcome);
    }

    private void assertNothingWritten(ImportEntityDefinitions.Outcome outcome) {
        assertThat(outcome.created()).isEmpty();
        assertThat(outcome.updated()).isEmpty();
        verifyNoInteractions(createUseCase, replaceUseCase);
    }

    private static InputStream yaml(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }
}
