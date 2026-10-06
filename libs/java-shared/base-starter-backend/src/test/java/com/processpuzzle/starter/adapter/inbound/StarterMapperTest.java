package com.processpuzzle.starter.adapter.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import com.processpuzzle.starter.model.CatalogStarterVersion;
import com.processpuzzle.starter.model.DefinitionKind;
import com.processpuzzle.starter.model.ImportReportItem;
import com.processpuzzle.starter.registry.StarterCatalog;
import com.processpuzzle.starter.usecase.ImportReport;
import com.processpuzzle.starter.usecase.ListInstalledStarters;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class StarterMapperTest {

    private final StarterMapper mapper = new StarterMapper();

    @ParameterizedTest
    @CsvSource({"APPLIED, applied, false", "WOULD_APPLY, would-apply, true", "REJECTED, rejected, false"})
    void mapsReportStatusesAndIdentity(ImportReport.Status status, String wireStatus, boolean dryRun) {
        ImportReport report = new ImportReport(dryRun, status, "inventory", "1.0.0", null, null);

        var model = mapper.toModel(report);

        assertThat(model.getStatus().getValue()).isEqualTo(wireStatus);
        assertThat(model.getDryRun()).isEqualTo(dryRun);
        assertThat(model.getStarterId()).isEqualTo("inventory");
        assertThat(model.getVersion()).isEqualTo("1.0.0");
        assertThat(model.getSummary().getCreated()).isZero();
        assertThat(model.getSummary().getUpdated()).isZero();
        assertThat(model.getSummary().getDeleted()).isZero();
    }

    @Test
    void mapsEveryActionAndErrorAndCountsTheSummary() {
        ImportReport report = new ImportReport(true, ImportReport.Status.WOULD_APPLY, "inventory", "1.0.0",
                List.of(new ImportReport.Item("entity", "item", ImportReport.Action.CREATE),
                        new ImportReport.Item("entity", "location", ImportReport.Action.CREATE),
                        new ImportReport.Item("rule", "positive-qty", ImportReport.Action.UPDATE),
                        new ImportReport.Item("state", "retired", ImportReport.Action.DELETE)),
                List.of(new ImportReport.Error("rules/item.yaml", "Invalid expression"),
                        new ImportReport.Error(null, "Import refused")));

        var model = mapper.toModel(report);

        assertThat(model.getSummary().getCreated()).isEqualTo(2);
        assertThat(model.getSummary().getUpdated()).isEqualTo(1);
        assertThat(model.getSummary().getDeleted()).isEqualTo(1);
        assertThat(model.getItems()).extracting(ImportReportItem::getKind, ImportReportItem::getKey, ImportReportItem::getAction)
                .containsExactly(
                        tuple(DefinitionKind.ENTITY, "item", ImportReportItem.ActionEnum.CREATE),
                        tuple(DefinitionKind.ENTITY, "location", ImportReportItem.ActionEnum.CREATE),
                        tuple(DefinitionKind.RULE, "positive-qty", ImportReportItem.ActionEnum.UPDATE),
                        tuple(DefinitionKind.STATE, "retired", ImportReportItem.ActionEnum.DELETE));
        assertThat(model.getErrors()).extracting(
                        com.processpuzzle.starter.model.ImportError::getFile,
                        com.processpuzzle.starter.model.ImportError::getMessage)
                .containsExactly(tuple("rules/item.yaml", "Invalid expression"), tuple(null, "Import refused"));
    }

    @Test
    void mapsCatalogMetadataAndVersionStatuses() {
        StarterCatalog.Starter starter = new StarterCatalog.Starter("inventory", "Inventory", "Stock management",
                "ProcessPuzzle", "Apache-2.0", "Operations", List.of("stock"), "boxes",
                List.of(new StarterCatalog.Version("1.1.0", "2026-10-06T10:00:00Z", null, 1, "abc", "new.zip"),
                        new StarterCatalog.Version("1.0.0", "2026-10-01", "deprecated", 1, "def", "old.zip")));

        var model = mapper.toModel(starter);

        assertThat(model.getId()).isEqualTo("inventory");
        assertThat(model.getName()).isEqualTo("Inventory");
        assertThat(model.getDescription()).isEqualTo("Stock management");
        assertThat(model.getAuthor()).isEqualTo("ProcessPuzzle");
        assertThat(model.getLicense()).isEqualTo("Apache-2.0");
        assertThat(model.getCategory()).isEqualTo("Operations");
        assertThat(model.getTags()).containsExactly("stock");
        assertThat(model.getVersions()).extracting(
                        CatalogStarterVersion::getVersion, CatalogStarterVersion::getStatus, CatalogStarterVersion::getPublishedAt)
                .containsExactly(
                        tuple("1.1.0", CatalogStarterVersion.StatusEnum.PUBLISHED, OffsetDateTime.parse("2026-10-06T10:00:00Z")),
                        tuple("1.0.0", CatalogStarterVersion.StatusEnum.DEPRECATED, OffsetDateTime.parse("2026-10-01T00:00:00Z")));
    }

    @Test
    void usesTheIdWhenTheCatalogHasNoName() {
        StarterCatalog.Starter starter = new StarterCatalog.Starter("inventory", null, null, null, null,
                null, null, null, null);

        assertThat(mapper.toModel(starter).getName()).isEqualTo("inventory");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "not a date", "2026-02-30"})
    void missingOrMalformedPublicationDatesRemainAbsent(String value) {
        assertThat(StarterMapper.publishedAt(value)).isNull();
    }

    @Test
    void mapsInstalledStarterProvenanceWithAUtcTimestamp() {
        ListInstalledStarters.View view = new ListInstalledStarters.View("inventory", "1.0.0",
                Instant.parse("2026-10-06T10:00:00Z"), "alice", 2);

        var model = mapper.toModel(view);

        assertThat(model.getStarterId()).isEqualTo("inventory");
        assertThat(model.getVersion()).isEqualTo("1.0.0");
        assertThat(model.getInstalledAt()).isEqualTo(OffsetDateTime.parse("2026-10-06T10:00:00Z"));
        assertThat(model.getInstalledBy()).isEqualTo("alice");
        assertThat(model.getCustomizedDefinitions()).isEqualTo(2);
    }
}
