package com.processpuzzle.baseentity.adapter.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.processpuzzle.baseentity.instances.domain.EntityObjectRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

class EntityObjectDataProbeTest {

    @Test
    void countsTheEntityObjectsOfTheOrganization() {
        EntityObjectRepository repository = mock(EntityObjectRepository.class);
        when(repository.countByOrgKey("acme")).thenReturn(4L);
        EntityObjectDataProbe probe = new EntityObjectDataProbe(repository);

        assertThat(probe.count("acme")).isEqualTo(4L);
        assertThat(probe.label()).isEqualTo("entity objects");
    }

    @Test
    void countsOnlyTheObjectsOfTheGivenEntityTypes() {
        EntityObjectRepository repository = mock(EntityObjectRepository.class);
        when(repository.countByOrgKeyAndEntityDefinitionCodeIn("acme", List.of("inv-item", "inv-location"))).thenReturn(3L);
        EntityObjectDataProbe probe = new EntityObjectDataProbe(repository);

        assertThat(probe.definitionKind()).isEqualTo("entity");
        assertThat(probe.count("acme", List.of("inv-item", "inv-location"))).isEqualTo(3L);
        assertThat(probe.count("acme", List.of())).isZero();
    }
}
