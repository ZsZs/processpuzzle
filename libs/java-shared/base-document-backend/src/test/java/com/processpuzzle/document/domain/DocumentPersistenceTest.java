package com.processpuzzle.document.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the tenant-scoped composite key and the {@link DocumentGraph} JSON column round-trip
 * correctly — the two things {@code RuleDefinitionPersistenceTest} proves for its own module's
 * mapping, applied to base-document's shape instead of an {@code @ElementCollection}.
 */
@DataJpaTest(showSql = false)
@EntityScan("com.processpuzzle.document.domain")
@EnableJpaRepositories("com.processpuzzle.document.domain")
class DocumentPersistenceTest {

    @Configuration
    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestConfig {
    }

    @Autowired
    private DocumentRepository repository;

    @BeforeEach
    void seed() {
        repository.deleteAll();
    }

    @Test
    void graphColumnRoundTripsPortsAndBlocks() {
        DocumentBlock text = new DocumentBlock("intro", BlockKind.TEXT, true, null,
                null, null, null, null, null);
        DocumentBlock chart = new DocumentBlock("chart-1", BlockKind.WIDGET, null, null,
                WidgetPlacement.REFERENCED, "entity-grid",
                Map.of("entityType", "Claim"), Map.of("rsqlFilter", "claimsFilter"), Map.of());
        DocumentInputPort port = new DocumentInputPort("claimsFilter", PortType.ENTITY_COLLECTION,
                false, "Filter applied to the claims grid", null, "Claim", AttributeVisibility.all(), null);
        DocumentGraph graph = new DocumentGraph(List.of(port), List.of(), List.of(text, chart));

        repository.saveAndFlush(new Document("demo", "q3-plan", "Q3 Plan", "desc", graph));

        Document reloaded = repository.findByOrgKeyAndId("demo", "q3-plan").orElseThrow();
        assertThat(reloaded.getGraph().blocks()).hasSize(2);
        assertThat(reloaded.getGraph().findBlock("chart-1")).isPresent()
                .get().extracting(DocumentBlock::placement).isEqualTo(WidgetPlacement.REFERENCED);
        assertThat(reloaded.getGraph().findBlock("chart-1").orElseThrow().inputBindings())
                .containsEntry("rsqlFilter", "claimsFilter");
        assertThat(reloaded.getGraph().inputPorts()).extracting(DocumentInputPort::name)
                .containsExactly("claimsFilter");
        assertThat(reloaded.getVersion()).isZero();
        assertThat(reloaded.getCreatedAt()).isNotNull();
    }

    @Test
    void sameDocumentIdCoexistsInDifferentOrganizations() {
        repository.saveAndFlush(new Document("org-a", "plan", "Plan A", null, DocumentGraph.empty()));
        repository.saveAndFlush(new Document("org-b", "plan", "Plan B", null, DocumentGraph.empty()));

        assertThat(repository.count()).isEqualTo(2);
        assertThat(repository.findByOrgKeyAndId("org-a", "plan").orElseThrow().getTitle()).isEqualTo("Plan A");
        assertThat(repository.findByOrgKeyAndId("org-b", "plan").orElseThrow().getTitle()).isEqualTo("Plan B");
        assertThat(repository.existsByOrgKeyAndId("org-a", "plan")).isTrue();
        assertThat(repository.existsByOrgKeyAndId("org-c", "plan")).isFalse();
    }

    @Test
    void versionIsHibernateManagedOptimisticLock() {
        Document saved = repository.saveAndFlush(
                new Document("demo", "plan", "Plan", null, DocumentGraph.empty()));
        assertThat(saved.getVersion()).isZero();

        saved.replace("Plan v2", null, DocumentGraph.empty());
        repository.saveAndFlush(saved);

        assertThat(saved.getVersion()).isEqualTo(1L);
    }

    @Test
    void deleteByOrgKeyRemovesOnlyThatOrganizationsDocuments() {
        repository.saveAndFlush(new Document("org-a", "plan-1", "P1", null, DocumentGraph.empty()));
        repository.saveAndFlush(new Document("org-a", "plan-2", "P2", null, DocumentGraph.empty()));
        repository.saveAndFlush(new Document("org-b", "plan-1", "P1", null, DocumentGraph.empty()));

        repository.deleteByOrgKey("org-a");
        repository.flush();

        assertThat(repository.findByOrgKey("org-a")).isEmpty();
        assertThat(repository.findByOrgKey("org-b")).hasSize(1);
    }
}
