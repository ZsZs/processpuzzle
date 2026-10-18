package com.processpuzzle.document.usecase.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.processpuzzle.core.definition.PlatformSubject;
import org.junit.jupiter.api.Test;

class DocumentPlatformSubjectConfigurationTest {

    @Test
    void declaresTheDocumentSubjectWithTheAttributesItsFactsCarry() {
        PlatformSubject subject = new DocumentPlatformSubjectConfiguration().documentSubject();

        assertThat(subject.subjectType()).isEqualTo(DocumentPlatformFacts.SUBJECT_TYPE);
        assertThat(subject.attributes()).isEqualTo(DocumentPlatformFacts.FACT_ATTRIBUTES);
    }
}
