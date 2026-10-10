package com.processpuzzle.widget.adapter.inbound;

import com.processpuzzle.shared.event.OrganizationDeletedEvent;
import com.processpuzzle.widget.domain.WidgetDefinition;
import com.processpuzzle.widget.domain.WidgetDefinitionRepository;
import com.processpuzzle.widget.i18n.WidgetTranslationBundle;
import com.processpuzzle.widget.i18n.WidgetTranslationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Erasing one tenant removes every row this feature holds for it, and nothing of another's.
 *
 * <p>Against a real transaction, because that is the whole correctness argument: the listener is
 * {@code BEFORE_COMMIT}, and an {@code AFTER_COMMIT} one would have its deletes discarded without an
 * error. Publishing the event inside a committed transaction and finding the rows gone is what proves
 * the phase, which is why the class suspends {@code @DataJpaTest}'s own rolled-back transaction.
 *
 * <p>H2 in PostgreSQL mode, because the translation bundles map their messages as {@code jsonb}.
 */
@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:widget-erasure;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop"})
@EntityScan({"com.processpuzzle.widget.domain", "com.processpuzzle.widget.i18n"})
@EnableJpaRepositories({"com.processpuzzle.widget.domain", "com.processpuzzle.widget.i18n"})
@Import(TenantDataCleaner.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class TenantDataCleanerTest {

    private static final String ERASED = "erased-org";
    private static final String KEPT = "kept-org";

    @Configuration
    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestConfig {
    }

    @Autowired
    private WidgetDefinitionRepository widgets;

    @Autowired
    private WidgetTranslationRepository translations;

    @Autowired
    private ApplicationEventPublisher events;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void erasesEveryRowOfTheTenantAndNothingOfAnother() {
        givenWidgetsAndTranslationsFor(ERASED);
        givenWidgetsAndTranslationsFor(KEPT);

        erase(ERASED);

        assertThat(widgets.findAll()).extracting(WidgetDefinition::getOrgKey).containsOnly(KEPT).hasSize(2);
        assertThat(translations.findAll()).extracting(bundle -> bundle.getKey().getOrgKey())
                .containsOnly(KEPT);
    }

    @Test
    void aRolledBackErasureDeletesNothing() {
        givenWidgetsAndTranslationsFor(ERASED);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            events.publishEvent(new OrganizationDeletedEvent(ERASED));
            status.setRollbackOnly();
        });

        assertThat(widgets.count()).isEqualTo(2);
        assertThat(translations.count()).isEqualTo(1);
    }

    /** A tenant with nothing here — or erased twice — is not an error. */
    @Test
    void erasingATenantWithNoWidgetsIsHarmless() {
        erase("nobody");

        assertThat(widgets.count()).isZero();
    }

    private void erase(String orgKey) {
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> events.publishEvent(new OrganizationDeletedEvent(orgKey)));
    }

    private void givenWidgetsAndTranslationsFor(String orgKey) {
        widgets.saveAndFlush(new WidgetDefinition(orgKey, "kpi-tile", "KPI tile"));
        widgets.saveAndFlush(new WidgetDefinition(orgKey, "line-chart", "Line chart"));
        translations.saveAndFlush(new WidgetTranslationBundle(orgKey, "widget", "en", Map.of("title", "Widgets")));
    }

    @AfterEach
    void cleanUp() {
        widgets.deleteAll();
        translations.deleteAll();
    }
}
