import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { PersistedEntity } from '@processpuzzle/base-entity';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { firstValueFrom } from 'rxjs';
import { beforeEach, describe, expect, it } from 'vitest';
import { ChatDefinition } from './chat-definition';
import { ChatDefinitionService } from './chat-definition.service';

describe('ChatDefinitionService', () => {
  let service: ChatDefinitionService;
  let controller: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: RUNTIME_CONFIGURATION, useValue: { BASE_CONFIGURATION: { CHAT_SERVICE_ROOT: 'https://localhost:8080/organizations/acme' } } },
      ],
    });
    service = TestBed.inject(ChatDefinitionService);
    controller = TestBed.inject(HttpTestingController);
  });

  it('reads the definitions of the organization and maps each key to an id', async () => {
    const found = firstValueFrom(service.findAll());

    const request = controller.expectOne((candidate) => candidate.url.startsWith('https://localhost:8080/organizations/acme/chat/definitions'));
    expect(request.request.method).toBe('GET');
    request.flush([{ key: 'order-discussion', name: 'Order discussion', subjectType: 'ENTITY', subjectDefinition: 'order' }]);

    expect(((await found) as PersistedEntity<ChatDefinition>[]).map((definition) => definition.id)).toEqual(['order-discussion']);
  });

  it('creates a definition with a PUT under its key, as the contract has no collection POST', async () => {
    const created = firstValueFrom(service.add(new ChatDefinition({ id: 'order-crew', name: 'Crew', subjectDefinition: 'order' })));

    const request = controller.expectOne('https://localhost:8080/organizations/acme/chat/definitions/order-crew');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toMatchObject({ key: 'order-crew', name: 'Crew' });
    request.flush({ key: 'order-crew', name: 'Crew', subjectType: 'ENTITY', subjectDefinition: 'order', version: 0 });

    expect((await created).id).toBe('order-crew');
  });
});
