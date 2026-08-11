package com.processpuzzle.document.usecase;

import com.processpuzzle.document.adapter.inbound.DocumentMapper;
import com.processpuzzle.document.domain.DocumentContent;
import com.processpuzzle.document.domain.DocumentPorts;
import com.processpuzzle.document.model.DocumentInput;
import com.processpuzzle.document.model.DocumentTranslationInput;
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
    private final DocumentMapper mapper;

    public ValidateDocument(DocumentReferentialIntegrityChecker integrityChecker, DocumentMapper mapper) {
        this.integrityChecker = integrityChecker;
        this.mapper = mapper;
    }

    public ValidationOutcome execute(DocumentInput input) {
        DocumentPorts ports = mapper.toPorts(input);
        Map<String, DocumentContent> byLocale = contentByLocale(input);
        List<DocumentValidationProblem> problems = new ArrayList<>();

        byLocale.forEach((locale, content) ->
                problems.addAll(DocumentValidationProblem.withLocale(integrityChecker.check(ports, content), locale)));

        // Cross-locale widget coverage, reported against each translation rather than the source.
        DocumentContent sourceContent = byLocale.get(input.getSourceLocale());
        if (sourceContent != null) {
            byLocale.forEach((locale, content) -> {
                if (!locale.equals(input.getSourceLocale())) {
                    problems.addAll(DocumentValidationProblem.withLocale(
                            integrityChecker.checkWidgetCoverage(sourceContent, input.getSourceLocale(), content),
                            locale));
                }
            });
        }

        boolean valid = DocumentValidationProblem.blocking(problems).isEmpty();
        return new ValidationOutcome(valid, problems);
    }

    private Map<String, DocumentContent> contentByLocale(DocumentInput input) {
        Map<String, DocumentContent> byLocale = new LinkedHashMap<>();
        if (input.getTranslations() == null) {
            return byLocale;
        }
        for (DocumentTranslationInput translation : input.getTranslations()) {
            DocumentContent content = mapper.toContentOrNull(translation);
            byLocale.put(translation.getLocale(), content == null ? DocumentContent.empty() : content);
        }
        return byLocale;
    }

    public record ValidationOutcome(boolean valid, List<DocumentValidationProblem> problems) {
    }
}
