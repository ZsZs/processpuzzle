package com.processpuzzle.widget.adapter.inbound;

import com.processpuzzle.shared.model.InputPort;
import com.processpuzzle.shared.model.PortType;
import com.processpuzzle.widget.domain.WidgetDefinitionRepository;
import com.processpuzzle.widget.model.WidgetDefinition;
import com.processpuzzle.widget.model.WidgetDefinitionInput;
import com.processpuzzle.widget.usecase.WidgetDefinitionCrud;
import com.processpuzzle.widget.usecase.exception.WidgetDefinitionAlreadyExistsException;
import com.processpuzzle.widget.usecase.exception.WidgetDefinitionInvalidException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the loader's file walk and, through the bundled {@code processpuzzle-testbed-widgets.yaml},
 * the shipped catalogue itself: the last tests feed the parsed definitions to the real
 * {@link WidgetDefinitionCrud}, so a YAML edit that breaks a key or duplicates a port name fails here
 * rather than at run-time with the loader logging a rejection nobody reads.
 */
class DefaultWidgetLoaderTest {

    private static final String TESTBED_KEY = "processpuzzle-testbed";
    private static final String TESTBED_FILE = "processpuzzle-testbed-widgets.yaml";

    private WidgetEndpoint endpoint;
    private ResourcePatternResolver resourceResolver;
    private DefaultWidgetLoader loader;

    @BeforeEach
    void setUp() throws IOException {
        endpoint = mock(WidgetEndpoint.class);
        resourceResolver = mock(ResourcePatternResolver.class);
        when(resourceResolver.getResources(anyString())).thenReturn(new Resource[] { bundledTestbedFile() });
        when(endpoint.createWidgetDefinition(anyString(), any())).thenAnswer(call -> created(call.getArgument(1)));
        loader = new DefaultWidgetLoader(endpoint, resourceResolver);
    }

    @Test
    void createsEveryDefinitionOfTheFileInTheOrganizationItIsNamedAfter() {
        loader.loadDefaults();

        // The catalogue holds exactly the keys base-widget-frontend's provideBaseWidgets() registers; a
        // palette entry with no component behind it only fails when an app is previewed. See the file header.
        assertThat(capturedDefinitions()).extracting(WidgetDefinitionInput::getKey)
                .containsExactly("cards-grid", "markdown-page", "language-selector", "like-button", "share-button", "version-button");
    }

    @Test
    void leavesAnAlreadyPresentDefinitionUntouched() {
        // doThrow, not when(...).thenThrow: re-stubbing through the mock would invoke the answer set up
        // in setUp with null arguments.
        doThrow(new WidgetDefinitionAlreadyExistsException(TESTBED_KEY, "cards-grid"))
                .when(endpoint).createWidgetDefinition(anyString(), any());

        assertThatCode(loader::loadDefaults).doesNotThrowAnyException();
    }

    @Test
    void survivesADefinitionRejectedByValidation() {
        doThrow(new WidgetDefinitionInvalidException("Widget key must be kebab-case: 'Cards Grid'."))
                .when(endpoint).createWidgetDefinition(anyString(), any());

        assertThatCode(loader::loadDefaults).doesNotThrowAnyException();
    }

    @Test
    void aCreationFailingForAnyOtherReason_isSurvived() {
        doThrow(new IllegalStateException("constraint violation"))
                .when(endpoint).createWidgetDefinition(anyString(), any());

        assertThatCode(loader::loadDefaults).doesNotThrowAnyException();
    }

    /** The revision is only logged, so an answer without one must not become an exception. */
    @Test
    void aCreationAnsweringWithoutARevision_isSurvived() {
        doReturn(new ResponseEntity<>(new WidgetDefinition(), HttpStatus.CREATED))
                .when(endpoint).createWidgetDefinition(anyString(), any());
        assertThatCode(loader::loadDefaults).doesNotThrowAnyException();

        doReturn(ResponseEntity.ok(null)).when(endpoint).createWidgetDefinition(anyString(), any());
        assertThatCode(loader::loadDefaults).doesNotThrowAnyException();
    }

