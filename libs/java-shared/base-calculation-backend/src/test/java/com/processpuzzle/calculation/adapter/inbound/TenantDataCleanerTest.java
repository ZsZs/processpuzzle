package com.processpuzzle.calculation.adapter.inbound;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.processpuzzle.calculation.domain.CalculationDefinitionRepository;
import com.processpuzzle.calculation.domain.CalculationRunRepository;
import com.processpuzzle.calculation.i18n.CalculationTranslationRepository;
import com.processpuzzle.shared.event.OrganizationDeletedEvent;
import org.junit.jupiter.api.Test;

class TenantDataCleanerTest {

    @Test
    void erasesTheTenantsCalculationsRunsAndTranslations() {
        CalculationDefinitionRepository definitions = mock(CalculationDefinitionRepository.class);
        CalculationRunRepository runs = mock(CalculationRunRepository.class);
        CalculationTranslationRepository translations = mock(CalculationTranslationRepository.class);

        new TenantDataCleaner(definitions, runs, translations).onOrganizationDeleted(new OrganizationDeletedEvent("acme"));

        verify(definitions).eraseByOrgKey("acme");
        verify(runs).eraseByOrgKey("acme");
        verify(translations).eraseByOrgKey("acme");
    }
}
