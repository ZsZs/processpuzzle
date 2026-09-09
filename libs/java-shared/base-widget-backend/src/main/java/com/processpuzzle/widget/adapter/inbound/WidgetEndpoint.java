package com.processpuzzle.widget.adapter.inbound;

import com.processpuzzle.core.logging.LogClass;
import com.processpuzzle.widget.api.BaseWidgetApi;
import com.processpuzzle.widget.model.PageOfWidgetDefinition;
import com.processpuzzle.widget.model.WidgetDefinition;
import com.processpuzzle.widget.model.WidgetDefinitionInput;
import com.processpuzzle.widget.usecase.WidgetDefinitionCrud;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST adapter for {@link WidgetDefinitionCrud}. Thin by intent: it converts, delegates and maps
 * the result, and holds no decision of its own — refusals are raised by the use case and turned
 * into status codes by {@link WidgetApiExceptionHandler}.
 *
 * <p>{@code orgKey} arrives as a path segment and is <em>not</em> an authorization decision on its
 * own. Verifying it against the authenticated principal's {@code organization} claim is a
 * server-side responsibility the host application supplies; base-app models that as an
 * {@code OrganizationAccessPolicy} port, and this module will need the same once it is hosted
 * behind a real identity provider. Recorded here rather than silently omitted.
 */
@RestController
@LogClass
public class WidgetEndpoint implements BaseWidgetApi {

    private final WidgetDefinitionCrud crud;
    private final WidgetMapper mapper;

    public WidgetEndpoint(WidgetDefinitionCrud crud, WidgetMapper mapper) {
        this.crud = crud;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<PageOfWidgetDefinition> listWidgetDefinitions(String orgKey, String where, String order, Integer page, Integer size) {
        Page<com.processpuzzle.widget.domain.WidgetDefinition> found =
                crud.findAll(orgKey, PageRequest.of(page == null ? 0 : page, size == null ? 20 : size));

        PageOfWidgetDefinition body = new PageOfWidgetDefinition();
        body.setContent(found.getContent().stream().map(mapper::toModel).toList());
        body.setTotalElements(found.getTotalElements());
        body.setTotalPages(found.getTotalPages());
        body.setNumber(found.getNumber());
        body.setSize(found.getSize());
        return ResponseEntity.ok(body);
    }

    @Override
    public ResponseEntity<WidgetDefinition> getWidgetDefinition(String orgKey, String key) {
        return ResponseEntity.ok(mapper.toModel(crud.find(orgKey, key)));
    }

    @Override
    public ResponseEntity<WidgetDefinition> createWidgetDefinition(String orgKey, WidgetDefinitionInput input) {
        var created = crud.create(orgKey, mapper.toDraft(input));
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toModel(created));
    }

    @Override
    public ResponseEntity<WidgetDefinition> updateWidgetDefinition(String orgKey, String key, WidgetDefinitionInput input) {
        return ResponseEntity.ok(mapper.toModel(crud.update(orgKey, key, mapper.toDraft(input))));
    }

    @Override
    public ResponseEntity<WidgetDefinition> publishWidgetDefinition(String orgKey, String key) {
        return ResponseEntity.ok(mapper.toModel(crud.publish(orgKey, key)));
    }

    @Override
    public ResponseEntity<Void> deleteWidgetDefinition(String orgKey, String key) {
        crud.delete(orgKey, key);
        return ResponseEntity.noContent().build();
    }
}
