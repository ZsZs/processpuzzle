package com.processpuzzle.document.usecase;

import com.processpuzzle.document.domain.Document;
import com.processpuzzle.document.domain.DocumentContent;
import com.processpuzzle.document.domain.DocumentDraft;
import com.processpuzzle.document.domain.DocumentDraftRepository;
import com.processpuzzle.document.domain.DocumentPorts;
import com.processpuzzle.document.domain.DocumentRepository;
import com.processpuzzle.document.domain.DocumentStatus;
import com.processpuzzle.document.domain.PublishedDocument;
import com.processpuzzle.document.domain.PublishedDocumentRepository;
import com.processpuzzle.document.usecase.service.DocumentGuard;
import com.processpuzzle.document.usecase.service.DocumentPlatformFacts;
import com.processpuzzle.document.usecase.service.DocumentReferentialIntegrityChecker;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Bulk-imports already decoded candidates. All-or-nothing: all candidates are validated and all
 * existing targets authorized before anything is mutated or persisted.
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
    private final PublishedDocumentRepository publishedRepository;
    private final DocumentReferentialIntegrityChecker integrityChecker;
    private final DocumentGuard guard;
    private final DocumentPlatformFacts facts;

    public ImportDocuments(DocumentRepository repository,
                           DocumentDraftRepository draftRepository,
                           PublishedDocumentRepository publishedRepository,
                           DocumentReferentialIntegrityChecker integrityChecker,
                           DocumentGuard guard,
                           DocumentPlatformFacts facts) {
        this.repository = repository;
        this.draftRepository = draftRepository;
        this.publishedRepository = publishedRepository;
        this.integrityChecker = integrityChecker;
        this.guard = guard;
        this.facts = facts;
    }

    @Transactional
    public ImportOutcome execute(String orgKey, List<DocumentCommand> entries) {
        guard.requireOrganizationAccess(orgKey);

        List<String> errors = validate(entries);
        if (!errors.isEmpty()) {
            return new ImportOutcome(0, 0, errors);
        }

        // Authorize against stored roles, before an earlier entry can replace any access metadata.
        for (DocumentCommand entry : entries) {
            repository.findByOrgKeyAndSlug(orgKey, entry.properties().slug()).ifPresent(guard::requireEditor);
        }

        for (DocumentCommand entry : entries) {
            repository.findByOrgKeyAndSlug(orgKey, entry.properties().slug()).ifPresent(existing -> {
                List<DocumentValidationProblem> blocking = DocumentValidationProblem.blocking(
                        integrityChecker.checkPortChange(entry.properties().ports(),
                                draftRepository.findByOrgKeyAndDocumentId(orgKey, existing.getId()),
                                contentByLocale(entry),
                                publishedRepository.findByOrgKeyAndDocumentId(orgKey, existing.getId())));
                blocking.forEach(problem -> errors.add("Entry '" + entry.properties().slug() + "': " + problem));
            });
        }
        if (!errors.isEmpty()) {
            return new ImportOutcome(0, 0, errors);
        }

        int created = 0;
        int updated = 0;
        for (DocumentCommand entry : entries) {
            if (repository.findByOrgKeyAndSlug(orgKey, entry.properties().slug()).isPresent()) {
                applyToExisting(orgKey, entry);
                updated++;
            } else {
                createFrom(orgKey, entry);
                created++;
            }
        }
        return new ImportOutcome(created, updated, List.of());
    }

    private List<String> validate(List<DocumentCommand> entries) {
        List<String> errors = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            DocumentCommand entry = entries.get(i);
            if (entry.properties().slug() == null || entry.properties().slug().isBlank()) {
                errors.add("Entry " + i + " is missing 'slug' and was skipped.");
                continue;
            }
            if (entry.properties().sourceLocale() == null || entry.properties().sourceLocale().isBlank()) {
                errors.add("Entry '" + entry.properties().slug() + "' is missing 'sourceLocale'.");
                continue;
            }
            DocumentPorts ports = entry.properties().ports();
            contentByLocale(entry).forEach((locale, content) -> {
                List<DocumentValidationProblem> blocking =
                        DocumentValidationProblem.blocking(integrityChecker.check(ports, content));
                if (!blocking.isEmpty()) {
                    errors.add("Entry '" + entry.properties().slug() + "' locale '" + locale + "': " + blocking);
                }
            });
        }
        return errors;
    }

    private void createFrom(String orgKey, DocumentCommand entry) {
        String documentId = UUID.randomUUID().toString();
        Document document = entry.properties().create(orgKey, documentId, guard.currentPrincipal());
        repository.save(document);

        Map<String, DocumentContent> byLocale = contentByLocale(entry);
        byLocale.putIfAbsent(entry.properties().sourceLocale(), DocumentContent.empty());
        Long sourceRevision = 1L;
        byLocale.forEach((locale, content) -> draftRepository.save(new DocumentDraft(
                orgKey, documentId, locale, content,
                locale.equals(entry.properties().sourceLocale()) ? null : sourceRevision)));
        facts.created(document);
    }

    private void applyToExisting(String orgKey, DocumentCommand entry) {
        Document existing = repository.findByOrgKeyAndSlug(orgKey, entry.properties().slug()).orElseThrow();
        Map<String, DocumentDraft> draftsByLocale = new HashMap<>();
        for (DocumentDraft draft : draftRepository.findByOrgKeyAndDocumentId(orgKey, existing.getId())) {
            draftsByLocale.put(draft.getLocale(), draft);
        }
        List<PublishedDocument> snapshots = publishedRepository.findByOrgKeyAndDocumentId(orgKey, existing.getId());
        DocumentStatus before = DocumentPlatformFacts.defaultStatusOf(existing, draftsByLocale.values(), snapshots);

        entry.properties().applyTo(existing);
        repository.save(existing);

        List<DocumentDraft> written = new ArrayList<>(draftsByLocale.values());
        contentByLocale(entry).forEach((locale, content) -> {
            DocumentDraft draft = draftsByLocale.get(locale);
            if (draft == null) {
                DocumentDraft added = new DocumentDraft(orgKey, existing.getId(), locale, content,
                        locale.equals(entry.properties().sourceLocale()) ? null : sourceRevisionOf(draftsByLocale, entry));
                draftRepository.save(added);
                written.add(added);
            } else {
                draft.replaceBlocks(content.blocks());
                draftRepository.save(draft);
            }
        });
        // Re-importing over a published document leaves unpublished changes behind — a state change.
        facts.stateChanged(existing, before, DocumentPlatformFacts.defaultStatusOf(existing, written, snapshots));
    }

    private static Long sourceRevisionOf(Map<String, DocumentDraft> draftsByLocale, DocumentCommand entry) {
        DocumentDraft source = draftsByLocale.get(entry.properties().sourceLocale());
        return source == null ? null : source.getRevision();
    }

    private Map<String, DocumentContent> contentByLocale(DocumentCommand entry) {
        Map<String, DocumentContent> byLocale = new LinkedHashMap<>();
        for (DocumentTranslationCommand translation : entry.translations()) {
            byLocale.put(translation.locale(), translation.contentOrEmpty());
        }
        return byLocale;
    }
}
