package com.processpuzzle.document.usecase;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.processpuzzle.document.adapter.inbound.DocumentMapper;
import com.processpuzzle.document.domain.Document;
import com.processpuzzle.document.domain.DocumentContent;
import com.processpuzzle.document.domain.DocumentDraft;
import com.processpuzzle.document.domain.DocumentDraftRepository;
import com.processpuzzle.document.domain.DocumentPorts;
import com.processpuzzle.document.domain.DocumentRepository;
import com.processpuzzle.document.model.DocumentInput;
import com.processpuzzle.document.model.DocumentTranslationInput;
import com.processpuzzle.document.usecase.service.DocumentGuard;
import com.processpuzzle.document.usecase.service.DocumentReferentialIntegrityChecker;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Bulk-imports documents from YAML. {@link DocumentInput} deserializes straight from YAML with
 * Jackson's YAML factory — no intermediate {@code *YamlEntry} DTO is needed the way rules use one,
 * since a document has no cross-entry linkage comparable to {@code extendsRuleId} to resolve during
 * import. All-or-nothing: the whole file is validated before anything is persisted, the guarantee
 * the contract promises.
 *
 * <h2>Matching on slug, not id</h2>
 *
 * <p>Entries are matched on {@code slug} and any {@code id} in the file is ignored. Ids are
 * organization-local UUIDs, so importing by id into a second organization would either collide with
 * an unrelated document or fabricate identity for one; the slug is the name a human gave the
 * document and is what "the same document" means across organizations.
 *
 * <h2>Publication state is not importable</h2>
 *
 * <p>Content lands in each translation's draft. A newly imported document is unpublished, and
 * re-importing over an existing one leaves its published snapshots exactly as they were — so the
 * import shows up as unpublished changes rather than silently going live. Publishing is an editorial
 * act in the target organization, not a property of a file someone sent.
 */
@Service
public class ImportDocuments {

    private final DocumentRepository repository;
    private final DocumentDraftRepository draftRepository;
    private final DocumentReferentialIntegrityChecker integrityChecker;
    private final DocumentGuard guard;
    private final DocumentMapper mapper;
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory())
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    public ImportDocuments(DocumentRepository repository,
                           DocumentDraftRepository draftRepository,
                           DocumentReferentialIntegrityChecker integrityChecker,
                           DocumentGuard guard,
                           DocumentMapper mapper) {
        this.repository = repository;
        this.draftRepository = draftRepository;
        this.integrityChecker = integrityChecker;
        this.guard = guard;
        this.mapper = mapper;
    }

    @Transactional
    public ImportOutcome execute(String orgKey, InputStream input) throws IOException {
        guard.requireOrganizationAccess(orgKey);

        DocumentYamlFile yamlFile = yamlMapper.readValue(input, DocumentYamlFile.class);
        List<DocumentInput> entries = yamlFile.documents() == null ? List.of() : yamlFile.documents();

        List<String> errors = validate(entries);
        if (!errors.isEmpty()) {
            return new ImportOutcome(0, 0, errors);
        }

        int created = 0;
        int updated = 0;
        for (DocumentInput entry : entries) {
            if (repository.findByOrgKeyAndSlug(orgKey, entry.getSlug()).isPresent()) {
                applyToExisting(orgKey, entry);
                updated++;
            } else {
                createFrom(orgKey, entry);
                created++;
            }
        }
        return new ImportOutcome(created, updated, List.of());
    }

    private List<String> validate(List<DocumentInput> entries) {
        List<String> errors = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            DocumentInput entry = entries.get(i);
            if (entry.getSlug() == null || entry.getSlug().isBlank()) {
                errors.add("Entry " + i + " is missing 'slug' and was skipped.");
                continue;
            }
            if (entry.getSourceLocale() == null || entry.getSourceLocale().isBlank()) {
                errors.add("Entry '" + entry.getSlug() + "' is missing 'sourceLocale'.");
                continue;
            }
            DocumentPorts ports = mapper.toPorts(entry);
            contentByLocale(entry).forEach((locale, content) -> {
                List<DocumentValidationProblem> blocking =
                        DocumentValidationProblem.blocking(integrityChecker.check(ports, content));
                if (!blocking.isEmpty()) {
                    errors.add("Entry '" + entry.getSlug() + "' locale '" + locale + "': " + blocking);
                }
            });
        }
        return errors;
    }

    private void createFrom(String orgKey, DocumentInput entry) {
        String documentId = UUID.randomUUID().toString();
        Document document = mapper.toDomain(orgKey, documentId, entry, guard.currentPrincipal());
        repository.save(document);

        Map<String, DocumentContent> byLocale = contentByLocale(entry);
        byLocale.putIfAbsent(entry.getSourceLocale(), DocumentContent.empty());
        Long sourceRevision = 1L;
        byLocale.forEach((locale, content) -> draftRepository.save(new DocumentDraft(
                orgKey, documentId, locale, content,
                locale.equals(entry.getSourceLocale()) ? null : sourceRevision)));
    }

    private void applyToExisting(String orgKey, DocumentInput entry) {
        Document existing = repository.findByOrgKeyAndSlug(orgKey, entry.getSlug()).orElseThrow();
        existing.replaceProperties(
                entry.getSlug(),
                entry.getTitle(),
                entry.getSubject(),
                entry.getDescription(),
                entry.getAuthor(),
                entry.getSourceLocale(),
                Boolean.TRUE.equals(entry.getIsPublic()),
                mapper.toRoles(entry),
                mapper.toPorts(entry));
        repository.save(existing);

        Map<String, DocumentDraft> draftsByLocale = new HashMap<>();
        for (DocumentDraft draft : draftRepository.findByOrgKeyAndDocumentId(orgKey, existing.getId())) {
            draftsByLocale.put(draft.getLocale(), draft);
        }
        contentByLocale(entry).forEach((locale, content) -> {
            DocumentDraft draft = draftsByLocale.get(locale);
            if (draft == null) {
                draftRepository.save(new DocumentDraft(orgKey, existing.getId(), locale, content,
                        locale.equals(entry.getSourceLocale()) ? null : sourceRevisionOf(draftsByLocale, entry)));
            } else {
                draft.replaceBlocks(content.blocks());
                draftRepository.save(draft);
            }
        });
    }

    private static Long sourceRevisionOf(Map<String, DocumentDraft> draftsByLocale, DocumentInput entry) {
        DocumentDraft source = draftsByLocale.get(entry.getSourceLocale());
        return source == null ? null : source.getRevision();
    }

    private Map<String, DocumentContent> contentByLocale(DocumentInput entry) {
        Map<String, DocumentContent> byLocale = new LinkedHashMap<>();
        if (entry.getTranslations() == null) {
            return byLocale;
        }
        for (DocumentTranslationInput translation : entry.getTranslations()) {
            DocumentContent content = mapper.toContentOrNull(translation);
            byLocale.put(translation.getLocale(), content == null ? DocumentContent.empty() : content);
        }
        return byLocale;
    }
}
