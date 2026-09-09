import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { BaseEntityLoadResponse, PersistedEntity } from '@processpuzzle/base-entity';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { firstValueFrom } from 'rxjs';
import { beforeEach, describe, expect, it } from 'vitest';
import { pageOfWidgetDefinitions, WIDGET_DEFINITION_DTO } from './test-widget-definition';
import { WidgetDefinition } from './widget-definition';
import { WidgetDefinitionMapper } from './widget-definition.mapper';
import { WidgetDefinitionService } from './widget-definition.service';

describe('WidgetDefinitionService', () => {
  const serviceRoot = 'http://localhost:3000/organizations/processpuzzle-testbed';
  let service: WidgetDefinitionService;
  let controller: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: RUNTIME_CONFIGURATION, useValue: { BASE_CONFIGURATION: { APP_SERVICE_ROOT: serviceRoot } } },
        WidgetDefinitionMapper,
        WidgetDefinitionService,
      ],
    });
    service = TestBed.inject(WidgetDefinitionService);
    controller = TestBed.inject(HttpTestingController);
  });

  // The catalogue is a collection of the organization, beside its apps rather than under one — which is
  // what lets one widget type be placed by every app and module of the tenant.
  it('lists the widget definitions of the configured organization', async () => {
    const pending = firstValueFrom(service.findAll());

    const request = controller.expectOne(`${serviceRoot}/widget-definitions`);
    expect(request.request.method).toBe('GET');
    request.flush(pageOfWidgetDefinitions(WIDGET_DEFINITION_DTO));

    const result = (await pending) as BaseEntityLoadResponse<PersistedEntity<WidgetDefinition>>;
    expect(result.totalElements).toBe(1);
    expect(result.content[0].id).toBe('cards-grid');
    expect(result.content[0].inputPorts?.[0].name).toBe('items');
  });

  it('still reads the bare array the json-server mock returns', async () => {
    const pending = firstValueFrom(service.findAll());

    controller.expectOne(`${serviceRoot}/widget-definitions`).flush([WIDGET_DEFINITION_DTO]);

    expect(((await pending) as WidgetDefinition[])[0].category).toBe('Content');
  });

  /** The URL variable is the widget key, which the mapper turned into the `id` `findById` builds from. */
  it('addresses a single definition by its key', async () => {
    const pending = firstValueFrom(service.findById('cards-grid'));

    const request = controller.expectOne(`${serviceRoot}/widget-definitions/cards-grid`);
    expect(request.request.method).toBe('GET');
    request.flush(WIDGET_DEFINITION_DTO);

    expect(((await pending) as WidgetDefinition).name).toBe('Cards grid');
  });

  /** The reason the mapper carries unrendered fields: update replaces the whole input schema. */
  it('sends the whole definition on update, props schema and all', () => {
    const entity = new WidgetDefinitionMapper().fromDto(WIDGET_DEFINITION_DTO) as PersistedEntity<WidgetDefinition>;

    service.update(entity).subscribe();

    const request = controller.expectOne(`${serviceRoot}/widget-definitions/cards-grid`);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body.key).toBe('cards-grid');
    expect(request.request.body.propsSchema).toEqual(WIDGET_DEFINITION_DTO.propsSchema);
    request.flush(WIDGET_DEFINITION_DTO);
  });

  it('promotes a definition through its publish endpoint', async () => {
    const pending = firstValueFrom(service.publish('cards-grid'));

    const request = controller.expectOne(`${serviceRoot}/widget-definitions/cards-grid/publish`);
    expect(request.request.method).toBe('POST');
    request.flush({ ...WIDGET_DEFINITION_DTO, status: 'PUBLISHED', publishedVersion: 3 });

    const published = await pending;
    expect(published.status).toBe('PUBLISHED');
    expect(published.publishedVersion).toBe(3);
  });

  it('deletes a definition by key', () => {
    service.delete('cards-grid').subscribe();

    const request = controller.expectOne(`${serviceRoot}/widget-definitions/cards-grid`);
    expect(request.request.method).toBe('DELETE');
    request.flush(null);
  });
});
