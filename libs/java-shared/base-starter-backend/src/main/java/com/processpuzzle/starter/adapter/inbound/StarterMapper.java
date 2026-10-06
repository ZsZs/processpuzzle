package com.processpuzzle.starter.adapter.inbound;

import com.processpuzzle.starter.model.CatalogStarter;
import com.processpuzzle.starter.model.CatalogStarterVersion;
import com.processpuzzle.starter.model.DefinitionKind;
import com.processpuzzle.starter.model.ImportError;
import com.processpuzzle.starter.model.ImportReportItem;
import com.processpuzzle.starter.model.ImportSummary;
import com.processpuzzle.starter.model.InstalledStarter;
import com.processpuzzle.starter.registry.StarterCatalog;
import com.processpuzzle.starter.usecase.ImportReport;
import com.processpuzzle.starter.usecase.ListInstalledStarters;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import org.springframework.stereotype.Component;

/** Maps the use cases' results onto base-starter-api.yaml's models. */
@Component
public class StarterMapper {

    public com.processpuzzle.starter.model.ImportReport toModel(ImportReport report) {
        com.processpuzzle.starter.model.ImportReport model = new com.processpuzzle.starter.model.ImportReport()
                .dryRun(report.dryRun())
                .status(switch (report.status()) {
                    case APPLIED -> com.processpuzzle.starter.model.ImportReport.StatusEnum.APPLIED;
                    case WOULD_APPLY -> com.processpuzzle.starter.model.ImportReport.StatusEnum.WOULD_APPLY;
                    case REJECTED -> com.processpuzzle.starter.model.ImportReport.StatusEnum.REJECTED;
                })
                .starterId(report.starterId())
                .version(report.version())
                .summary(new ImportSummary()
                        .created((int) report.created())
                        .updated((int) report.updated())
                        .deleted((int) report.deleted()));
        report.items().forEach(item -> model.addItemsItem(new ImportReportItem()
                .kind(DefinitionKind.fromValue(item.kind()))
                .key(item.key())
                .action(switch (item.action()) {
                    case CREATE -> ImportReportItem.ActionEnum.CREATE;
                    case UPDATE -> ImportReportItem.ActionEnum.UPDATE;
                    case DELETE -> ImportReportItem.ActionEnum.DELETE;
                })));
        report.errors().forEach(error -> model.addErrorsItem(new ImportError().file(error.file()).message(error.message())));
        return model;
    }

    public InstalledStarter toModel(ListInstalledStarters.View view) {
        return new InstalledStarter()
                .starterId(view.starterId())
                .version(view.version())
                .installedAt(view.installedAt().atOffset(ZoneOffset.UTC))
                .installedBy(view.installedBy())
                .customizedDefinitions(view.customizedDefinitions());
    }

    public CatalogStarter toModel(StarterCatalog.Starter starter) {
        CatalogStarter model = new CatalogStarter()
                .id(starter.id())
                .name(starter.name() == null ? starter.id() : starter.name())
                .description(starter.description())
                .author(starter.author())
                .license(starter.license())
                .category(starter.category())
                .tags(starter.tags());
        starter.versions().forEach(version -> model.addVersionsItem(new CatalogStarterVersion()
                .version(version.version())
                .publishedAt(publishedAt(version.publishedAt()))
                .status(StarterCatalog.Version.DEPRECATED.equals(version.effectiveStatus())
                        ? CatalogStarterVersion.StatusEnum.DEPRECATED
                        : CatalogStarterVersion.StatusEnum.PUBLISHED)));
        return model;
    }

    /** CI writes an instant; a bare date, as early hand-written catalogs have, reads as its UTC midnight. */
    static OffsetDateTime publishedAt(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value).atOffset(ZoneOffset.UTC);
        } catch (DateTimeParseException notAnInstant) {
            try {
                return LocalDate.parse(value).atStartOfDay().atOffset(ZoneOffset.UTC);
            } catch (DateTimeParseException notADate) {
                return null;
            }
        }
    }
}
