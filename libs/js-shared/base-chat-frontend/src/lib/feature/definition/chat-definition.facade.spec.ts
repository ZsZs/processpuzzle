import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { beforeEach, describe, expect, it } from 'vitest';
import { ChatDefinition } from '../../domain/chat-definition';
import { ChatDefinitionMapper } from '../../domain/chat-definition.mapper';
import { ChatDefinitionService } from '../../domain/chat-definition.service';
import { ChatDefinitionStore } from '../../domain/chat-definition.store';
import { ChatDefinitionFacade } from './chat-definition.facade';

describe('ChatDefinitionFacade', () => {
  let facade: ChatDefinitionFacade;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: RUNTIME_CONFIGURATION, useValue: { BASE_CONFIGURATION: { CHAT_SERVICE_ROOT: 'https://localhost:8080/organizations/acme' } } },
        ChatDefinitionFacade,
      ],
    });
    facade = TestBed.inject(ChatDefinitionFacade);
  });

  it('registers under the entity name the route and the transloco scope derive from', () => {
    expect(facade.entityType).toBe(ChatDefinition);
    expect(facade.entityName).toBe('Chat Definition');
  });

  it('reuses the root-provided mapper, service and store', () => {
    expect(facade.mapper).toBe(TestBed.inject(ChatDefinitionMapper));
    expect(facade.service).toBe(TestBed.inject(ChatDefinitionService));
    expect(facade.storeClass).toBe(ChatDefinitionStore);
    expect(facade.store).toBe(TestBed.inject(ChatDefinitionStore));
  });

  it('binds the store to the descriptor it hands out', () => {
    expect(facade.descriptor.store).toBe(facade.store);
  });
});