    @Test
    void seedsTheTenantTheFileIsNamedAfterRatherThanAnythingInsideIt() {
        resolvesTo(yamlFile("other-org-widgets.yaml", "widgetDefinitions:\n  - key: chart\n    name: Chart\n"));

        loader.loadDefaults();

        verify(endpoint).createWidgetDefinition(eq("other-org"), any(WidgetDefinitionInput.class));
    }

    @Test
    void skipsAFileNotNamedAfterAnOrganization() {
        resolvesTo(yamlFile("widgets.yaml", "widgetDefinitions:\n  - key: chart\n    name: Chart\n"));

        loader.loadDefaults();

        verify(endpoint, never()).createWidgetDefinition(anyString(), any());
    }

    /** {@code -widgets.yaml} on its own names no organization, so there is nowhere to put its contents. */
    @Test
    void aFileNamedOnlyAfterTheSuffix_isSkipped() {
        resolvesTo(yamlFile("-widgets.yaml", "widgetDefinitions: []"));

        loader.loadDefaults();

        verify(endpoint, never()).createWidgetDefinition(anyString(), any());
    }

    @Test
    void aResourceWithoutAName_isSkipped() {
        resolvesTo(new ByteArrayResource("widgetDefinitions: []".getBytes(StandardCharsets.UTF_8)));

        loader.loadDefaults();

        verify(endpoint, never()).createWidgetDefinition(anyString(), any());
    }

    /** A blank key is as unusable as an absent one: the key is what a placement names the type by. */
    @Test
    void anEntryWithoutAUsableKey_isRejectedWithoutReachingTheEndpoint() {
        resolvesTo(yamlFile("other-org-widgets.yaml",
                "widgetDefinitions:\n  - name: Nameless\n  - key: \"   \"\n    name: Blank\n"));

        loader.loadDefaults();

        verify(endpoint, never()).createWidgetDefinition(anyString(), any());
    }

    /** A file that parses but declares no `widgetDefinitions` — an empty catalogue, not a broken file. */
    @Test
    void aFileDeclaringNoDefinitions_seedsNone() {
        resolvesTo(yamlFile("other-org-widgets.yaml", "note: nothing to seed yet\n"));

        loader.loadDefaults();

        verify(endpoint, never()).createWidgetDefinition(anyString(), any());
    }

    /**
     * A convenience that refuses to boot would be worse than one that seeds nothing, so every failure
     * below has to leave the application started.
     */
    @Test
    void anUnscannableClasspath_isLoggedRatherThanFailingStartup() throws IOException {
        when(resourceResolver.getResources(anyString())).thenThrow(new IOException("no such classpath"));

        assertThatCode(loader::loadDefaults).doesNotThrowAnyException();

        verify(endpoint, never()).createWidgetDefinition(anyString(), any());
    }

    @Test
    void aDeploymentBundlingNoDefaults_seedsNothing() {
        resolvesTo();

        loader.loadDefaults();

        verify(endpoint, never()).createWidgetDefinition(anyString(), any());
    }

    @Test
    void anUnreadableFile_isSkipped() {
        resolvesTo(yamlFile("broken-widgets.yaml", "widgetDefinitions: [\n"));

        assertThatCode(loader::loadDefaults).doesNotThrowAnyException();

        verify(endpoint, never()).createWidgetDefinition(anyString(), any());
    }

