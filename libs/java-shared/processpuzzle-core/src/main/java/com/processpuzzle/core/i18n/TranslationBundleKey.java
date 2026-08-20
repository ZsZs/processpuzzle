package com.processpuzzle.core.i18n;

import java.io.Serializable;
import java.util.Objects;

/**
 * Composite primary key of a translation bundle: one message map per organization, transloco scope and
 * locale. All three are needed — a scope is unique only within a tenant, and a scope has one bundle per
 * language.
 *
 * <p>Intentionally a plain mutable class and not a record, for the reason {@code AppDefinitionKey} in
 * base-app records: JPA requires an {@code @IdClass} to be public, {@link Serializable}, and
 * instantiable through a public no-arg constructor, which a record cannot provide. Field names and types
 * must match the entity's {@code @Id} fields exactly.
 */
public class TranslationBundleKey implements Serializable {

    private static final long serialVersionUID = 1L;

    private String orgKey;
    private String scope;
    private String locale;

    public TranslationBundleKey() {
        // required by JPA
    }

    public TranslationBundleKey(String orgKey, String scope, String locale) {
        this.orgKey = orgKey;
        this.scope = scope;
        this.locale = locale;
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

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TranslationBundleKey that)) {
            return false;
        }
        return Objects.equals(orgKey, that.orgKey)
                && Objects.equals(scope, that.scope)
                && Objects.equals(locale, that.locale);
    }

    @Override
    public int hashCode() {
        return Objects.hash(orgKey, scope, locale);
    }

    @Override
    public String toString() {
        return orgKey + "/" + scope + "/" + locale;
    }
}
