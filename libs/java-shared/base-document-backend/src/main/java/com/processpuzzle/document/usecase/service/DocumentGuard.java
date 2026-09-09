package com.processpuzzle.document.usecase.service;

import com.processpuzzle.document.domain.Document;
import com.processpuzzle.document.usecase.exception.DocumentAccessDeniedException;
import com.processpuzzle.document.usecase.port.DocumentAccessPolicy;
import com.processpuzzle.document.usecase.port.PermitAllDocumentAccessPolicy;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * The single place this feature consults {@link DocumentAccessPolicy}. Use cases depend on this
 * component, never on the port directly.
 *
 * <p>The policy is resolved with {@link ObjectProvider#getIfUnique}, not
 * {@code @ConditionalOnMissingBean} — same reasoning {@code OrganizationGuard} documents in
 * base-app: that condition is only reliable inside auto-configuration, which is processed after
 * user beans are registered, and the deploying application component-scans {@code com.processpuzzle}
 * anyway. {@code getIfUnique} is order-independent.
 *
 * <h2>The access ladder</h2>
 *
 * <p>Reading resolves in one order, and the order is the whole design:
 *
 * <ol>
 *   <li>{@code isPublic} — anyone, authenticated or not. Nothing else is consulted.
 *   <li>otherwise the principal must be authenticated and a member of the organization,
 *   <li>and must hold one of {@code readerRoles}, unless that list is empty, which means any member.
 * </ol>
 *
 * <p>Editing and publishing never take the first rung: {@code isPublic} widens who may <em>read</em>
 * published content and says nothing about who may write. A public document is still edited only by
 * its editors.
 */
@Component
public class DocumentGuard {

    private final DocumentAccessPolicy policy;

    public DocumentGuard(ObjectProvider<DocumentAccessPolicy> policyProvider) {
        this.policy = policyProvider.getIfUnique(PermitAllDocumentAccessPolicy::new);
    }

    /** Rejects the call with 403 when the principal is not a member of {@code orgKey}. */
    public void requireOrganizationAccess(String orgKey) {
        policy.requireAccess(orgKey);
    }

    /**
     * Whether the principal may read this document's <em>published</em> content.
     *
     * <p>Returns a boolean rather than throwing, because the public read path turns a denial into
     * 404 rather than 403 — telling an anonymous caller "this exists but you may not see it" leaks
     * exactly what a private document is for.
     */
    public boolean canRead(Document document) {
        if (document.isPublic()) {
            return true;
        }
        if (!policy.isAuthenticated()) {
            return false;
        }
        return holdsAnyOf(document.getRoles().readerRoles());
    }

    /** Rejects with 403 unless the principal may edit this document's metadata or draft content. */
    public void requireEditor(Document document) {
        requireOrganizationAccess(document.getOrgKey());
        if (!holdsAnyOf(document.getRoles().editorRoles())) {
            throw DocumentAccessDeniedException.lacksRole("edit", document.getId());
        }
    }

    /**
     * Rejects with 403 unless the principal may publish, unpublish or discard a draft.
     * {@code publisherRoles} falling back to {@code editorRoles} is decided by
     * {@code DocumentRoles.effectivePublisherRoles()}, so the fallback is defined once next to the
     * data rather than re-derived at each call site.
     */
    public void requirePublisher(Document document) {
        requireOrganizationAccess(document.getOrgKey());
        if (!holdsAnyOf(document.getRoles().effectivePublisherRoles())) {
            throw DocumentAccessDeniedException.lacksRole("publish", document.getId());
        }
    }

    /** Who is acting, for audit fields. {@code null} when there is no identity provider wired. */
    public String currentPrincipal() {
        return policy.currentPrincipal();
    }

    private boolean holdsAnyOf(List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return true;
        }
        return policy.hasAnyRole(roles);
    }
}
