package com.processpuzzle.baseentity.definition.adapters.outbound;

import com.processpuzzle.baseentity.instances.domain.EntityObjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EntityInstanceExistenceCheckAdapterTest {

    @Mock
    private EntityObjectRepository entityObjectRepository;

    private EntityInstanceExistenceCheckAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new EntityInstanceExistenceCheckAdapter(entityObjectRepository);
    }

    @Test
    void existsAnyInstanceOf_returnsTrue_whenRepositoryReturnsTrue() {
        when(entityObjectRepository.existsByEntityDefinitionCode("partner")).thenReturn(true);

        boolean result = adapter.existsAnyInstanceOf("partner");

        assertThat(result).isTrue();
        verify(entityObjectRepository).existsByEntityDefinitionCode("partner");
    }

    @Test
    void existsAnyInstanceOf_returnsFalse_whenRepositoryReturnsFalse() {
        when(entityObjectRepository.existsByEntityDefinitionCode("unknown")).thenReturn(false);

        boolean result = adapter.existsAnyInstanceOf("unknown");

        assertThat(result).isFalse();
        verify(entityObjectRepository).existsByEntityDefinitionCode("unknown");
    }
}
