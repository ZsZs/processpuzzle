package com.processpuzzle.document.usecase;

import com.processpuzzle.document.domain.Document;
import com.processpuzzle.document.domain.DocumentPorts;
import com.processpuzzle.document.domain.DocumentRoles;
import com.processpuzzle.document.usecase.exception.DocumentAccessDeniedException;
import com.processpuzzle.document.usecase.port.DocumentAccessPolicy;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The access ladder, rung by rung. Mirrors {@code OrganizationGuardTest} in base-app for the same
 * reason it exists there: these are the decisions that go wrong silently, and each one is one line
 * of production code with a lot riding on it.
 */
class DocumentGuardTest {

    private static final String ORG = "demo";
    private static final String ID = "11111111-1111-1111-1111-111111111111";

    @Test
    void withNoPolicyBeanTheGuardFallsBackToPermitAll() {
        // Matches production when no identity provider is wired: the feature has to stay usable, so
        // the fallback permits rather than denies.
        assertThat(TestGuards.permitAll().canRead(restricted("secret-reader"))).isTrue();
        assertThatCode(() -> TestGuards.permitAll().requireEditor(restricted("secret-reader")))
                .doesNotThrowAnyException();
    }

    @Test
    void aPublicDocumentIsReadableWithoutAuthentication() {
        Document document = publicDocument();

        assertThat(TestGuards.with(anonymous()).canRead(document)).isTrue();
    }

    @Test
    void aPublicDocumentIsStillOnlyEditableByItsEditors() {
        // isPublic widens who may read published content. It says nothing about who may write, and
        // conflating the two would make every public page world-editable.
        Document document = publicDocument();
        document.replaceProperties("getting-started", "Getting started", null, null, null, "en", true,
                new DocumentRoles(List.of(), List.of("editor"), List.of()), DocumentPorts.empty());

        assertThatThrownBy(() -> TestGuards.with(holding("reader")).requireEditor(document))
                .isInstanceOf(DocumentAccessDeniedException.class);
    }

    @Test
    void aNonPublicDocumentIsNotReadableWithoutAuthentication() {
        assertThat(TestGuards.with(anonymous()).canRead(unrestricted())).isFalse();
    }

    @Test
    void anEmptyReaderRoleListMeansAnyAuthenticatedMember() {
        // The convention NavNode.roles establishes in base-app: empty means "any member", not
        // "nobody". Getting this backwards locks everyone out of every document by default.
        assertThat(TestGuards.with(holding()).canRead(unrestricted())).isTrue();
    }

    @Test
    void aReaderRoleListIsEnforcedWhenPresent() {
        Document document = restricted("secret-reader");

        assertThat(TestGuards.with(holding("secret-reader")).canRead(document)).isTrue();
        assertThat(TestGuards.with(holding("someone-else")).canRead(document)).isFalse();
    }

    @Test
    void publisherRolesFallBackToEditorRolesButOverrideThemWhenPresent() {
        Document editorsPublish = document();
        editorsPublish.replaceProperties("getting-started", "Getting started", null, null, null, "en", false,
                new DocumentRoles(List.of(), List.of("editor"), List.of()), DocumentPorts.empty());
        assertThatCode(() -> TestGuards.with(holding("editor")).requirePublisher(editorsPublish))
                .doesNotThrowAnyException();

        Document separatePublishers = document();
        separatePublishers.replaceProperties("getting-started", "Getting started", null, null, null, "en", false,
                new DocumentRoles(List.of(), List.of("editor"), List.of("chief-editor")), DocumentPorts.empty());
        assertThatThrownBy(() -> TestGuards.with(holding("editor")).requirePublisher(separatePublishers))
                .isInstanceOf(DocumentAccessDeniedException.class);
        assertThatCode(() -> TestGuards.with(holding("chief-editor")).requirePublisher(separatePublishers))
                .doesNotThrowAnyException();
    }

    @Test
    void organizationAccessIsDelegatedToThePolicy() {
        DocumentAccessPolicy denying = new DocumentAccessPolicy() {
            @Override
            public void requireAccess(String orgKey) {
                throw new DocumentAccessDeniedException("not a member of " + orgKey);
            }
        };

        assertThatThrownBy(() -> TestGuards.with(denying).requireEditor(unrestricted()))
                .isInstanceOf(DocumentAccessDeniedException.class)
                .hasMessageContaining("not a member");
    }

    // region fixtures
    private static Document document() {
        return new Document(ORG, ID, "getting-started", "Getting started", "en", "ada");
    }

    private static Document unrestricted() {
        return document();
    }

    private static Document publicDocument() {
        Document document = document();
        document.replaceProperties("getting-started", "Getting started", null, null, null, "en", true,
                DocumentRoles.unrestricted(), DocumentPorts.empty());
        return document;
    }

    private static Document restricted(String readerRole) {
        Document document = document();
        document.replaceProperties("getting-started", "Getting started", null, null, null, "en", false,
                new DocumentRoles(List.of(readerRole), List.of(readerRole), List.of()), DocumentPorts.empty());
        return document;
    }

    private static DocumentAccessPolicy anonymous() {
        return new DocumentAccessPolicy() {
            @Override
            public boolean isAuthenticated() {
                return false;
            }
        };
    }

    private static DocumentAccessPolicy holding(String... roles) {
        List<String> held = List.of(roles);
        return new DocumentAccessPolicy() {
            @Override
            public boolean hasAnyRole(Collection<String> requiredRoles) {
                return requiredRoles.stream().anyMatch(held::contains);
            }
        };
    }
    // endregion
}
