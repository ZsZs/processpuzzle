package com.processpuzzle.document.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/** Every finder is scoped by {@code orgKey}, for the reason {@link DocumentRepository} gives. */
public interface ContentBookRepository
        extends JpaRepository<ContentBook, ContentBookKey>, JpaSpecificationExecutor<ContentBook> {

    Optional<ContentBook> findByOrgKeyAndId(String orgKey, String id);

    Optional<ContentBook> findByOrgKeyAndSlug(String orgKey, String slug);

    boolean existsByOrgKeyAndSlug(String orgKey, String slug);

    List<ContentBook> findByOrgKey(String orgKey);

    /**
     * The books of a tenant that show {@code documentId}. Filtered in memory rather than in SQL: the
     * tree is a JSON column, and an organization holds a handful of books, not thousands.
     */
    default List<ContentBook> findByOrgKeyReferencing(String orgKey, String documentId) {
        return findByOrgKey(orgKey).stream().filter(book -> book.getNodes().references(documentId)).toList();
    }

    /** Every book of a tenant in one statement, for erasing the tenant. See {@link DocumentRepository#eraseByOrgKey}. */
    @Modifying(flushAutomatically = true)
    @Query("delete from ContentBook b where b.orgKey = :orgKey")
    int eraseByOrgKey(@Param("orgKey") String orgKey);
}
