import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { firstValueFrom } from 'rxjs';
import { BaseEntityLoadResponse, PersistedEntity } from '@processpuzzle/base-entity';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { beforeEach, describe, expect, it } from 'vitest';
import { ModuleDefinition } from './module-definition';
import { ModuleDefinitionMapper } from './module-definition.mapper';
import { ModuleDefinitionService } from './module-definition.service';
import { MODULE_DEFINITION_DTO, pageOfModuleDefinitions } from './test-module-definition';

describe('ModuleDefinitionService', () => {
  const serviceRoot = 'http://localhost:3000/organizations/processpuzzle-testbed';
  let service: ModuleDefinitionService;
  let controller: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: RUNTIME_CONFIGURATION, useValue: { BASE_CONFIGURATION: { APP_SERVICE_ROOT: serviceRoot } } },
        ModuleDefinitionMapper,
        ModuleDefinitionService,
      ],
    });
    service = TestBed.inject(ModuleDefinitionService);
    controller = TestBed.inject(HttpTestingController);
  });

  // Modules are a collection of the organization, beside its apps rather than under one, which is what
  // makes the same module mountable by more than one app.
  it('lists complete module definitions of the configured organization', async () => {
    const pending = firstValueFrom(service.findAll());

    const request = controller.expectOne(`${serviceRoot}/modules`);
    expect(request.request.method).toBe('GET');
    request.flush(pageOfModuleDefinitions(MODULE_DEFINITION_DTO));

    const result = (await pending) as BaseEntityLoadResponse<PersistedEntity<ModuleDefinition>>;
    expect(result.totalElements).toBe(1);
    const listed = result.content[0];
    expect(listed.id).toBe('order-admin');
    expect(listed.translocoScope).toBe('order_admin');
    expect(listed.routes?.map((route) => route.path)).toEqual(['lines', 'line/:id']);
  });

  it('still reads the bare array the json-server mock returns', async () => {
    const pending = firstValueFrom(service.findAll());

    controller.expectOne(`${serviceRoot}/modules`).flush([MODULE_DEFINITION_DTO]);

    const result = (await pending) as ModuleDefinition[];
    expect(result[0].routes?.[0].entityName).toBe('Order Line');
  });

  /** The URL variable is the module key, which the mapper turned into the `id` `findById` builds from. */
  it('addresses a single module by its key', async () => {
    const pending = firstValueFrom(service.findById('order-admin'));

    const request = controller.expectOne(`${serviceRoot}/modules/order-admin`);
    expect(request.request.method).toBe('GET');
    request.flush(MODULE_DEFINITION_DTO);

    expect(((await pending) as ModuleDefinition).name).toBe('Order administration');
  });

  it('sends the rebuilt module graph on update, key and all', () => {
    const entity = new ModuleDefinitionMapper().fromDto(MODULE_DEFINITION_DTO) as PersistedEntity<ModuleDefinition>;

    service.update(entity).subscribe();

    const request = controller.expectOne(`${serviceRoot}/modules/order-admin`);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body.key).toBe('order-admin');
    expect(request.request.body.routes).toEqual(MODULE_DEFINITION_DTO.routes);
    request.flush(MODULE_DEFINITION_DTO);
  });

  /**
   * There is no `publish` here on purpose: an app is what a user navigates to, and its status is what
   * decides whether a mount is live.
   */
  it('deletes a module by key', () => {
    service.delete('order-admin').subscribe();

    const request = controller.expectOne(`${serviceRoot}/modules/order-admin`);
    expect(request.request.method).toBe('DELETE');
    request.flush(null);
  });
});
