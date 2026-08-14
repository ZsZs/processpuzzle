package com.processpuzzle.app.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.List;

/**
 * A lazily-loadable slice of an application: its own flat route list, its own transloco scope, its
 * own resource namespace. Identified by ({@code orgKey}, {@code key}).
 *
 * <p>An aggregate of its own rather than a subtree of {@link AppDefinition}, and that is the whole
 * point: it is what stops a large application from being one graph that must be loaded, locked and
 * published atomically, and it makes a module the unit of authoring permission and versioning.
 *
 * <p>A module does <b>not</b> mount modules — {@link ModuleMount} exists only on the application
 * side. Composition therefore recurses exactly one level, by construction rather than by convention.
 *
 * <p>What "lazy" means here is metadata: this definition, its routes, its translations, and the
 * documents and descriptors they name. It is not code splitting — widget components are bundled at
 * compile time and resolved through the frontend registry.
 */
@Entity
@Table(name = "app_modules")
@IdClass(ModuleDefinitionKey.class)
public class ModuleDefinition {

    @Id
    @Column(name = "org_key", length = 63)
    private String orgKey;

    @Id
    @Column(name = "module_key", length = 100)
    private String key;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "transloco_id", length = 200)
    private String translocoId;

    @Column(length = 1000)
    private String description;

    /** Defaults to {@link #key} when unset; see the contract's note on the explicit-alias requirement. */
    @Column(name = "transloco_scope", length = 100)
    private String translocoScope;

    @Lob
    @Column(name = "routes")
    @Convert(converter = RouteListConverter.class)
    private List<AppRoute> routes;

    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ModuleDefinition() {
        // required by JPA
    }

    public ModuleDefinition(String orgKey, String key, String name) {
        this.orgKey = orgKey;
        this.key = key;
        this.name = name;
        this.version = 1L;
    }

    public void markEdited() {
        this.version++;
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

    // region accessors
    public String getOrgKey() {
        return orgKey;
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

    public String getTranslocoId() {
        return translocoId;
    }

    public void setTranslocoId(String translocoId) {
        this.translocoId = translocoId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    /** The authored scope, or {@link #key} when none was authored. */
    public String getTranslocoScope() {
        return translocoScope == null || translocoScope.isBlank() ? key : translocoScope;
    }

    public void setTranslocoScope(String translocoScope) {
        this.translocoScope = translocoScope;
    }

    public List<AppRoute> getRoutes() {
        return routes == null ? List.of() : routes;
    }

    public void setRoutes(List<AppRoute> routes) {
        this.routes = routes;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
    // endregion
}
