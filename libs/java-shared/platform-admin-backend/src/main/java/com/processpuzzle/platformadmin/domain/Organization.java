package com.processpuzzle.platformadmin.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A tenant. Its {@code key} is the public URL segment of the tenant's application
 * ({@code https://processpuzzle.com/{key}}) and the scope of every piece of metadata belonging
 * to it, which is why it is immutable once claimed — renaming would orphan every id.
 *
 * <p>There is deliberately no JPA association to anything a tenant owns, and there could not be:
 * the things scoped by an organization live in other Modulith modules and, for base-entity, in other
 * databases. {@code DeleteOrganization} therefore cascades by publishing
 * {@link com.processpuzzle.shared.event.OrganizationDeletedEvent} and letting each
 * feature delete its own rows.
 *
 * <p>{@code @Table(name = "organizations")} is the same table base-app wrote to before the aggregate
 * moved here. Kept deliberately: with {@code ddl-auto: update} and no migration scripts in the repo,
 * a rename would have silently orphaned every existing row.
 */
@Entity
@Table(name = "organizations")
public class Organization {

    @Id
    @Column(name = "org_key", length = 63)
    private String key;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(name = "contact_email", length = 320)
    private String contactEmail;

    @Column(name = "default_locale", length = 35)
    private String defaultLocale;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrganizationStatus status = OrganizationStatus.ACTIVE;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected Organization() {
        // required by JPA
    }

    public Organization(String key, String name, String description, String contactEmail,
                        String defaultLocale, OrganizationStatus status) {
        this.key = key;
        this.name = name;
        this.description = description;
        this.contactEmail = contactEmail;
        this.defaultLocale = defaultLocale;
        this.status = status == null ? OrganizationStatus.ACTIVE : status;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public String getKey() {
        return key;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public void setContactEmail(String contactEmail) {
        this.contactEmail = contactEmail;
    }

    public String getDefaultLocale() {
        return defaultLocale;
    }

    public void setDefaultLocale(String defaultLocale) {
        this.defaultLocale = defaultLocale;
    }

    public OrganizationStatus getStatus() {
        return status;
    }

    public void setStatus(OrganizationStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
