package com.processpuzzle.document.adapter.inbound;

import com.processpuzzle.document.domain.ContentBook;
import com.processpuzzle.document.domain.DocumentRoles;
import com.processpuzzle.document.model.ContentBookInput;
import com.processpuzzle.document.model.ContentBookNode;
import com.processpuzzle.document.model.PageOfContentBook;
import com.processpuzzle.document.usecase.ContentBookCommand;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/** Field-for-field between {@link ContentBook} and the generated model, as {@link DocumentMapper} is for documents. */
@Component
public class ContentBookMapper {

    // ── model -> domain ─────────────────────────────────────────

    public ContentBookCommand toCommand(ContentBookInput input) {
        return new ContentBookCommand(input.getSlug(), input.getTitle(), input.getDescription(),
                Boolean.TRUE.equals(input.getIsPublic()),
                new DocumentRoles(input.getReaderRoles(), input.getEditorRoles(), List.of()),
                input.getNodes() == null ? null : toNodes(input.getNodes()));
    }

    public List<com.processpuzzle.document.domain.ContentBookNode> toNodes(List<ContentBookNode> nodes) {
        return nodes.stream()
                .map(node -> new com.processpuzzle.document.domain.ContentBookNode(
                        node.getId(),
                        node.getDocumentId() == null ? null : node.getDocumentId().toString(),
                        node.getParentId(),
                        node.getOrderIndex() == null ? 0 : node.getOrderIndex()))
                .toList();
    }

    // ── domain -> model ─────────────────────────────────────────

    public com.processpuzzle.document.model.ContentBook toModel(ContentBook book) {
        return new com.processpuzzle.document.model.ContentBook()
                .id(UUID.fromString(book.getId()))
                .orgKey(book.getOrgKey())
                .slug(book.getSlug())
                .title(book.getTitle())
                .description(book.getDescription())
                .isPublic(book.isPublic())
                .readerRoles(book.getRoles().readerRoles())
                .editorRoles(book.getRoles().editorRoles())
                .nodes(book.getNodes().nodes().stream().map(ContentBookMapper::toModel).toList())
                .lockVersion(book.getLockVersion())
                .createdBy(book.getCreatedBy())
                .createdAt(toOffsetDateTime(book.getCreatedAt()))
                .updatedAt(toOffsetDateTime(book.getUpdatedAt()));
    }

    public PageOfContentBook toModel(Page<ContentBook> page) {
        return new PageOfContentBook()
                .content(page.getContent().stream().map(this::toModel).toList())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .number(page.getNumber())
                .size(page.getSize());
    }

    private static ContentBookNode toModel(com.processpuzzle.document.domain.ContentBookNode node) {
        return new ContentBookNode()
                .id(node.id())
                .documentId(UUID.fromString(node.documentId()))
                .parentId(node.parentId())
                .orderIndex(node.orderIndex());
    }

    private static OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }
}
