import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { BASE_ENTITY_FACADE_REGISTRY } from '@processpuzzle/base-entity';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { beforeEach, describe, expect, it } from 'vitest';
import { BASE_CHAT_ENTITY_FACADES, BASE_CHAT_FACADE_PROVIDERS } from './base-chat.providers';
import { ChatDefinition } from './domain/chat-definition';
import { CHAT_DEFINITION_ENTITY_NAME } from './domain/chat-entity-names';

describe('BASE_CHAT facade providers', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: RUNTIME_CONFIGURATION, useValue: { BASE_CONFIGURATION: { CHAT_SERVICE_ROOT: 'https://localhost:8080/organizations/acme' } } },
        ...BASE_CHAT_FACADE_PROVIDERS,
        { provide: BASE_ENTITY_FACADE_REGISTRY, useValue: BASE_CHAT_ENTITY_FACADES },
      ],
    });
  });

  it('resolves the chat definition through the registry', () => {
    const registry = TestBed.inject(BASE_ENTITY_FACADE_REGISTRY);
    const facade = TestBed.inject(registry[CHAT_DEFINITION_ENTITY_NAME]);

    expect(Object.keys(registry)).toEqual(['Chat Definition']);
    expect(facade.entityType).toBe(ChatDefinition);
  });
});
