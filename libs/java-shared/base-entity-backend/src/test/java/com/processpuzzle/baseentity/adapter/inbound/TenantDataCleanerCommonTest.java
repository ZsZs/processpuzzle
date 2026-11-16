package com.processpuzzle.baseentity.adapter.inbound;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.processpuzzle.baseentity.definition.domain.EntityDefinitionRepository;
import com.processpuzzle.baseentity.i18n.EntityTranslationRepository;
import com.processpuzzle.baseentity.instances.domain.EntityObjectRepository;
import com.processpuzzle.core.tenancy.DataSpaceDirectory;
import com.processpuzzle.shared.event.OrganizationDeletedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;

/** A common's shared records are never erased together with it. */
class TenantDataCleanerCommonTest {

    @Test
    void refusesToEraseACommon() {
        DataSpaceDirectory directory = mock(DataSpaceDirectory.class);
        when(directory.isCommon("sailing-common")).thenReturn(true);
        @SuppressWarnings("unchecked")
        ObjectProvider<DataSpaceDirectory> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(directory);
        EntityDefinitionRepository definitions = mock(EntityDefinitionRepository.class);
        EntityObjectRepository objects = mock(EntityObjectRepository.class);
        TenantDataCleaner cleaner = new TenantDataCleaner(definitions, objects, mock(EntityTranslationRepository.class),
            mock(ApplicationEventPublisher.class), provider);

        assertThatThrownBy(() -> cleaner.onOrganizationDeleted(new OrganizationDeletedEvent("sailing-common")))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("sailing-common");
        verifyNoInteractions(objects);
    }
}
