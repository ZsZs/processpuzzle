package com.processpuzzle.core.i18n;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.MappedSuperclass;
import java.util.LinkedHashMap;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One Transloco bundle as it is stored: the whole message map of a {@code (orgKey, scope, locale)}
 * triple, in the exact shape the frontend's {@code assets/i18n/<scope>/<lang>.json} has.
 *
 * <p>A {@code @MappedSuperclass} rather than an {@code @Entity} on purpose. Each feature library owns and
 * serves its own translations — they are meant to become separate services with separate databases — so
 * every library declares its own concrete subclass against its own table. Core contributes the shape, not
 * the table: a single shared {@code translations} table would be exactly the coupling the split is meant
 * to avoid, and seven entities mapping one table would not start anyway.
 *
 * <p>Core therefore ships no concrete entity, which is also what keeps {@code processpuzzle-store} — the
 * one consumer of core that declares no JPA, core's own being {@code <optional>} — loading as it always
 * has. Nothing there references this class, so nothing there loads it.
 *
 * <p>The messages column is genuine {@code jsonb}, following {@code EntityObject.payload} in base-entity
 * rather than base-state's {@code StatesConverter}, whose comment claims H2 rejects the type name. It does
 * not under {@code MODE=PostgreSQL}, which is what the backend and every test run against, and base-entity
 * has been storing jsonb that way in CI throughout.
 */
@MappedSuperclass
@IdClass(TranslationBundleKey.class)
public abstract class AbstractTranslationBundle {

    /**
     * Scope of the application's own root bundle — the one served as {@code assets/i18n/<lang>.json} with
     * no scope segment. A sentinel rather than {@code ""} or {@code null}: a component-key column cannot
     * be null, and an empty string is invisible when reading the table by hand.
     */
    public static final String ROOT_SCOPE = "_root";

    @Id
    @Column(name = "org_key", nullable = false, length = 63)
    private String orgKey;

    @Id
    @Column(nullable = false, length = 64)
    private String scope;

    @Id
    @Column(nullable = false, length = 16)
    private String locale;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> messages = new LinkedHashMap<>();

    protected AbstractTranslationBundle() {
        // required by JPA
    }

    protected AbstractTranslationBundle(String orgKey, String scope, String locale, Map<String, Object> messages) {
        this.orgKey = orgKey;
        this.scope = scope;
        this.locale = locale;
        this.messages = messages == null ? new LinkedHashMap<>() : new LinkedHashMap<>(messages);
    }

    public String getOrgKey() {
        return orgKey;
    }

    public void setOrgKey(String orgKey) {
        this.orgKey = orgKey;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public String getLocale() {
        return locale;
    }

    public void setLocale(String locale) {
        this.locale = locale;
    }

    public Map<String, Object> getMessages() {
        return messages;
    }

    public void setMessages(Map<String, Object> messages) {
        this.messages = messages == null ? new LinkedHashMap<>() : messages;
    }
}
