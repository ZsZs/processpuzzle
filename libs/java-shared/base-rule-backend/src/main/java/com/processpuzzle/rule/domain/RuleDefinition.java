package com.processpuzzle.rule.domain;

import org.hibernate.annotations.ColumnDefault;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import com.processpuzzle.core.definition.Channel;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Persisted PPCL rule definition, identified by ({@code orgKey}, {@code id}) — see
 * {@link RuleDefinitionKey}. A rule id is unique only within its organization.
 *
 * <p>{@code extendsRuleId} is deliberately a plain string column, not a JPA
 * {@code @ManyToOne} self-reference: a YAML import batch may contain a child rule in the
 * same file as (or even before) its parent, so enforcing this at the database level via a
 * foreign key would make import ordering matter. Referential integrity (the parent exists,
 * no cycles) is validated at the service layer instead — see {@code RuleExtendsValidator}
 * and {@code ImportRules}. It resolves within {@code orgKey} only: cross-organization
 * inheritance would leak one tenant's expressions into another's evaluation chain.
 */
@Entity
@Table(name = "rule_definitions")
@IdClass(RuleDefinitionKey.class)
public class RuleDefinition {

    @Id
    @Column(name = "org_key", length = 63)
    private String orgKey;

    @Id
    @Column(length = 100)
    private String id;

    /**
     * Which of the rule's two rows this is: the {@code DRAFT} the designer edits, or the {@code RELEASED} one the
     * running application evaluates, which only a release of the organization replaces. The database default lets
     * {@code ddl-auto} add the column to a table that already has rows; {@code RuleChannelBackfill} then widens the
     * key and gives each row its released copy.
     */
    @Id
    @Enumerated(EnumType.STRING)
    @ColumnDefault("'DRAFT'")
    @Column(name = "channel", length = 16, nullable = false)
    private Channel channel = Channel.DRAFT;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false, length = 100)
    private String context;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(nullable = false)
    private String expression;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Severity severity = Severity.ERROR;

    @Column(length = 1000)
    private String message;

    @Column(name = "transloco_id", length = 200)
    private String translocoId;

    @Column(name = "extends_rule_id", length = 100)
    private String extendsRuleId;

    @Column(nullable = false)
    private boolean override = false;

    @Column(nullable = false)
    private boolean enabled = true;

    // Every key column is needed in the join: rule_id alone would collide across tenants, and a draft's fields
    // must not be its released row's.
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "rule_definition_fields", joinColumns = {
            @JoinColumn(name = "org_key", referencedColumnName = "org_key"),
            @JoinColumn(name = "rule_id", referencedColumnName = "id"),
            @JoinColumn(name = "channel", referencedColumnName = "channel")
    })
    @Column(name = "field_name", length = 100)
    private List<String> fields = new ArrayList<>();

    @Version
    private Long version;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected RuleDefinition() {
        // required by JPA
    }

    public RuleDefinition(String orgKey, String id, String name, String description, String context,
                           String expression, Severity severity, String message,
                           String translocoId, String extendsRuleId,
                           boolean override, boolean enabled) {
        this(orgKey, id, name, description, context, expression, severity, message,
                translocoId, extendsRuleId, override, enabled, null);
    }

    public RuleDefinition(String orgKey, String id, String name, String description, String context,
                           String expression, Severity severity, String message,
                           String translocoId, String extendsRuleId,
                           boolean override, boolean enabled, List<String> fields) {
        this.orgKey = orgKey;
        this.id = id;
        this.name = name;
        this.description = description;
        this.context = context;
        this.expression = expression;
        this.severity = severity;
        this.message = message;
        this.translocoId = translocoId;
        this.extendsRuleId = extendsRuleId;
        this.override = override;
        this.enabled = enabled;
        this.fields = fields == null ? new ArrayList<>() : new ArrayList<>(fields);
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

    /** A new released row with the draft's content: what a rule's first release creates. */
    public static RuleDefinition releasedCopyOf(RuleDefinition draft) {
        RuleDefinition released = new RuleDefinition(draft.orgKey, draft.id, draft.name, draft.description,
                draft.context, draft.expression, draft.severity, draft.message, draft.translocoId,
                draft.extendsRuleId, draft.override, draft.enabled, draft.fields);
        released.channel = Channel.RELEASED;
        return released;
    }

    /** Replaces this released row's content with the draft's, keeping its own version and timestamps. */
    public void releaseFrom(RuleDefinition draft) {
        this.name = draft.name;
        this.description = draft.description;
        this.context = draft.context;
        this.expression = draft.expression;
        this.severity = draft.severity;
        this.message = draft.message;
        this.translocoId = draft.translocoId;
        this.extendsRuleId = draft.extendsRuleId;
        this.override = draft.override;
        this.enabled = draft.enabled;
        this.fields = new ArrayList<>(draft.fields);
    }

    public Channel getChannel() {
        return channel;
    }

    /** The owning organization. Part of the identity, so there is deliberately no setter. */
    public String getOrgKey() {
        return orgKey;
    }

    public String getId() {
        return id;
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

    public String getContext() {
        return context;
    }

    public void setContext(String context) {
        this.context = context;
    }

    public String getExpression() {
        return expression;
    }

    public void setExpression(String expression) {
        this.expression = expression;
    }

    public Severity getSeverity() {
        return severity;
    }

    public void setSeverity(Severity severity) {
        this.severity = severity;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getTranslocoId() {
        return translocoId;
    }

    public void setTranslocoId(String translocoId) {
        this.translocoId = translocoId;
    }

    public String getExtendsRuleId() {
        return extendsRuleId;
    }

    public void setExtendsRuleId(String extendsRuleId) {
        this.extendsRuleId = extendsRuleId;
    }

    public boolean isOverride() {
        return override;
    }

    public void setOverride(boolean override) {
        this.override = override;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getFields() {
        return Collections.unmodifiableList(fields);
    }

    public void setFields(List<String> fields) {
        this.fields = fields == null ? new ArrayList<>() : new ArrayList<>(fields);
    }

    public Long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
