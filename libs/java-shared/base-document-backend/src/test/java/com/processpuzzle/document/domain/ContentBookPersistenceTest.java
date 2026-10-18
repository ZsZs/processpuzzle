package com.processpuzzle.document.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The content book mapping: composite key, the roles and tree JSON columns, slug uniqueness per organization. */
@DataJpaTest(showSql = false)
@EntityScan("com.processpuzzle.document.domain")
@EnableJpaRepositories("com.processpuzzle.document.domain")
class ContentBookPersistenceTest {

    private static final String DOC_A = "11111111-1111-1111-1111-111111111111";
    private static final String DOC_B = "22222222-2222-2222-2222-222222222222";

    @Configuration
    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestConfig {
    }

    @Autowired
    private ContentBookRepository repository;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    @Test
    void rolesAndTreeRoundTrip() {
        ContentBook book = new ContentBook("demo", "book-1", "handbook", "Handbook", "ada");
        book.replaceProperties("handbook", "Handbook", "All of it", true,
                new DocumentRoles(List.of("reader"), List.of("editor"), List.of()));
        book.replaceNodes(new ContentBookNodes(List.of(
                new ContentBookNode("a", DOC_A, null, 0), new ContentBookNode("b", DOC_B, "a", 0))));
        repository.saveAndFlush(book);

        ContentBook reloaded = repository.findByOrgKeyAndSlug("demo", "handbook").orElseThrow();
        assertThat(reloaded.getDescription()).isEqualTo("All of it");
        assertThat(reloaded.isPublic()).isTrue();
        assertThat(reloaded.getRoles().editorRoles()).containsExactly("editor");
        assertThat(reloaded.getNodes().documentIds()).containsExactly(DOC_A, DOC_B);
        assertThat(reloaded.getCreatedBy()).isEqualTo("ada");
        assertThat(reloaded.getCreatedAt()).isNotNull();
        assertThat(reloaded.getUpdatedAt()).isNotNull();
        assertThat(reloaded.getLockVersion()).isNotNull();
    }

    @Test
    void aNewBookHasAnEmptyTreeAndUnrestrictedRoles() {
        repository.saveAndFlush(new ContentBook("demo", "book-1", "handbook", "Handbook", "ada"));

        ContentBook reloaded = repository.findByOrgKeyAndId("demo", "book-1").orElseThrow();
        assertThat(reloaded.getNodes().nodes()).isEmpty();
        assertThat(reloaded.getRoles()).isEqualTo(DocumentRoles.unrestricted());
    }

    @Test
    void findsTheBooksThatShowADocument() {
        ContentBook showing = new ContentBook("demo", "book-1", "handbook", "Handbook", "ada");
        showing.replaceNodes(new ContentBookNodes(List.of(new ContentBookNode("a", DOC_A, null, 0))));
        repository.saveAndFlush(showing);
        repository.saveAndFlush(new ContentBook("demo", "book-2", "other", "Other", "ada"));

        assertThat(repository.findByOrgKeyReferencing("demo", DOC_A)).extracting(ContentBook::getId)
                .containsExactly("book-1");
        assertThat(repository.findByOrgKeyReferencing("elsewhere", DOC_A)).isEmpty();
    }

    @Test
    void aSlugIsUniqueWithinAnOrganizationOnly() {
        repository.saveAndFlush(new ContentBook("demo", "book-1", "handbook", "Handbook", "ada"));
        repository.saveAndFlush(new ContentBook("other-org", "book-2", "handbook", "Handbook", "ada"));
        assertThat(repository.existsByOrgKeyAndSlug("demo", "handbook")).isTrue();

        ContentBook copy = new ContentBook("demo", "book-3", "handbook", "Copy", "ada");
        assertThatThrownBy(() -> repository.saveAndFlush(copy))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void erasesOneTenantInOneStatement() {
        repository.saveAndFlush(new ContentBook("demo", "book-1", "handbook", "Handbook", "ada"));
        repository.saveAndFlush(new ContentBook("other-org", "book-2", "handbook", "Handbook", "ada"));

        assertThat(repository.eraseByOrgKey("demo")).isEqualTo(1);
        assertThat(repository.findByOrgKey("other-org")).hasSize(1);
    }
}
