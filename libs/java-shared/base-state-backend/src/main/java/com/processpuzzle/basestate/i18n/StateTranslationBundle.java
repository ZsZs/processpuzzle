package com.processpuzzle.basestate.i18n;

import com.processpuzzle.core.i18n.AbstractTranslationBundle;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.Map;

/**
 * This feature's own translation bundles, in its own table.
 *
 * <p>One table per feature rather than one shared {@code translations} table, because each feature is
 * meant to become a service with a database of its own — a shared table would be exactly the coupling
 * that split has to undo. In the monolith it is also what lets the seven coexist: seven entities mapping
 * one table would not start.
 *
 * <p>The shape — the {@code (orgKey, scope, locale)} key and the jsonb message map — comes from
 * {@link AbstractTranslationBundle} in processpuzzle-core, which is a {@code @MappedSuperclass} and so
 * contributes no table of its own.
 */
@Entity
@Table(name = "state_translations")
public class StateTranslationBundle extends AbstractTranslationBundle {

    protected StateTranslationBundle() {
        // required by JPA
    }

    public StateTranslationBundle(String orgKey, String scope, String locale, Map<String, Object> messages) {
        super(orgKey, scope, locale, messages);
    }
}