    /**
     * The catalogue the library ships: a palette entry is only useful if it carries the props schema the
     * designer generates a form from, so its absence is asserted against rather than tolerated.
     */
    @Test
    void theBundledCatalogueDescribesItsWidgetsPropsAndPorts() {
        loader.loadDefaults();

        WidgetDefinitionInput cardsGrid = capturedDefinitions().get(0);
        assertThat(cardsGrid.getName()).isEqualTo("Cards grid");
        assertThat(cardsGrid.getTranslocoId()).isEqualTo("base_widget.cards_grid.name");
        assertThat(cardsGrid.getCategory()).isEqualTo("Content");
        assertThat(cardsGrid.getIcon()).isEqualTo("grid_view");
        assertThat(cardsGrid.getInputPorts()).extracting(InputPort::getName, InputPort::getType)
                .containsExactly(tuple("cards", PortType.ARRAY));

        // Nested, and stored verbatim: the array's item schema is what the generated props form needs to
        // render a row editor, and a mapper that flattened it would leave the designer with a text box.
        assertThat(nested(cardsGrid.getPropsSchema(), "properties", "cards")).containsEntry("type", "array");
        assertThat(nested(cardsGrid.getPropsSchema(), "properties", "cards", "items", "properties"))
                .containsKeys("title", "imageUrl", "actionLink");

        WidgetDefinitionInput markdownPage = capturedDefinitions().get(1);
        assertThat(markdownPage.getName()).isEqualTo("Markdown page");
        assertThat(markdownPage.getPropsSchema()).containsEntry("required", List.of("markdownSrc"));
        assertThat(markdownPage.getInputPorts()).extracting(InputPort::getName, InputPort::getType)
                .containsExactly(tuple("markdownSrc", PortType.STRING));
    }

    /**
     * The same definitions through the real use case: a kebab-case key, a name and unique port names are
     * what {@code WidgetDefinitionCrud} insists on, and a bundled default that fails them would be seeded
     * as nothing but a warning in the startup log.
     */
    @Test
    void everyBundledDefinitionIsAcceptedByTheRealUseCase() {
        loader.loadDefaults();

        WidgetMapper mapper = new WidgetMapper();
        WidgetDefinitionRepository repository = mock(WidgetDefinitionRepository.class);
        when(repository.existsByOrgKeyAndKey(anyString(), anyString())).thenReturn(false);
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));
        WidgetDefinitionCrud crud = new WidgetDefinitionCrud(repository);

        for (WidgetDefinitionInput definition : capturedDefinitions()) {
            assertThatCode(() -> crud.create(TESTBED_KEY, mapper.toDraft(definition)))
                    .as("bundled definition '%s'", definition.getKey())
                    .doesNotThrowAnyException();
        }
    }

    // --- helpers -----------------------------------------------------------------------------

    private List<WidgetDefinitionInput> capturedDefinitions() {
        ArgumentCaptor<WidgetDefinitionInput> definitions = ArgumentCaptor.forClass(WidgetDefinitionInput.class);
        verify(endpoint, atLeastOnce()).createWidgetDefinition(eq(TESTBED_KEY), definitions.capture());
        return definitions.getAllValues();
    }

    /** Walks a parsed {@code propsSchema} without a cast at every level. */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> nested(Map<String, Object> schema, String... path) {
        Map<String, Object> current = schema;
        for (String step : path) {
            assertThat(current).as("path step '%s'", step).containsKey(step);
            current = (Map<String, Object>) current.get(step);
        }
        return current;
    }

    private void resolvesTo(Resource... resources) {
        try {
            when(resourceResolver.getResources(anyString())).thenReturn(resources);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    private static ResponseEntity<WidgetDefinition> created(WidgetDefinitionInput input) {
        WidgetDefinition definition = new WidgetDefinition();
        definition.setKey(input.getKey());
        definition.setVersion(1L);
        return new ResponseEntity<>(definition, HttpStatus.CREATED);
    }

    /** The file this library actually ships, so the test reads the same bytes production does. */
    private static Resource bundledTestbedFile() {
        return new ClassPathResource("default-widgets/" + TESTBED_FILE);
    }

    /** An in-memory YAML file; {@link ByteArrayResource} has no name of its own. */
    private static Resource yamlFile(String fileName, String content) {
        return new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return fileName;
            }
        };
    }
}
