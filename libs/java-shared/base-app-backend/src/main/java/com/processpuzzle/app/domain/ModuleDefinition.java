package com.processpuzzle.app.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import com.processpuzzle.core.definition.Channel;
import java.time.Instant;
import java.util.List;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A lazily-loadable slice of an application: its own flat route list, its own transloco scope, its
 * own resource namespace. Identified by ({@code orgKey}, {@code key}).
 *
 * <p>An aggregate of its own rather than a subtree of {@link AppDefinition}, and that is the whole
 * point: it is what stops a large application from being one graph that must be loaded, locked and
 * published atomically, and it makes a module the unit of authoring permission and versioning.
 *
 * <p>Stored twice, once per {@link Channel}: the designer edits the {@code DRAFT}, and the running
 * application reads the {@code RELEASED} row, which only a release of the organization replaces — see
 * {@link #releaseFrom}. Until then a module's edits are invisible to end users, as an app's are.
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

    /**
     * The database default lets {@code ddl-auto} add the column to a table that already has rows; see
     * {@code ChannelBackfill}. Declared through {@link ColumnDefault} rather than a {@code columnDefinition}:
     * Hibernate compares a {@code columnDefinition} with the column's type as text, never finds them equal, and
     * re-issues an {@code alter column} on every start.
     */
    @Id
    @Enumerated(EnumType.STRING)
    @ColumnDefault("'DRAFT'")
    @Column(name = "channel", length = 16, nullable = false)
    private Channel channel = Channel.DRAFT;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "transloco_id", length = 200)
    private String translocoId;

    @Column(length = 1000)
    private String description;

    /** Defaults to {@link #key} when unset; see the contract's note on the explicit-alias requirement. */
    @Column(name = "transloco_scope", length = 100)
    private String translocoScope;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
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
        this(orgKey, key, name, Channel.DRAFT);
    }

    public ModuleDefinition(String orgKey, String key, String name, Channel channel) {
        this.orgKey = orgKey;
        this.key = key;
        this.name = name;
        this.channel = channel;
        this.version = 1L;
    }

    /** A released row carrying {@code draft}'s content, for an organization releasing the module for the first time. */
    public static ModuleDefinition releasedCopyOf(ModuleDefinition draft) {
        ModuleDefinition released = new ModuleDefinition(draft.orgKey, draft.key, draft.name, Channel.RELEASED);
        released.releaseFrom(draft);
        return released;
    }

    /** Makes this released row carry {@code draft}'s content, including its version. */
    public void releaseFrom(ModuleDefinition draft) {
        this.name = draft.name;
        this.translocoId = draft.translocoId;
        this.description = draft.description;
        this.translocoScope = draft.translocoScope;
        this.routes = draft.routes == null ? null : List.copyOf(draft.routes);
        this.version = draft.version;
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

    public Channel getChannel() {
        return channel;
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
