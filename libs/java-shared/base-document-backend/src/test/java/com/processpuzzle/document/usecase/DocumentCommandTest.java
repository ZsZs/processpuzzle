package com.processpuzzle.document.usecase;

import com.processpuzzle.document.domain.DocumentContent;
import com.processpuzzle.document.domain.DocumentPorts;
import com.processpuzzle.document.domain.DocumentRoles;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentCommandTest {
    @Test
    void commandsCopyCollectionsAndRetainAbsentContent() {
        var translations = new ArrayList<>(List.of(new DocumentTranslationCommand("en", null)));
        var command = new DocumentCommand(properties(), translations);
        translations.clear();

        assertThat(command.translations()).hasSize(1);
        assertThat(command.translations().getFirst().content()).isNull();
        assertThat(command.translations().getFirst().contentOrEmpty()).isEqualTo(DocumentContent.empty());
        assertThatThrownBy(() -> command.translations().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThat(command.properties().ports()).isEqualTo(DocumentPorts.empty());
        assertThat(command.properties().roles()).isEqualTo(DocumentRoles.unrestricted());
    }

    @Test
    void authorDefaultingBelongsToCreationNotMetadataReplacement() {
        var properties = properties();
        var document = properties.create("demo", "id", "ada");
        assertThat(document.getAuthor()).isEqualTo("ada");
        assertThat(document.getCreatedBy()).isEqualTo("ada");

        properties.applyTo(document);
        assertThat(document.getAuthor()).isNull();
        assertThat(document.getCreatedBy()).isEqualTo("ada");
    }

    private static DocumentProperties properties() {
        return new DocumentProperties("slug", "Title", null, null, null, "en", false, null, null);
    }
}
