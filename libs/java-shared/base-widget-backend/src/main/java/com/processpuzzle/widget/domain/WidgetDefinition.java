package com.processpuzzle.widget.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * The description of a widget <em>type</em>: what it is called, what props it takes, and what ports
 * it offers. Identified by ({@code orgKey}, {@code key}) — see {@link WidgetDefinitionKey}.
 *
 * <h2>What is a column and what is JSON</h2>
 *
 * <p>{@code name}, {@code category} and friends are real columns because {@code listWidgetDefinitions}
 * filters and sorts on them via RSQL. {@code propsSchema} and the two port lists are opaque JSON in
 * long text: nothing queries inside them, and the backend never interprets them. Same division
 * base-app makes between its header fields and its graph.
 *
 * <h2>Why {@code version} is not {@code @Version}</h2>
 *
 * <p>Status is defined as PUBLISHED exactly when {@code publishedVersion == version}, so publishing
 * must leave the counter alone. Hibernate increments a {@code @Version} field on any dirty flush,
 * and publishing dirties the row — a managed version would land at {@code publishedVersion + 1} the
 * instant a publish committed, reporting unpublished edits on every freshly published widget.
 * {@code version} is therefore a plain column, bumped explicitly on update. Identical reasoning to
 * {@code AppDefinition.revision}; see the note there.
 *
 * <h2>{@code propsSchema} may be null, and that is meaningful</h2>
 *
 * <p>Null means "props are unconstrained" — the honest state for a widget type nobody has described
 * yet. It is not the same as an empty schema, which would assert the widget takes no props at all.
 * The contract keeps the field nullable for exactly this reason, and the column follows.
 */
@Entity
@Table(name = "widget_definitions")
@IdClass(WidgetDefinitionKey.class)
public class WidgetDefinition {

    @Id
    @Column(name = "org_key", length = 63)
    private String orgKey;

    @Id
    @Column(name = "widget_key", length = 64)
    private String key;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "transloco_id", length = 200)
    private String translocoId;

    @Column(length = 1000)
    private String description;

    @Column(length = 100)
    private String category;

    @Column(length = 100)
    private String icon;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "props_schema")
    @Convert(converter = JsonMapConverter.class)
    private java.util.Map<String, Object> propsSchema;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "input_ports")
    @Convert(converter = PortListConverter.class)
    private java.util.List<Port> inputPorts;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "output_ports")
    @Convert(converter = PortListConverter.class)
    private java.util.List<Port> outputPorts;

    @Column(nullable = false)
    private long version;

    @Column(name = "published_version")
    private Long publishedVersion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected WidgetDefinition() {
        // required by JPA
    }

    public WidgetDefinition(String orgKey, String key, String name) {
        this.orgKey = orgKey;
        this.key = key;
        this.name = name;
        this.version = 1L;
    }

    /**
     * Derived rather than stored — see {@link WidgetDefinitionStatus}. A definition that has never
     * been published has a null {@code publishedVersion} and is therefore DRAFT.
     */
    public WidgetDefinitionStatus status() {
        return publishedVersion != null && publishedVersion == version ? WidgetDefinitionStatus.PUBLISHED : WidgetDefinitionStatus.DRAFT;
    }

    /** Bumps {@link #version}. Called by the update use case, never by a publish. */
    public void markEdited() {
        this.version++;
    }

    /** Promotes the current {@link #version} to {@link #publishedVersion}, leaving the counter alone. */
    public void publish() {
        this.publishedVersion = this.version;
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

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public java.util.Map<String, Object> getPropsSchema() {
        return propsSchema;
    }

    public void setPropsSchema(java.util.Map<String, Object> propsSchema) {
        this.propsSchema = propsSchema;
    }

    public java.util.List<Port> getInputPorts() {
        return inputPorts;
    }

    public void setInputPorts(java.util.List<Port> inputPorts) {
        this.inputPorts = inputPorts;
    }

    public java.util.List<Port> getOutputPorts() {
        return outputPorts;
    }

    public void setOutputPorts(java.util.List<Port> outputPorts) {
        this.outputPorts = outputPorts;
    }

    public long getVersion() {
        return version;
    }

    public Long getPublishedVersion() {
        return publishedVersion;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
    // endregion
}
