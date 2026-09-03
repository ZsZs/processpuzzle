package com.processpuzzle.app.adapter.inbound;

import com.processpuzzle.app.domain.AppDefinitionRepository;
import com.processpuzzle.app.domain.ModuleDefinitionRepository;
import com.processpuzzle.platformadmin.domain.event.OrganizationDeletedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * The cascade that used to be three lines inside {@code DeleteOrganization}, now reached by event
 * because that use case lives in another module.
 */
class TenantDataCleanerTest {

    private final AppDefinitionRepository appDefinitionRepository = mock(AppDefinitionRepository.class);
    private final ModuleDefinitionRepository moduleDefinitionRepository = mock(ModuleDefinitionRepository.class);
    private final TenantDataCleaner cleaner =
            new TenantDataCleaner(appDefinitionRepository, moduleDefinitionRepository);

    @Test
    void deletesBothOfThisFeaturesTenantScopedTables() {
        cleaner.onOrganizationDeleted(new OrganizationDeletedEvent("my-org"));

        verify(appDefinitionRepository).deleteByOrgKey("my-org");
        verify(moduleDefinitionRepository).deleteByOrgKey("my-org");
    }

    /**
     * The phase is the whole correctness argument, and it is invisible in behaviour: an
     * {@code AFTER_COMMIT} listener would run with the deleting transaction already finished, and
     * these two deletes would be discarded without an error — orphaned app definitions and no failing
     * assertion anywhere. So the annotation itself is asserted.
     */
    @Test
    void runsBeforeCommitSoItsDeletesJoinTheDeletingTransaction() throws NoSuchMethodException {
        Method handler = TenantDataCleaner.class
                .getDeclaredMethod("onOrganizationDeleted", OrganizationDeletedEvent.class);

        TransactionalEventListener annotation = handler.getAnnotation(TransactionalEventListener.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.phase()).isEqualTo(TransactionPhase.BEFORE_COMMIT);
    }
}
