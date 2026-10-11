package com.processpuzzle.document;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class DocumentApplicationArchitectureTest {

    @Test
    void allApplicationClassesAreIndependentOfAdaptersAndTransport() {
        var production = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.processpuzzle.document");

        noClasses().that().resideInAPackage("com.processpuzzle.document.usecase..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.processpuzzle.document.adapter..",
                        "com.processpuzzle.document.model..",
                        "com.processpuzzle.document.api..",
                        "com.processpuzzle.shared.model..",
                        "com.fasterxml.jackson.dataformat..",
                        "org.springframework.web..",
                        "org.springframework.http..")
                .check(production);
    }
}
