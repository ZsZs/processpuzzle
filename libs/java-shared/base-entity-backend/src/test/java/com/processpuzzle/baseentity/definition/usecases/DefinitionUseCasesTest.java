package com.processpuzzle.baseentity.definition.usecases;

import com.processpuzzle.baseentity.common.ConflictException;
import com.processpuzzle.baseentity.definition.domain.*;
import com.processpuzzle.baseentity.definition.usecases.inbound.*;
import com.processpuzzle.baseentity.definition.usecases.outbound.EntityInstanceExistenceCheckPort;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
    private FindEntityDefinitionByCodeUseCase findByCodeUseCase;
    private FindAllEntityDefinitionsUseCase findAllUseCase;
    private AddAttributeUseCase addAttributeUseCase;
    private ReplaceAttributeUseCase replaceAttributeUseCase;
    private DeleteAttributeUseCase deleteAttributeUseCase;

    @BeforeEach
    void setUp() {
        createUseCase = new CreateEntityDefinitionUseCase(repository, validator);
        replaceUseCase = new ReplaceEntityDefinitionUseCase(repository, validator);
        deleteUseCase = new DeleteEntityDefinitionUseCase(repository, existenceCheckPort);
        findByCodeUseCase = new FindEntityDefinitionByCodeUseCase(repository);
        findAllUseCase = new FindAllEntityDefinitionsUseCase(repository);
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
}
