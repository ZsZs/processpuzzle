package com.processpuzzle.document.usecase;

import com.processpuzzle.document.domain.DocumentContent;
import com.processpuzzle.document.domain.DocumentPorts;
import com.processpuzzle.document.usecase.service.DocumentReferentialIntegrityChecker;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Validates a candidate document — every translation it carries, against its ports.
 *
 * <p>Stateless and read-only by construction: no repository dependency at all, since a candidate
 * document need not exist yet. The write paths call the same
 * {@link DocumentReferentialIntegrityChecker} directly rather than going through this use case, so
 * "valid enough to persist" and "valid enough to preview" stay one source of truth without an extra
 * hop.
 */
@Service
public class ValidateDocument {

    private final DocumentReferentialIntegrityChecker integrityChecker;

    public ValidateDocument(DocumentReferentialIntegrityChecker integrityChecker) {
        this.integrityChecker = integrityChecker;
    }

    public ValidationOutcome execute(DocumentCommand input) {
        DocumentPorts ports = input.properties().ports();
        Map<String, DocumentContent> byLocale = contentByLocale(input);
        List<DocumentValidationProblem> problems = new ArrayList<>();

        byLocale.forEach((locale, content) ->
                problems.addAll(DocumentValidationProblem.withLocale(integrityChecker.check(ports, content), locale)));

        // Cross-locale widget coverage, reported against each translation rather than the source.
        DocumentContent sourceContent = byLocale.get(input.properties().sourceLocale());
        if (sourceContent != null) {
            byLocale.forEach((locale, content) -> {
                if (!locale.equals(input.properties().sourceLocale())) {
                    problems.addAll(DocumentValidationProblem.withLocale(
                            integrityChecker.checkWidgetCoverage(sourceContent, input.properties().sourceLocale(), content),
                            locale));
                }
            });
        }

        boolean valid = DocumentValidationProblem.blocking(problems).isEmpty();
        return new ValidationOutcome(valid, problems);
    }

    private Map<String, DocumentContent> contentByLocale(DocumentCommand input) {
        Map<String, DocumentContent> byLocale = new LinkedHashMap<>();
        for (DocumentTranslationCommand translation : input.translations()) {
            byLocale.put(translation.locale(), translation.contentOrEmpty());
        }
        return byLocale;
    }

    public record ValidationOutcome(boolean valid, List<DocumentValidationProblem> problems) {
    }
}
