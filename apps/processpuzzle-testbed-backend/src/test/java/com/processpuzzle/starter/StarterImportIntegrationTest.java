package com.processpuzzle.starter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.processpuzzle.baseentity.api.EntityAttributeQuery;
import com.processpuzzle.baseentity.api.EntityAttributeKind;
import com.processpuzzle.rule.usecase.ImportRules;
import com.processpuzzle.rule.usecase.engine.RuleEngine;
import com.processpuzzle.rule.usecase.engine.RuleKey;
import com.processpuzzle.starter.usecase.ImportBundle;
import com.processpuzzle.starter.usecase.ImportRejectedException;
import com.processpuzzle.starter.usecase.ImportReport;
import com.processpuzzle.starter.usecase.ListInstalledStarters;
import com.processpuzzle.state.usecase.FindStateMachineDefinition;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ClassPathResource;

/**
 * The pilot {@code inventory} starter, end to end through every participant the testbed hosts. Only
 * the application sees base-entity, base-state and base-rule together, so only here does a state
 * machine validate against an entity created moments earlier in the same import transaction.
 *
 * <p>Each test imports into its own organization: the context, and its database, are shared.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "base-starter.bundle.max-file-bytes=16384")
class StarterImportIntegrationTest {

    private static final String BUNDLE_ROOT = "starters/inventory/";
    private static final String[] BUNDLE_FILES = {
        "manifest.yaml",
        "entities/inventory-entities.yaml",
        "states/inventory-item-lifecycle.yaml",
        "rules/inventory-rules.yaml"};

    @LocalServerPort
    private int port;

    @Autowired
    private ImportBundle importBundle;
    @Autowired
    private ListInstalledStarters listInstalledStarters;
    @Autowired
    private EntityAttributeQuery entityAttributeQuery;
    @Autowired
    private FindStateMachineDefinition findStateMachine;
    @Autowired
    private ImportRules importRules;
    @Autowired
    private RuleEngine ruleEngine;

    @Test
    void aDryRunReportsEveryCreateAndWritesNothing() throws IOException {
        ImportReport report = importBundle.execute("dry-run-org", zip(pilot()), true, null);

        assertThat(report.status()).isEqualTo(ImportReport.Status.WOULD_APPLY);
        assertThat(report.items()).extracting(item -> item.kind() + ":" + item.key()).containsExactly(
                "entity:stock-location", "entity:inventory-item", "state:inventory-item",
                "rule:non-negative-quantity", "rule:sku-format");
        assertThat(report.items()).extracting(ImportReport.Item::action).containsOnly(ImportReport.Action.CREATE);

        assertThat(entityAttributeQuery.entityTypeExists("dry-run-org", "inventory-item")).isFalse();
        assertThatThrownBy(() -> findStateMachine.execute("dry-run-org", "inventory-item")).isInstanceOf(RuntimeException.class);
        assertThat(ruleEngine.isRegistered(RuleKey.of("dry-run-org", "non-negative-quantity"))).isFalse();
        assertThat(listInstalledStarters.execute("dry-run-org")).isEmpty();
    }

    @Test
    void appliesIntoTwoOrganizationsWithoutCollision() throws IOException {
        ImportReport first = importBundle.execute("tenant-a", zip(pilot()), false, "alice");
        ImportReport second = importBundle.execute("tenant-b", zip(pilot()), false, "bob");

        for (ImportReport report : new ImportReport[] {first, second}) {
            assertThat(report.status()).isEqualTo(ImportReport.Status.APPLIED);
            assertThat(report.created()).isEqualTo(5);
        }
        for (String orgKey : new String[] {"tenant-a", "tenant-b"}) {
            assertThat(entityAttributeQuery.attributeKind(orgKey, "inventory-item", "status")).contains(EntityAttributeKind.ENUM);
            assertThat(findStateMachine.execute(orgKey, "inventory-item").getInitialStateKey()).isEqualTo("IN_STOCK");
            assertThat(ruleEngine.isRegistered(RuleKey.of(orgKey, "sku-format"))).isTrue();
            assertThat(listInstalledStarters.execute(orgKey)).singleElement().satisfies(installed -> {
                assertThat(installed.starterId()).isEqualTo("inventory");
                assertThat(installed.version()).isEqualTo("1.0.0");
                assertThat(installed.customizedDefinitions()).isZero();
            });
        }
    }

    @Test
    void aReimportReportsUpdatesAndAnEditCountsAsCustomized() throws IOException {
        importBundle.execute("reimport-org", zip(pilot()), false, null);

        ImportReport again = importBundle.execute("reimport-org", zip(pilot()), false, null);
        assertThat(again.items()).hasSize(5).extracting(ImportReport.Item::action).containsOnly(ImportReport.Action.UPDATE);
        assertThat(listInstalledStarters.execute("reimport-org")).singleElement()
                .extracting(ListInstalledStarters.View::customizedDefinitions).isEqualTo(0);

        String edited = """
                rules:
                  - id: sku-format
                    name: SKU is upper case
                    context: Inventory Item
                    severity: ERROR
                    fields: [sku]
                    expression: "!entity.sku || entity.sku === entity.sku.toUpperCase()"
                    message: SKUs must be written in upper case.
                """;
        importRules.execute("reimport-org", new ByteArrayInputStream(edited.getBytes(StandardCharsets.UTF_8)));

        assertThat(listInstalledStarters.execute("reimport-org")).singleElement()
                .extracting(ListInstalledStarters.View::customizedDefinitions).isEqualTo(1);
    }

    @Test
    void aBadRuleRollsBackTheEntitiesAndStatesToo() throws IOException {
        Map<String, byte[]> files = pilot();
        files.put("rules/inventory-rules.yaml", """
                rules:
                  - id: orphan
                    name: Extends nothing
                    context: Inventory Item
                    extends: no-such-rule
                    expression: "true"
                """.getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> importBundle.execute("rollback-org", zip(files), false, null))
                .isInstanceOfSatisfying(ImportRejectedException.class, e -> {
                    assertThat(e.getReport().errors()).singleElement().satisfies(error -> {
                        assertThat(error.file()).isEqualTo("rules/inventory-rules.yaml");
                        assertThat(error.message()).contains("extends unknown rule 'no-such-rule'");
                    });
                });

        assertThat(entityAttributeQuery.entityTypeExists("rollback-org", "inventory-item")).isFalse();
        assertThat(entityAttributeQuery.entityTypeExists("rollback-org", "stock-location")).isFalse();
        assertThatThrownBy(() -> findStateMachine.execute("rollback-org", "inventory-item")).isInstanceOf(RuntimeException.class);
        assertThat(listInstalledStarters.execute("rollback-org")).isEmpty();
    }

    @Test
    void aStateMachineNamingAMissingAttributeIsRejected() throws IOException {
        Map<String, byte[]> files = pilot();
        String lifecycle = new String(files.get("states/inventory-item-lifecycle.yaml"), StandardCharsets.UTF_8);
        files.put("states/inventory-item-lifecycle.yaml",
                lifecycle.replace("stateAttributeKey: status", "stateAttributeKey: phase").getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> importBundle.execute("state-org", zip(files), false, null))
                .isInstanceOf(ImportRejectedException.class)
                .hasMessageContaining("'phase' is not an attribute of 'inventory-item'");
        assertThat(entityAttributeQuery.entityTypeExists("state-org", "inventory-item")).isFalse();
    }

    @Test
    void overHttpAnAppliedImportAnswers200() throws Exception {
        HttpResponse<String> response = upload("http-org", zipBytes(pilot()), false);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"status\":\"applied\"").contains("\"created\":5");
    }

    @Test
    void overHttpAZipSlipBundleIsRejectedWith422() throws Exception {
        Map<String, byte[]> files = pilot();
        files.put("../../etc/evil.yaml", "rules: []".getBytes(StandardCharsets.UTF_8));

        HttpResponse<String> response = upload("zip-slip-org", zipBytes(files), true);

        assertThat(response.statusCode()).isEqualTo(422);
        assertThat(response.body()).contains("\"status\":\"rejected\"").contains("Unsafe path");
    }

    @Test
    void overHttpAnOversizedBundleIsRejectedWith413() throws Exception {
        Map<String, byte[]> files = pilot();
        files.put("entities/inventory-entities.yaml", new byte[32 * 1024]);

        HttpResponse<String> response = upload("oversized-org", zipBytes(files), true);

        assertThat(response.statusCode()).isEqualTo(413);
        assertThat(response.body()).contains("\"status\":\"rejected\"");
    }

    private Map<String, byte[]> pilot() throws IOException {
        Map<String, byte[]> files = new LinkedHashMap<>();
        for (String file : BUNDLE_FILES) {
            try (InputStream input = new ClassPathResource(BUNDLE_ROOT + file).getInputStream()) {
                files.put(file, input.readAllBytes());
            }
        }
        return files;
    }

    private static InputStream zip(Map<String, byte[]> files) throws IOException {
        return new ByteArrayInputStream(zipBytes(files));
    }

    private static byte[] zipBytes(Map<String, byte[]> files) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (Map.Entry<String, byte[]> file : files.entrySet()) {
                zip.putNextEntry(new ZipEntry(file.getKey()));
                zip.write(file.getValue());
                zip.closeEntry();
            }
        }
        return out.toByteArray();
    }

    private HttpResponse<String> upload(String orgKey, byte[] bundle, boolean dryRun) throws Exception {
        String boundary = "----starter-test-boundary";
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"bundle\"; filename=\"inventory.zip\"\r\n"
                + "Content-Type: application/zip\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(bundle);
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

        HttpRequest request = HttpRequest.newBuilder(URI.create(
                        "http://localhost:" + port + "/organizations/" + orgKey + "/definitions/import?dryRun=" + dryRun))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                .build();
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }
}
