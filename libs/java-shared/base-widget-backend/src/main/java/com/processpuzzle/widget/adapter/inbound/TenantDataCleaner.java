package com.processpuzzle.widget.adapter.inbound;

import com.processpuzzle.shared.event.OrganizationDeletedEvent;
import com.processpuzzle.widget.domain.WidgetDefinitionRepository;
import com.processpuzzle.widget.i18n.WidgetTranslationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Deletes this feature's rows of an erased tenant: its widget definitions and its translations.
 *
 * <p><b>{@code BEFORE_COMMIT}</b>, for the reason base-app's cleaner gives: these deletes join the
 * erasing transaction, so a tenant is either erased in every feature or in none. An
 * {@code AFTER_COMMIT} listener's writes would be discarded without an error.
 *
 * <p><b>Bulk statements, no child tables.</b> A definition's props schema and ports are columns of
 * its own row, so each table goes in one statement and the order does not matter. The widget
 * instances that refer to a definition by key belong to base-app and base-document, which erase
 * their own.
 *
 * <p><b>Nothing after commit.</b> Nothing of this feature lives outside the database; the widget
 * implementations are frontend components, not tenant data.
 *
 * <p>Named, because base-app and base-rule declare a {@code TenantDataCleaner} too, and an
 * application that assembles several features would otherwise have two beans of one name.
 */
@Component("widgetTenantDataCleaner")
public class TenantDataCleaner {

    private static final Logger LOG = LoggerFactory.getLogger(TenantDataCleaner.class);

    private final WidgetDefinitionRepository widgets;
    private final WidgetTranslationRepository translations;

    public TenantDataCleaner(WidgetDefinitionRepository widgets, WidgetTranslationRepository translations) {
        this.widgets = widgets;
        this.translations = translations;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onOrganizationDeleted(OrganizationDeletedEvent event) {
        String orgKey = event.orgKey();

        int erasedWidgets = widgets.eraseByOrgKey(orgKey);
        int erasedTranslations = translations.eraseByOrgKey(orgKey);

        LOG.info("Organization '{}' erased: {} widget definition(s) and {} translation bundle(s) deleted.", orgKey,
                erasedWidgets, erasedTranslations);
    }
}
