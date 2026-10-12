package com.processpuzzle.release.usecase;

import com.processpuzzle.core.definition.PendingChange;
import com.processpuzzle.release.domain.OrgReleaseContent;
import com.processpuzzle.release.domain.OrgReleaseContentRepository;
import com.processpuzzle.release.domain.OrgReleaseItem;
import com.processpuzzle.release.domain.OrgReleaseItemRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What an organization ran at a given release: every definition it held then, with the content released for it.
 *
 * <p>A release records only what it changed, so the state at release {@code N} is the newest item of each
 * definition up to {@code N}, minus those whose newest item is a removal. That is what this rebuilds. It is the
 * ground a diff between two releases, a return to an earlier one, or running work bound to its release stands on.
 *
 * <p>No guard here: this answers other use cases, not a request. Whatever exposes it checks the caller first.
 */
@Service
@Transactional(readOnly = true)
public class ReleaseHistory {

    private final OrgReleaseItemRepository itemRepository;
    private final OrgReleaseContentRepository contentRepository;

    ReleaseHistory(OrgReleaseItemRepository itemRepository, OrgReleaseContentRepository contentRepository) {
        this.itemRepository = itemRepository;
        this.contentRepository = contentRepository;
    }

    /**
     * @return the definitions held at {@code releaseNumber}, in release order, each with its released content;
     *         empty before the organization's first release
     */
    public List<ReleasedDefinition> contentAt(String orgKey, long releaseNumber) {
        Map<String, OrgReleaseItem> newest = new LinkedHashMap<>();
        for (OrgReleaseItem item : itemRepository
                .findByOrgKeyAndReleaseNumberLessThanEqualOrderByReleaseNumberAscSequenceNoAsc(orgKey, releaseNumber)) {
            String identity = item.getKind() + '\u0000' + item.getDefinitionKey();
            newest.remove(identity);
            if (item.getChange() != PendingChange.Change.REMOVED) newest.put(identity, item);
        }
        Map<UUID, String> contents = contentRepository.findAllById(newest.values().stream().map(OrgReleaseItem::getId).toList())
                .stream()
                .collect(Collectors.toMap(OrgReleaseContent::getItemId, OrgReleaseContent::getContent));
        return newest.values().stream()
                .map(item -> new ReleasedDefinition(item.getKind(), item.getDefinitionKey(), item.getReleaseNumber(),
                        contents.get(item.getId())))
                .toList();
    }

    /**
     * One definition as it stood at a release.
     *
     * @param releasedIn the release that last changed it, at or before the one asked about
     * @param content    its canonical JSON; null only for an item recorded before content was kept
     */
    public record ReleasedDefinition(String kind, String key, long releasedIn, String content) {
    }
}
