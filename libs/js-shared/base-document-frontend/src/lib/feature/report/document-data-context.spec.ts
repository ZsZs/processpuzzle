import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { EntityDefinition, EntityDefinitionRegistry } from '@processpuzzle/base-entity';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { beforeEach, describe, expect, it } from 'vitest';
import { DocumentInputPort, PortType } from '../../domain/base-document';
import { DocumentDataContext } from './document-data-context';

const ROOT = 'http://localhost:3000/organizations/processpuzzle-testbed';
const ORDER = new EntityDefinition({ code: 'order', name: 'Order', attributes: [{ code: 'orderNumber', formControlType: 'TEXT_BOX', isLinkToDetails: true }] });

const settle = () => new Promise<void>((resolve) => setTimeout(resolve, 0));

function port(name: string, type: PortType, patch: Partial<DocumentInputPort> = {}): DocumentInputPort {
  return Object.assign(new DocumentInputPort(name, type), { entityType: 'Order', ...patch });
}

function envelope(id: string, payload: Record<string, unknown>) {
  return { id, version: 1, entityDefinitionCode: 'order', payload };
}

describe('DocumentDataContext', () => {
  let context: DocumentDataContext;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: RUNTIME_CONFIGURATION, useValue: { BASE_CONFIGURATION: { BACKEND_SERVICE_ROOT: ROOT } } },
        { provide: EntityDefinitionRegistry, useValue: { byName: async (name?: string) => (name === 'Order' ? ORDER : undefined) } },
        DocumentDataContext,
      ],
    });
    context = TestBed.inject(DocumentDataContext);
    http = TestBed.inject(HttpTestingController);
  });

  it('fetches the object an ENTITY_REF port is given and resolves paths below it', async () => {
    const bound = context.bind([port('order', PortType.ENTITY_REF, { required: true })], { order: 'o-1' });
    await settle();
    http.expectOne(`${ROOT}/entities/order/o-1`).flush(envelope('o-1', { orderNumber: 'A-1', customer: { name: 'Ada' }, lineItems: [{ qty: 2 }] }));
    await bound;

    expect(context.resolve('order.orderNumber')).toBe('A-1');
    expect(context.resolve('order.customer.name')).toBe('Ada');
    expect(context.resolve('order.lineItems')).toEqual([{ qty: 2 }]);
    expect(context.resolve('order')).toMatchObject({ id: 'o-1' });
    expect(context.isComplete()).toBe(true);
    expect(context.loading()).toBe(false);
  });

  it('reports a required port without a value or default, and fetches nothing for it', async () => {
    await context.bind([port('order', PortType.ENTITY_REF, { required: true })], {});

    expect(context.missingRequiredPorts()).toEqual(['order']);
    expect(context.resolve('order.orderNumber')).toBeUndefined();
    http.expectNone(() => true);
  });

  it('queries a collection port with the default filter, substituting references to other ports', async () => {
    const ports = [port('order', PortType.ENTITY_REF, { defaultValue: 'o-1' }), port('siblings', PortType.ENTITY_COLLECTION, { defaultRsqlFilter: 'customer==${order.customer}' })];
    const bound = context.bind(ports, {});
    await settle();
    http.expectOne(`${ROOT}/entities/order/o-1`).flush(envelope('o-1', { customer: 'c-9' }));
    await settle();
    await settle();
    const query = http.expectOne((request) => request.url.startsWith(`${ROOT}/entities/order?`));
    expect(query.request.url).toContain('rsql=customer%3D%3Dc-9');
    query.flush({ content: [envelope('o-1', { customer: 'c-9' }), envelope('o-2', { customer: 'c-9' })], number: 0, size: 2, totalElements: 2, totalPages: 1 });
    await bound;

    expect((context.resolve('siblings') as unknown[]).length).toBe(2);
  });

  it('leaves a collection empty instead of running its query without an unresolved reference', async () => {
    await context.bind([port('siblings', PortType.ENTITY_COLLECTION, { defaultRsqlFilter: 'customer==${order.customer}' })], {});

    expect(context.resolve('siblings')).toEqual([]);
    http.expectNone(() => true);
  });

  it('prefers the host filter over the default, and reads an empty answer as no rows', async () => {
    const bound = context.bind([port('open', PortType.ENTITY_COLLECTION, { defaultRsqlFilter: 'status==OPEN' })], { open: 'status==CLOSED' });
    await settle();
    await settle();
    const query = http.expectOne((request) => request.url.startsWith(`${ROOT}/entities/order?`));
    expect(query.request.url).toContain('CLOSED');
    query.flush(null, { status: 204, statusText: 'No Content' });
    await bound;

    expect(context.resolve('open')).toEqual([]);
    expect(context.error()).toBeUndefined();
  });

  it('passes plain ports through and resolves nothing for an unknown entity type', async () => {
    await context.bind([port('note', PortType.STRING, { defaultValue: 'Thanks' }), port('other', PortType.ENTITY_REF, { entityType: 'Nope' }), port('list', PortType.ENTITY_COLLECTION, { entityType: 'Nope' })], { other: 'x' });

    expect(context.resolve('note')).toBe('Thanks');
    expect(context.resolve('other')).toBeUndefined();
    expect(context.resolve('list')).toEqual([]);
  });

  it('records a failed fetch as its error', async () => {
    const bound = context.bind([port('order', PortType.ENTITY_REF)], { order: 'o-1' });
    await settle();
    http.expectOne(`${ROOT}/entities/order/o-1`).flush('boom', { status: 500, statusText: 'Server Error' });
    await bound;

    expect(context.error()).toBeTruthy();
    expect(context.loading()).toBe(false);
  });

  it('drops the answers of a bind a newer one superseded', async () => {
    const first = context.bind([port('order', PortType.ENTITY_REF)], { order: 'o-1' });
    await settle();
    const second = context.bind([port('order', PortType.ENTITY_REF)], { order: 'o-2' });
    await settle();
    http.expectOne(`${ROOT}/entities/order/o-2`).flush(envelope('o-2', { orderNumber: 'B-2' }));
    await second;
    http.expectOne(`${ROOT}/entities/order/o-1`).flush(envelope('o-1', { orderNumber: 'A-1' }));
    await first;

    expect(context.resolve('order.orderNumber')).toBe('B-2');
  });

  it('lists objects for a picker, labelled by the attribute that titles them', async () => {
    const listed = context.listObjects('Order');
    await settle();
    await settle();
    http.expectOne((request) => request.url.startsWith(`${ROOT}/entities/order?`)).flush([envelope('o-1', { orderNumber: 'A-1' }), envelope('o-2', {})]);

    expect(await listed).toEqual([
      { id: 'o-1', label: 'A-1' },
      { id: 'o-2', label: 'o-2' },
    ]);
    expect(await context.listObjects('Nope')).toEqual([]);
  });

  it('lists nothing when the entity has no objects', async () => {
    const listed = context.listObjects('Order');
    await settle();
    await settle();
    http.expectOne((request) => request.url.startsWith(`${ROOT}/entities/order?`)).flush(null, { status: 204, statusText: 'No Content' });

    expect(await listed).toEqual([]);
  });
});
