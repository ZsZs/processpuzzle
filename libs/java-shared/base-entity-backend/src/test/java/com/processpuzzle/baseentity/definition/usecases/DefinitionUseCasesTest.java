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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefinitionUseCasesTest {

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
        when(repository.existsByCode("partner")).thenReturn(false);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        BaseEntityDefinition result = createUseCase.create(input);

        assertThat(result.getCode()).isEqualTo("partner");
        verify(validator).validate(input);
        verify(repository).save(input);
    }

    @Test
    void createEntityDefinition_alreadyExists_throwsConflict() {
        BaseEntityDefinition input = BaseEntityDefinition.builder().code("partner").build();
        when(repository.existsByCode("partner")).thenReturn(true);

        assertThatThrownBy(() -> createUseCase.create(input))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void replaceEntityDefinition_success() {
        BaseEntityDefinition existing = BaseEntityDefinition.builder().id(UUID.randomUUID()).code("partner").name("Old").build();
        BaseEntityDefinition update = BaseEntityDefinition.builder().code("partner").name("New").build();

        when(repository.findByCode("partner")).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        BaseEntityDefinition result = replaceUseCase.replace("partner", update);

        assertThat(result.getName()).isEqualTo("New");
        verify(validator).validate(existing);
        verify(repository).save(existing);
    }

    @Test
    void replaceEntityDefinition_codeMismatch_throwsConflict() {
        BaseEntityDefinition existing = BaseEntityDefinition.builder().id(UUID.randomUUID()).code("partner").name("Old").build();
        BaseEntityDefinition update = BaseEntityDefinition.builder().code("different").name("New").build();

        when(repository.findByCode("partner")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> replaceUseCase.replace("partner", update))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void deleteEntityDefinition_success() {
        BaseEntityDefinition existing = BaseEntityDefinition.builder().code("partner").build();
        when(repository.findByCode("partner")).thenReturn(Optional.of(existing));
        when(existenceCheckPort.existsAnyInstanceOf("partner")).thenReturn(false);

        deleteUseCase.delete("partner");

        verify(repository).delete(existing);
    }

    @Test
    void deleteEntityDefinition_instancesExist_throwsConflict() {
        BaseEntityDefinition existing = BaseEntityDefinition.builder().code("partner").build();
        when(repository.findByCode("partner")).thenReturn(Optional.of(existing));
        when(existenceCheckPort.existsAnyInstanceOf("partner")).thenReturn(true);

        assertThatThrownBy(() -> deleteUseCase.delete("partner"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void addAttribute_success() {
        BaseEntityDefinition existing = BaseEntityDefinition.builder()
                .code("partner")
                .attributes(new ArrayList<>())
                .build();
        BaseEntityAttribute attribute = BaseEntityAttribute.builder().code("email").name("Email").build();

        when(repository.findByCode("partner")).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        BaseEntityAttribute result = addAttributeUseCase.addAttribute("partner", attribute);

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

        when(repository.findByCode("partner")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> addAttributeUseCase.addAttribute("partner", duplicate))
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

        when(repository.findByCode("partner")).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        BaseEntityAttribute result = replaceAttributeUseCase.replaceAttribute("partner", "email", replacement);

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

        when(repository.findByCode("partner")).thenReturn(Optional.of(existing));

        deleteAttributeUseCase.deleteAttribute("partner", "email");

        assertThat(existing.getAttributes()).isEmpty();
        verify(repository).save(existing);
    }

    @Test
    void findAll_withNullFilters_queriesSuccessfully() {
        Pageable pageable = PageRequest.of(0, 20);
        BaseEntityDefinition def = BaseEntityDefinition.builder().code("partner").build();
        when(repository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(def)));

        Page<BaseEntityDefinition> result = findAllUseCase.findAll(null, null, pageable);

        assertThat(result.getContent()).containsExactly(def);
        verify(repository).findAll(any(Specification.class), eq(pageable));
    }

    @Test
    void findAll_withFilters_queriesSuccessfully() {
        Pageable pageable = PageRequest.of(0, 20);
        BaseEntityDefinition def = BaseEntityDefinition.builder().code("partner").status(EntityDefinitionStatus.ACTIVE).isEmbedded(false).build();
        when(repository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(def)));

        Page<BaseEntityDefinition> result = findAllUseCase.findAll(EntityDefinitionStatus.ACTIVE, false, pageable);

        assertThat(result.getContent()).containsExactly(def);
        verify(repository).findAll(any(Specification.class), eq(pageable));
    }

    @Test
    void findByCode_success() {
        BaseEntityDefinition def = BaseEntityDefinition.builder().code("partner").build();
        when(repository.findByCode("partner")).thenReturn(Optional.of(def));

        BaseEntityDefinition result = findByCodeUseCase.findByCode("partner");

        assertThat(result).isSameAs(def);
    }

    @Test
    void findByCode_notFound_throwsNotFound() {
        when(repository.findByCode("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> findByCodeUseCase.findByCode("unknown"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void replaceEntityDefinition_notFound_throwsNotFound() {
        when(repository.findByCode("unknown")).thenReturn(Optional.empty());

        BaseEntityDefinition update = BaseEntityDefinition.builder().code("unknown").build();
        assertThatThrownBy(() -> replaceUseCase.replace("unknown", update))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void deleteEntityDefinition_notFound_throwsNotFound() {
        when(repository.findByCode("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> deleteUseCase.delete("unknown"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void deleteEntityDefinition_hasComponentParentDependents_throwsConflict() {
        BaseEntityDefinition existing = BaseEntityDefinition.builder().code("address").build();
        when(repository.findByCode("address")).thenReturn(Optional.of(existing));
        when(existenceCheckPort.existsAnyInstanceOf("address")).thenReturn(false);
        when(repository.findByComponentParentsContaining("address"))
                .thenReturn(List.of(BaseEntityDefinition.builder().code("partner").build()));

        assertThatThrownBy(() -> deleteUseCase.delete("address"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("is still declared as a componentParent by another definition");
    }

    @Test
    void addAttribute_definitionNotFound_throwsNotFound() {
        when(repository.findByCode("unknown")).thenReturn(Optional.empty());

        BaseEntityAttribute attr = BaseEntityAttribute.builder().code("email").build();
        assertThatThrownBy(() -> addAttributeUseCase.addAttribute("unknown", attr))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void replaceAttribute_definitionNotFound_throwsNotFound() {
        when(repository.findByCode("unknown")).thenReturn(Optional.empty());

        BaseEntityAttribute attr = BaseEntityAttribute.builder().code("email").build();
        assertThatThrownBy(() -> replaceAttributeUseCase.replaceAttribute("unknown", "email", attr))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void replaceAttribute_attributeNotFound_throwsNotFound() {
        BaseEntityDefinition existing = BaseEntityDefinition.builder()
                .code("partner")
                .attributes(new ArrayList<>())
                .build();
        when(repository.findByCode("partner")).thenReturn(Optional.of(existing));

        BaseEntityAttribute attr = BaseEntityAttribute.builder().code("email").build();
        assertThatThrownBy(() -> replaceAttributeUseCase.replaceAttribute("partner", "email", attr))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void deleteAttribute_definitionNotFound_throwsNotFound() {
        when(repository.findByCode("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> deleteAttributeUseCase.deleteAttribute("unknown", "email"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void deleteAttribute_attributeNotFound_throwsNotFound() {
        BaseEntityDefinition existing = BaseEntityDefinition.builder()
                .code("partner")
                .attributes(new ArrayList<>())
                .build();
        when(repository.findByCode("partner")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> deleteAttributeUseCase.deleteAttribute("partner", "email"))
                .isInstanceOf(NotFoundException.class);
    }
}
