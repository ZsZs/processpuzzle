package com.processpuzzle.document.usecase.service;

import com.processpuzzle.core.definition.PlatformSubject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares {@code document} — the subject of the facts {@link DocumentPlatformFacts} publishes about a document — so
 * the event catalog can name a created or published document although no entity type is called that. A signature
 * fact is about the signed-for object, an entity, and needs no declaration.
 */
@Configuration(proxyBeanMethods = false)
public class DocumentPlatformSubjectConfiguration {

    @Bean
    PlatformSubject documentSubject() {
        return new PlatformSubject(DocumentPlatformFacts.SUBJECT_TYPE, DocumentPlatformFacts.FACT_ATTRIBUTES);
    }
}
