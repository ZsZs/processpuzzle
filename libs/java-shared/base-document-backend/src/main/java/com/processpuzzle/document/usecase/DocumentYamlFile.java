package com.processpuzzle.document.usecase;

import com.processpuzzle.document.model.DocumentInput;

import java.util.List;

/** The YAML file shape for both {@link ImportDocuments} and {@link ExportDocument} — one list under 'documents', so an export round-trips as an import file unchanged. */
public record DocumentYamlFile(List<DocumentInput> documents) {
}
