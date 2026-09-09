package com.processpuzzle.widget.adapter.inbound.dto;

import com.processpuzzle.widget.model.WidgetDefinitionInput;

import java.util.List;

/**
 * Root of a bundled {@code default-widgets/<orgKey>-widgets.yaml} file, read by
 * {@link com.processpuzzle.widget.adapter.inbound.DefaultWidgetLoader} on startup.
 *
 * <p>Deliberately thinner than base-app's {@code DefaultAppsDocument}: that one carries an
 * {@code organization} block, because an app definition cannot exist without a provisioned tenant and
 * base-app owns provisioning. A widget definition is keyed by {@code (orgKey, key)} and this module
 * knows nothing of an organization registry — see the {@code allowedDependencies} in
 * {@code com.processpuzzle.widget.package-info} — so there is nothing here to provision and no block
 * to declare it with. The organization key comes from the file name, exactly as it does there.
 *
 * <p>Entries are the generated {@code WidgetDefinitionInput}, so this file is precisely the payload of
 * {@code POST /organizations/{orgKey}/widget-definitions} repeated, and passes through the same
 * mapper and the same validation as a definition a designer saves.
 *
 * @param widgetDefinitions the widget types to create in that organization
 */
public record DefaultWidgetsDocument(List<WidgetDefinitionInput> widgetDefinitions) {

    public DefaultWidgetsDocument {
        widgetDefinitions = widgetDefinitions == null ? List.of() : List.copyOf(widgetDefinitions);
    }
}
