package com.processpuzzle.starter.adapter.inbound;

import com.processpuzzle.core.definition.ImportedItem;
import com.processpuzzle.starter.model.DefinitionKind;
import com.processpuzzle.starter.model.ImportError;
import com.processpuzzle.starter.model.ImportReportItem;
import com.processpuzzle.starter.model.ImportSummary;
import com.processpuzzle.starter.model.InstalledStarter;
import com.processpuzzle.starter.usecase.ImportReport;
import com.processpuzzle.starter.usecase.ListInstalledStarters;
import java.time.ZoneOffset;
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
                .summary(new ImportSummary().created((int) report.created()).updated((int) report.updated()));
        report.items().forEach(item -> model.addItemsItem(new ImportReportItem()
                .kind(DefinitionKind.fromValue(item.kind()))
                .key(item.key())
                .action(item.action() == ImportedItem.Action.CREATE
                        ? ImportReportItem.ActionEnum.CREATE
                        : ImportReportItem.ActionEnum.UPDATE)));
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
}
