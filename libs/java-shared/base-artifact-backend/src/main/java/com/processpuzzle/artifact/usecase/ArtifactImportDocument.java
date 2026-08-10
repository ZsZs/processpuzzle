package com.processpuzzle.artifact.usecase;

import com.processpuzzle.artifact.model.ArtifactInput;

import java.util.List;

/** The YAML file shape for both {@link ImportArtifacts} and {@link ExportArtifact} — one list under 'artifacts', so an export round-trips as an import file unchanged. */
public record ArtifactImportDocument(List<ArtifactInput> artifacts) {
}
