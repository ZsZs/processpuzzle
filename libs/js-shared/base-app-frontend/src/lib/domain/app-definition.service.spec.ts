import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { firstValueFrom } from 'rxjs';
import { PersistedEntity } from '@processpuzzle/base-entity';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { beforeEach, describe, expect, it } from 'vitest';
import { AppDefinition, AppDefinitionStatus } from './app-definition';
import { AppDefinitionMapper } from './app-definition.mapper';
import { AppDefinitionService } from './app-definition.service';

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

  it('lists the app definitions of the configured organization', async () => {
    const pending = firstValueFrom(service.findAll());

    const request = controller.expectOne(`${serviceRoot}/app-definitions`);
    expect(request.request.method).toBe('GET');
    request.flush([{ id: 'demo', name: 'Demo', theme: { materialTheme: 'azure-blue' } }]);

    const result = (await pending) as AppDefinition[];
    expect(result[0].id).toBe('demo');
    expect(result[0].materialTheme).toBe('azure-blue');
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
      pages: [{ id: 'p1', title: 'P1', widgets: [] }],
    }) as PersistedEntity<AppDefinition>;

    service.update(entity).subscribe();

    const request = controller.expectOne(`${serviceRoot}/app-definitions/demo`);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body.layout.preset).toBe('top-nav');
    expect(request.request.body.pages).toEqual([{ id: 'p1', title: 'P1', widgets: [] }]);
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
