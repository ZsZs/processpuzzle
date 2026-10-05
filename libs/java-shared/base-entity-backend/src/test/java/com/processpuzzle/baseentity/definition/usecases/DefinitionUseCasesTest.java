package com.processpuzzle.baseentity.definition.usecases;

import com.processpuzzle.baseentity.common.ConflictException;
import com.processpuzzle.baseentity.common.NotFoundException;
import com.processpuzzle.baseentity.definition.domain.BaseEntityAttribute;
import com.processpuzzle.baseentity.definition.domain.BaseEntityDefinition;
import com.processpuzzle.baseentity.definition.domain.EntityDefinitionRepository;
import com.processpuzzle.baseentity.definition.domain.EntityDefinitionStatus;
import com.processpuzzle.baseentity.definition.domain.EntityDefinitionValidator;
import com.processpuzzle.baseentity.definition.domain.FormControlType;
import com.processpuzzle.baseentity.definition.domain.ValueKind;
import com.processpuzzle.baseentity.definition.usecases.inbound.AddAttributeUseCase;
import com.processpuzzle.baseentity.definition.usecases.inbound.CreateEntityDefinitionUseCase;
import com.processpuzzle.baseentity.definition.usecases.inbound.DeleteAttributeUseCase;
import com.processpuzzle.baseentity.definition.usecases.inbound.DeleteEntityDefinitionUseCase;
import com.processpuzzle.baseentity.definition.usecases.inbound.FindAllEntityDefinitionsUseCase;
import com.processpuzzle.baseentity.definition.usecases.inbound.FindEntityDefinitionByCodeUseCase;
import com.processpuzzle.baseentity.definition.usecases.inbound.ReplaceAttributeUseCase;
import com.processpuzzle.baseentity.definition.usecases.inbound.ReplaceEntityDefinitionUseCase;
import com.processpuzzle.baseentity.definition.usecases.outbound.EntityInstanceExistenceCheckPort;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefinitionUseCasesTest {

    private static final String ORG = "acme";

    @Mock
    private EntityDefinitionRepository repository;

    @Mock
    private EntityDefinitionValidator validator;

    @Mock
    private EntityInstanceExistenceCheckPort existenceCheckPort;

    private CreateEntityDefinitionUseCase createUseCase;
    private ReplaceEntityDefinitionUseCase replaceUseCase;
    private DeleteEntityDefinitionUseCase deleteUseCase;
    private FindAllEntityDefinitionsUseCase findAllUseCase;
    private FindEntityDefinitionByCodeUseCase findByCodeUseCase;
    private AddAttributeUseCase addAttributeUseCase;
    private ReplaceAttributeUseCase replaceAttributeUseCase;
    private DeleteAttributeUseCase deleteAttributeUseCase;

    @BeforeEach
    void setUp() {
        createUseCase = new CreateEntityDefinitionUseCase(repository, validator);
        replaceUseCase = new ReplaceEntityDefinitionUseCase(repository, validator);
        deleteUseCase = new DeleteEntityDefinitionUseCase(repository, existenceCheckPort);
        findAllUseCase = new FindAllEntityDefinitionsUseCase(repository);
        findByCodeUseCase = new FindEntityDefinitionByCodeUseCase(repository);
        addAttributeUseCase = new AddAttributeUseCase(repository, validator);
        replaceAttributeUseCase = new ReplaceAttributeUseCase(repository, validator);
        deleteAttributeUseCase = new DeleteAttributeUseCase(repository);
    }

    @Test
    void createEntityDefinition_success() {
        BaseEntityDefinition input = BaseEntityDefinition.builder().code("partner").name("Partner").build();
        when(repository.existsByOrgKeyAndCode(ORG, "partner")).thenReturn(false);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        BaseEntityDefinition result = createUseCase.create(ORG, input);

        assertThat(result.getCode()).isEqualTo("partner");
        verify(validator).validate(input);
        verify(repository).save(input);
    }

    @Test
    void createEntityDefinition_setsOrgKeyOnTheDefinition() {
        BaseEntityDefinition input = BaseEntityDefinition.builder().code("partner").orgKey("someone-else").build();
        when(repository.existsByOrgKeyAndCode(ORG, "partner")).thenReturn(false);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        BaseEntityDefinition result = createUseCase.create(ORG, input);

        assertThat(result.getOrgKey()).isEqualTo(ORG);
    }

    @Test
    void createEntityDefinition_sameCodeInAnotherOrg_doesNotConflict() {
        BaseEntityDefinition input = BaseEntityDefinition.builder().code("partner").build();
        when(repository.existsByOrgKeyAndCode("globex", "partner")).thenReturn(false);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        BaseEntityDefinition result = createUseCase.create("globex", input);

        assertThat(result.getOrgKey()).isEqualTo("globex");
        verify(repository).existsByOrgKeyAndCode("globex", "partner");
        verify(repository, never()).existsByOrgKeyAndCode(ORG, "partner");
    }

    @Test
    void createEntityDefinition_alreadyExists_throwsConflict() {
        BaseEntityDefinition input = BaseEntityDefinition.builder().code("partner").build();
        when(repository.existsByOrgKeyAndCode(ORG, "partner")).thenReturn(true);

        assertThatThrownBy(() -> createUseCase.create(ORG, input))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void replaceEntityDefinition_success() {
        BaseEntityAttribute existingAttribute = BaseEntityAttribute.builder().code("email").name("Old email").build();
        BaseEntityDefinition existing = BaseEntityDefinition.builder()
            .id(UUID.randomUUID())
            .code("partner")
            .name("Old")
            .attributes(new ArrayList<>(List.of(existingAttribute)))
            .build();
        BaseEntityAttribute updatedAttribute = BaseEntityAttribute.builder().code("email").name("New email").build();
        BaseEntityDefinition update = BaseEntityDefinition.builder()
            .code("partner")
            .name("New")
            .attributes(List.of(updatedAttribute))
            .build();

        when(repository.findByOrgKeyAndCode(ORG, "partner")).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        BaseEntityDefinition result = replaceUseCase.replace(ORG, "partner", update);

        assertThat(result.getName()).isEqualTo("New");
        assertThat(result.getAttributes()).containsExactly(existingAttribute);
        assertThat(existingAttribute.getName()).isEqualTo("New email");
        verify(validator).validate(existing);
        verify(repository).save(existing);
    }

    @Test
    void replaceEntityDefinition_codeMismatch_throwsConflict() {
        BaseEntityDefinition existing = BaseEntityDefinition.builder().id(UUID.randomUUID()).code("partner").name("Old").build();
        BaseEntityDefinition update = BaseEntityDefinition.builder().code("different").name("New").build();

        when(repository.findByOrgKeyAndCode(ORG, "partner")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> replaceUseCase.replace(ORG, "partner", update))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void deleteEntityDefinition_success() {
        BaseEntityDefinition existing = BaseEntityDefinition.builder().code("partner").build();
        when(repository.findByOrgKeyAndCode(ORG, "partner")).thenReturn(Optional.of(existing));
        when(existenceCheckPort.existsAnyInstanceOf(ORG, "partner")).thenReturn(false);
        when(repository.findAllByOrgKey(ORG)).thenReturn(List.of(existing));

        deleteUseCase.delete(ORG, "partner");

        verify(repository).delete(existing);
    }

    @Test
    void deleteEntityDefinition_instancesExist_throwsConflict() {
        BaseEntityDefinition existing = BaseEntityDefinition.builder().code("partner").build();
        when(repository.findByOrgKeyAndCode(ORG, "partner")).thenReturn(Optional.of(existing));
        when(existenceCheckPort.existsAnyInstanceOf(ORG, "partner")).thenReturn(true);

        assertThatThrownBy(() -> deleteUseCase.delete(ORG, "partner"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void addAttribute_success() {
        BaseEntityDefinition existing = BaseEntityDefinition.builder()
                .code("partner")
                .attributes(new ArrayList<>())
                .build();
        BaseEntityAttribute attribute = BaseEntityAttribute.builder().code("email").name("Email").build();

        when(repository.findByOrgKeyAndCode(ORG, "partner")).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        BaseEntityAttribute result = addAttributeUseCase.addAttribute(ORG, "partner", attribute);

        assertThat(result.getCode()).isEqualTo("email");
        assertThat(existing.getAttributes()).contains(attribute);
        verify(validator).validate(existing);
    }

    @Test
    void addAttribute_alreadyExists_throwsConflict() {
        BaseEntityAttribute existingAttr = BaseEntityAttribute.builder().code("email").build();
        BaseEntityDefinition existing = BaseEntityDefinition.builder()
                .code("partner")
                .attributes(new ArrayList<>(List.of(existingAttr)))
                .build();
        BaseEntityAttribute duplicate = BaseEntityAttribute.builder().code("email").build();

        when(repository.findByOrgKeyAndCode(ORG, "partner")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> addAttributeUseCase.addAttribute(ORG, "partner", duplicate))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void replaceAttribute_success() {
        BaseEntityAttribute existingAttr = BaseEntityAttribute.builder().code("email").name("Old").build();
        BaseEntityDefinition existing = BaseEntityDefinition.builder()
                .code("partner")
                .attributes(new ArrayList<>(List.of(existingAttr)))
                .build();
        BaseEntityAttribute replacement = BaseEntityAttribute.builder().code("email").name("New").valueKind(ValueKind.TEXT).formControlType(FormControlType.TEXT).build();

        when(repository.findByOrgKeyAndCode(ORG, "partner")).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        BaseEntityAttribute result = replaceAttributeUseCase.replaceAttribute(ORG, "partner", "email", replacement);

        assertThat(result.getName()).isEqualTo("New");
        verify(validator).validate(existing);
    }

    @Test
    void deleteAttribute_success() {
        BaseEntityAttribute existingAttr = BaseEntityAttribute.builder().code("email").build();
        BaseEntityDefinition existing = BaseEntityDefinition.builder()
                .code("partner")
                .attributes(new ArrayList<>(List.of(existingAttr)))
                .build();

        when(repository.findByOrgKeyAndCode(ORG, "partner")).thenReturn(Optional.of(existing));

        deleteAttributeUseCase.deleteAttribute(ORG, "partner", "email");

        assertThat(existing.getAttributes()).isEmpty();
        verify(repository).save(existing);
    }

    @Test
    void findAll_withNullFilters_queriesSuccessfully() {
        Pageable pageable = PageRequest.of(0, 20);
        BaseEntityDefinition def = BaseEntityDefinition.builder().code("partner").build();
        when(repository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(def)));

        Page<BaseEntityDefinition> result = findAllUseCase.findAll(ORG, null, null, pageable);

        assertThat(result.getContent()).containsExactly(def);
        verify(repository).findAll(any(Specification.class), eq(pageable));
    }

    @Test
    void findAll_withFilters_queriesSuccessfully() {
        Pageable pageable = PageRequest.of(0, 20);
        BaseEntityDefinition def = BaseEntityDefinition.builder().code("partner").status(EntityDefinitionStatus.ACTIVE).isEmbedded(false).build();
        when(repository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(def)));

        Page<BaseEntityDefinition> result = findAllUseCase.findAll(ORG, EntityDefinitionStatus.ACTIVE, false, pageable);

        assertThat(result.getContent()).containsExactly(def);
        verify(repository).findAll(any(Specification.class), eq(pageable));
    }

    @Test
    @SuppressWarnings("unchecked")
    void findAll_restrictsTheQueryToTheOrganization() {
        Pageable pageable = PageRequest.of(0, 20);
        when(repository.findAll(any(Specification.class), eq(pageable))).thenReturn(Page.empty());

        findAllUseCase.findAll(ORG, null, null, pageable);

        ArgumentCaptor<Specification<BaseEntityDefinition>> captor = ArgumentCaptor.forClass(Specification.class);
        verify(repository).findAll(captor.capture(), eq(pageable));
        Root<BaseEntityDefinition> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Path<Object> orgKeyPath = mock(Path.class);
        when(root.get("orgKey")).thenReturn(orgKeyPath);

        captor.getValue().toPredicate(root, query, cb);

        verify(cb).equal(orgKeyPath, ORG);
    }

    @Test
    void findByCode_success() {
        BaseEntityDefinition def = BaseEntityDefinition.builder().code("partner").build();
        when(repository.findByOrgKeyAndCode(ORG, "partner")).thenReturn(Optional.of(def));

        BaseEntityDefinition result = findByCodeUseCase.findByCode(ORG, "partner");

        assertThat(result).isSameAs(def);
    }

    @Test
    void findByCode_notFound_throwsNotFound() {
        when(repository.findByOrgKeyAndCode(ORG, "unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> findByCodeUseCase.findByCode(ORG, "unknown"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void replaceEntityDefinition_notFound_throwsNotFound() {
        when(repository.findByOrgKeyAndCode(ORG, "unknown")).thenReturn(Optional.empty());

        BaseEntityDefinition update = BaseEntityDefinition.builder().code("unknown").build();
        assertThatThrownBy(() -> replaceUseCase.replace(ORG, "unknown", update))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void deleteEntityDefinition_notFound_throwsNotFound() {
        when(repository.findByOrgKeyAndCode(ORG, "unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> deleteUseCase.delete(ORG, "unknown"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void deleteEntityDefinition_hasComponentParentDependents_throwsConflict() {
        BaseEntityDefinition existing = BaseEntityDefinition.builder().code("address").build();
        BaseEntityDefinition dependent = BaseEntityDefinition.builder()
            .code("partner")
            .componentParents(List.of("address"))
            .build();
        when(repository.findByOrgKeyAndCode(ORG, "address")).thenReturn(Optional.of(existing));
        when(existenceCheckPort.existsAnyInstanceOf(ORG, "address")).thenReturn(false);
        when(repository.findAllByOrgKey(ORG)).thenReturn(List.of(existing, dependent));

        assertThatThrownBy(() -> deleteUseCase.delete(ORG, "address"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("is still declared as a componentParent by another definition");
    }

    @Test
    void findByCode_definitionOfAnotherOrg_isNotFound() {
        when(repository.findByOrgKeyAndCode("globex", "partner")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> findByCodeUseCase.findByCode("globex", "partner"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void deleteEntityDefinition_componentParentInAnotherOrg_doesNotBlock() {
        BaseEntityDefinition existing = BaseEntityDefinition.builder().code("address").build();
        when(repository.findByOrgKeyAndCode(ORG, "address")).thenReturn(Optional.of(existing));
        when(existenceCheckPort.existsAnyInstanceOf(ORG, "address")).thenReturn(false);
        when(repository.findAllByOrgKey(ORG)).thenReturn(List.of(existing));

        deleteUseCase.delete(ORG, "address");

        verify(repository).delete(existing);
        verify(repository, never()).findAll();
    }

    @Test
    void addAttribute_definitionNotFound_throwsNotFound() {
        when(repository.findByOrgKeyAndCode(ORG, "unknown")).thenReturn(Optional.empty());

        BaseEntityAttribute attr = BaseEntityAttribute.builder().code("email").build();
        assertThatThrownBy(() -> addAttributeUseCase.addAttribute(ORG, "unknown", attr))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void replaceAttribute_definitionNotFound_throwsNotFound() {
        when(repository.findByOrgKeyAndCode(ORG, "unknown")).thenReturn(Optional.empty());

        BaseEntityAttribute attr = BaseEntityAttribute.builder().code("email").build();
        assertThatThrownBy(() -> replaceAttributeUseCase.replaceAttribute(ORG, "unknown", "email", attr))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void replaceAttribute_attributeNotFound_throwsNotFound() {
        BaseEntityDefinition existing = BaseEntityDefinition.builder()
                .code("partner")
                .attributes(new ArrayList<>())
                .build();
        when(repository.findByOrgKeyAndCode(ORG, "partner")).thenReturn(Optional.of(existing));

        BaseEntityAttribute attr = BaseEntityAttribute.builder().code("email").build();
        assertThatThrownBy(() -> replaceAttributeUseCase.replaceAttribute(ORG, "partner", "email", attr))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void deleteAttribute_definitionNotFound_throwsNotFound() {
        when(repository.findByOrgKeyAndCode(ORG, "unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> deleteAttributeUseCase.deleteAttribute(ORG, "unknown", "email"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void deleteAttribute_attributeNotFound_throwsNotFound() {
        BaseEntityDefinition existing = BaseEntityDefinition.builder()
                .code("partner")
                .attributes(new ArrayList<>())
                .build();
        when(repository.findByOrgKeyAndCode(ORG, "partner")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> deleteAttributeUseCase.deleteAttribute(ORG, "partner", "email"))
                .isInstanceOf(NotFoundException.class);
    }
}
