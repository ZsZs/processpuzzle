package com.processpuzzle.document.adapter.inbound;

import com.processpuzzle.document.model.DocumentInput;

import java.util.List;

/** The YAML boundary shape: exports round-trip as imports unchanged. */
public record DocumentYamlFile(List<DocumentInput> documents) {
}
