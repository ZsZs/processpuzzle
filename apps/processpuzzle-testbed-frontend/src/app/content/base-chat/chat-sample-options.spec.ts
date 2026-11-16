import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
// eslint-disable-next-line @nx/enforce-module-boundaries
import { ChatDefinitionService } from '@processpuzzle/base-chat';
// eslint-disable-next-line @nx/enforce-module-boundaries
import { EntityDefinition, EntityDefinitionService } from '@processpuzzle/base-entity';
// eslint-disable-next-line @nx/enforce-module-boundaries
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it } from 'vitest';
import { ChatSampleOptions } from './chat-sample-options';

describe('ChatSampleOptions', () => {
  const root = 'http://localhost:8080/organizations/processpuzzle-testbed';
  const order = new EntityDefinition({
    code: 'order',
    name: 'Order',
    attributes: [
      { code: 'orderNumber', isLinkToDetails: true },
      { code: 'total' },
    ],
  } as never);
  const note = new EntityDefinition({ code: 'note', name: 'Note', attributes: [{ code: 'text' }] } as never);
  let chatDefinitions: { findAll: () => unknown };
  let entityDefinitions: { findAll: () => unknown };

  const options = () => TestBed.inject(ChatSampleOptions);
  /** records() waits for the definitions before it asks for the records; one turn of the event loop lets that happen. */
  const settle = () => new Promise((resolve) => setTimeout(resolve));

  beforeEach(() => {
    chatDefinitions = { findAll: () => of([]) };
    entityDefinitions = { findAll: () => of([order, note]) };
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: RUNTIME_CONFIGURATION, useValue: { BASE_CONFIGURATION: { ENTITY_SERVICE_ROOT: root, CHAT_SERVICE_ROOT: root } } },
        { provide: ChatDefinitionService, useValue: chatDefinitions },
        { provide: EntityDefinitionService, useValue: entityDefinitions },
      ],
    });
  });

  describe('definitions', () => {
    it('lists the chat definitions by name, each as an option', async () => {
      chatDefinitions.findAll = () =>
        of([
          { id: 'order-discussion', name: 'Order discussion', subjectType: 'ENTITY', subjectDefinition: 'order' },
          { id: 'onboarding-run-chat', name: 'Onboarding run', subjectType: 'WORKFLOW_INSTANCE', subjectDefinition: 'onboarding' },
        ]);

      expect(await options().definitions()).toEqual([
        { key: 'onboarding-run-chat', name: 'Onboarding run', subjectType: 'WORKFLOW_INSTANCE', subjectDefinition: 'onboarding' },
        { key: 'order-discussion', name: 'Order discussion', subjectType: 'ENTITY', subjectDefinition: 'order' },
      ]);
    });

    it('takes a paged answer as well as a list', async () => {
      chatDefinitions.findAll = () => of({ content: [{ id: 'only', name: 'Only', subjectType: 'ENTITY', subjectDefinition: 'order' }] });

      expect((await options().definitions()).map((option) => option.key)).toEqual(['only']);
    });

    it('offers nothing when the read fails', async () => {
      chatDefinitions.findAll = () => throwError(() => new Error('down'));

      expect(await options().definitions()).toEqual([]);
    });
  });

  describe('records', () => {
    it("reads the entity type's records and titles each by its isLinkToDetails attribute", async () => {
      const pending = options().records('order');
      await settle();
      const http = TestBed.inject(HttpTestingController);
      const request = http.expectOne(`${root}/entities/order?size=200`);
      expect(request.request.method).toBe('GET');
      request.flush({
        content: [
          { id: 'o-1', payload: { orderNumber: 'ORD-1001', total: 10 } },
          { id: 'o-2', payload: { orderNumber: '', total: 20 } },
          { id: 'o-3', payload: { orderNumber: 42 } },
        ],
      });

      expect(await pending).toEqual([
        { id: 'o-1', label: 'ORD-1001' },
        { id: 'o-2', label: undefined },
        { id: 'o-3', label: '42' },
      ]);
    });

    it('gives no label when the type has no title attribute', async () => {
      const pending = options().records('note');
      await settle();
      TestBed.inject(HttpTestingController)
        .expectOne(`${root}/entities/note?size=200`)
        .flush({ content: [{ id: 'n-1', payload: { text: 'hi' } }] });

      expect(await pending).toEqual([{ id: 'n-1', label: undefined }]);
    });

    it('gives no label to a title that is neither text nor a number', async () => {
      const pending = options().records('order');
      await settle();
      TestBed.inject(HttpTestingController)
        .expectOne(`${root}/entities/order?size=200`)
        .flush({ content: [{ id: 'o-4', payload: { orderNumber: { nested: true } } }] });

      expect(await pending).toEqual([{ id: 'o-4', label: undefined }]);
    });

    it('offers nothing when the definitions cannot be read', async () => {
      entityDefinitions.findAll = () => throwError(() => new Error('down'));

      expect(await options().records('order')).toEqual([]);
    });
  });
});
