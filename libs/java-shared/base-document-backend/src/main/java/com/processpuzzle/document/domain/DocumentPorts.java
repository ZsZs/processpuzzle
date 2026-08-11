package com.processpuzzle.document.domain;

import java.util.List;

/**
 * A document's declared input and output ports as one immutable value, persisted as one JSON
 * column on {@link Document} (see {@link DocumentPortsConverter}).
 *
 * <p>Language-invariant on purpose: a port declaration is structural wiring rather than content,
 * and every translation's widget {@code inputBindings}/{@code outputBindings} resolve against
 * these same names. Were the declarations held per locale, a port renamed in one language would
 * silently orphan the bindings in another.
 */
public record DocumentPorts(
        List<DocumentInputPort> inputPorts,
        List<DocumentOutputPort> outputPorts) {

    public DocumentPorts {
        inputPorts = inputPorts == null ? List.of() : List.copyOf(inputPorts);
        outputPorts = outputPorts == null ? List.of() : List.copyOf(outputPorts);
    }

    public static DocumentPorts empty() {
        return new DocumentPorts(List.of(), List.of());
    }

    public boolean declaresInputPort(String name) {
        return inputPorts.stream().anyMatch(port -> name.equals(port.name()));
    }

    public boolean declaresOutputPort(String name) {
        return outputPorts.stream().anyMatch(port -> name.equals(port.name()));
    }
}
