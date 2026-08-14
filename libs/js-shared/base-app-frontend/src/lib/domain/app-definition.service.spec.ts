import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { firstValueFrom } from 'rxjs';
import { BaseEntityLoadResponse, PersistedEntity } from '@processpuzzle/base-entity';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { beforeEach, describe, expect, it } from 'vitest';
import { AppDefinition, AppDefinitionStatus } from './app-definition';
import { AppDefinitionMapper } from './app-definition.mapper';
import { AppDefinitionService } from './app-definition.service';
import { APP_DEFINITION_DTO, pageOfAppDefinitions } from './test-app-definition';

describe('AppDefinitionService', () => {
  const serviceRoot = 'http://localhost:3000/organizations/processpuzzle-testbed';
  let service: AppDefinitionService;
  let controller: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: RUNTIME_CONFIGURATION, useValue: { BASE_CONFIGURATION: { APP_SERVICE_ROOT: serviceRoot } } },
        AppDefinitionMapper,
        AppDefinitionService,
      ],
    });
    service = TestBed.inject(AppDefinitionService);
    controller = TestBed.inject(HttpTestingController);
  });

  // The backend answers PageOf_AppDefinition — a page envelope whose entries are complete
  // definitions, not a header-only projection. The designer edits straight out of this list, so a
  // listed entry has to carry the graph or the next full-replacement PUT writes back an empty one.
  it('lists complete app definitions of the configured organization', async () => {
    const pending = firstValueFrom(service.findAll());

    const request = controller.expectOne(`${serviceRoot}/app-definitions`);
    expect(request.request.method).toBe('GET');
    request.flush(pageOfAppDefinitions(APP_DEFINITION_DTO));

    const result = (await pending) as BaseEntityLoadResponse<PersistedEntity<AppDefinition>>;
    expect(result.totalElements).toBe(1);
    const listed = result.content[0];
    expect(listed.id).toBe('demo');
    expect(listed.materialTheme).toBe('azure-blue');
    expect(listed.contentMaxWidth).toBe('1280px');
    expect(listed.regions).toEqual(APP_DEFINITION_DTO.regions);
    expect(listed.routes?.[0].path).toBe('orders');
    expect(listed.modules).toEqual(APP_DEFINITION_DTO.modules);
  });

  it('still reads the bare array the json-server mock returns', async () => {
    const pending = firstValueFrom(service.findAll());

    controller.expectOne(`${serviceRoot}/app-definitions`).flush([APP_DEFINITION_DTO]);

    const result = (await pending) as AppDefinition[];
    expect(result[0].routes?.[0].path).toBe('orders');
  });

  it('addresses a single definition by its app id', () => {
    service.delete('demo').subscribe();

    const request = controller.expectOne(`${serviceRoot}/app-definitions/demo`);
    expect(request.request.method).toBe('DELETE');
    request.flush(null);
  });

  it('sends the rebuilt definition graph on update', () => {
    const entity = new AppDefinitionMapper().fromDto({
      id: 'demo',
      name: 'Demo',
      layout: { preset: 'top-nav' },
      routes: [{ path: 'orders', title: 'Orders', target: { kind: 'ENTITY', entityName: 'Order', entityMode: 'LIST' } }],
      modules: [{ moduleKey: 'claims', basePath: 'claims' }],
    }) as PersistedEntity<AppDefinition>;

    service.update(entity).subscribe();

    const request = controller.expectOne(`${serviceRoot}/app-definitions/demo`);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body.layout.preset).toBe('top-nav');
    expect(request.request.body.routes).toEqual([{ path: 'orders', title: 'Orders', target: { kind: 'ENTITY', entityName: 'Order', entityMode: 'LIST' } }]);
    expect(request.request.body.modules).toEqual([{ moduleKey: 'claims', basePath: 'claims' }]);
    request.flush({ id: 'demo', name: 'Demo' });
  });

  it('promotes a definition through the publish sub-resource', async () => {
    const pending = firstValueFrom(service.publish('demo'));

    const request = controller.expectOne(`${serviceRoot}/app-definitions/demo/publish`);
    expect(request.request.method).toBe('POST');
    request.flush({ id: 'demo', name: 'Demo', status: 'PUBLISHED', version: 3, publishedVersion: 3 });

    const published = await pending;
    expect(published.status).toBe(AppDefinitionStatus.PUBLISHED);
    expect(published.publishedVersion).toBe(3);
  });
});
