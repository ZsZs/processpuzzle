import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { firstValueFrom } from 'rxjs';
import { BaseEntityLoadResponse, PersistedEntity } from '@processpuzzle/base-entity';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { beforeEach, describe, expect, it } from 'vitest';
import { StateMachineDefinition } from './state-machine-definition';
import { StateMachineDefinitionMapper } from './state-machine-definition.mapper';
import { StateMachineDefinitionService } from './state-machine-definition.service';
import { pageOfStateMachineDefinitions, STATE_MACHINE_DEFINITION_DTO } from './test-state-machine-definition';

describe('StateMachineDefinitionService', () => {
  const serviceRoot = 'http://localhost:3000/organizations/processpuzzle-testbed';

  function configure(baseConfiguration: object) {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: RUNTIME_CONFIGURATION, useValue: { BASE_CONFIGURATION: baseConfiguration } },
        StateMachineDefinitionMapper,
        StateMachineDefinitionService,
      ],
    });
    return { service: TestBed.inject(StateMachineDefinitionService), controller: TestBed.inject(HttpTestingController) };
  }

  let service: StateMachineDefinitionService;
  let controller: HttpTestingController;

  beforeEach(() => {
    ({ service, controller } = configure({ STATE_SERVICE_ROOT: serviceRoot }));
  });

  // The backend answers PageOf_StateMachineDefinition — a page whose entries are complete machines
  // rather than headers. The form reads its entity straight out of this list, so anything the list
  // drops the next full-replacement PUT writes back as empty.
  it('lists complete state machines of the configured organization', async () => {
    const pending = firstValueFrom(service.findAll());

    const request = controller.expectOne(`${serviceRoot}/state-machines`);
    expect(request.request.method).toBe('GET');
    request.flush(pageOfStateMachineDefinitions(STATE_MACHINE_DEFINITION_DTO));

    const result = (await pending) as BaseEntityLoadResponse<PersistedEntity<StateMachineDefinition>>;
    expect(result.totalElements).toBe(1);
    const listed = result.content[0];
    expect(listed.id).toBe('order');
    expect(listed.states).toHaveLength(2);
    expect(listed.transitions[0].guards).toHaveLength(1);
  });

  it('still reads the bare array the json-server mock returns', async () => {
    const pending = firstValueFrom(service.findAll());

    controller.expectOne(`${serviceRoot}/state-machines`).flush([STATE_MACHINE_DEFINITION_DTO]);

    const result = (await pending) as StateMachineDefinition[];
    expect(result[0].transitions[0].triggerKey).toBe('confirm');
  });

  // The contract addresses a definition by the entity it governs, and `id` is the mirror of exactly
  // that — so a single-record URL has to end in the entity name, not in a generated key.
  it('addresses a single machine by the entity name it governs', () => {
    service.delete('order').subscribe();

    const request = controller.expectOne(`${serviceRoot}/state-machines/order`);
    expect(request.request.method).toBe('DELETE');
    request.flush(null);
  });

  it('sends the whole machine on update', () => {
    const entity = new StateMachineDefinitionMapper().fromDto(STATE_MACHINE_DEFINITION_DTO) as PersistedEntity<StateMachineDefinition>;

    service.update(entity).subscribe();

    const request = controller.expectOne(`${serviceRoot}/state-machines/order`);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body.entityName).toBe('order');
    expect(request.request.body.states).toHaveLength(2);
    expect(request.request.body.transitions[0].guards[0].beanName).toBe('sufficientBalanceGuard');
    request.flush(STATE_MACHINE_DEFINITION_DTO);
  });

  // STATE_SERVICE_ROOT is optional by contract; `serviceRootOf` falls back to APP_SERVICE_ROOT, which
  // is the only root this workspace's deployments actually configure today.
  it('falls back to APP_SERVICE_ROOT when no state root is configured', () => {
    const { service: fallbackService, controller: fallbackController } = configure({ APP_SERVICE_ROOT: serviceRoot });

    fallbackService.delete('order').subscribe();

    fallbackController.expectOne(`${serviceRoot}/state-machines/order`).flush(null);
  });
});
