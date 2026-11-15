import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ResolvedEmbeddedAggregate } from '../base-entity-embedded/embedded-aggregate.accessor';
import { DirectEmbeddedWriter } from './direct-embedded-writer';
import { EmbeddedCollectionService } from './embedded-collection.service';

const payload = {
  id: 'W1',
  zones: [
    { id: 'z1', name: 'Cold', itemVersion: 3 },
    { id: 'z2', name: 'Dry', itemVersion: 1 },
  ],
};

function aggregate(overrides: Partial<ResolvedEmbeddedAggregate['context']> = {}) {
  const reload = vi.fn(async () => undefined);
  const resolved = {
    context: { rootEntityName: 'Warehouse', rootId: 'W1', viaWorkingContext: 'warehouse', path: [], attrName: 'zones', entityName: 'Zone', ...overrides },
    rootStore: { reload } as unknown as ResolvedEmbeddedAggregate['rootStore'],
    rootPayload: payload,
  } as ResolvedEmbeddedAggregate;
  return { resolved, reload };
}

describe('DirectEmbeddedWriter', () => {
  const collections = {
    append: vi.fn(async (_e: string, _i: string, _a: string, item: object) => ({ ...item, id: 'new', itemVersion: 1 })),
    appendNested: vi.fn(async (_e: string, _i: string, _a: string, _o: string, _c: string, item: object) => ({ ...item, id: 'new', itemVersion: 1 })),
    patch: vi.fn(async () => ({ id: 'z1', name: 'Frozen', itemVersion: 4 })),
    patchNested: vi.fn(async () => ({ id: 's1', itemVersion: 2 })),
    remove: vi.fn(async () => undefined),
    removeNested: vi.fn(async () => undefined),
  };
  let writer: DirectEmbeddedWriter;

  beforeEach(() => {
    Object.values(collections).forEach((fn) => fn.mockClear());
    TestBed.configureTestingModule({ providers: [{ provide: EmbeddedCollectionService, useValue: collections }] });
    writer = TestBed.inject(DirectEmbeddedWriter);
  });

  it('handles only an aggregate whose root is a working context', () => {
    expect(writer.handles(aggregate().resolved)).toBe(true);
    expect(writer.handles(aggregate({ viaWorkingContext: undefined }).resolved)).toBe(false);
  });

  it("appends a row of the context's entity through the item endpoint, then reads the root again", async () => {
    const { resolved, reload } = aggregate();

    const stored = await writer.append(resolved, { name: 'Ambient' });

    expect(collections.append).toHaveBeenCalledWith('Warehouse', 'W1', 'zones', { name: 'Ambient' });
    expect(stored).toMatchObject({ id: 'new', itemVersion: 1 });
    expect(reload).toHaveBeenCalledWith('W1');
  });

  it("appends a row nested in another through the owner's id", async () => {
    const { resolved } = aggregate({ path: [{ attrName: 'zones', index: 1 }], attrName: 'shelves', entityName: 'Shelf' });

    await writer.append(resolved, { code: 'A1' });

    expect(collections.appendNested).toHaveBeenCalledWith('Warehouse', 'W1', 'zones', 'z2', 'shelves', { code: 'A1' });
  });

  it("sends only what changed, against the row's own version", async () => {
    const { resolved } = aggregate();

    await writer.patch(resolved, payload.zones[0], { ...payload.zones[0], name: 'Frozen' });

    expect(collections.patch).toHaveBeenCalledWith('Warehouse', 'W1', 'zones', 'z1', { name: 'Frozen' }, '3');
  });

  it('sends nothing for a row that did not change', async () => {
    const { resolved, reload } = aggregate();

    const result = await writer.patch(resolved, payload.zones[0], { ...payload.zones[0] });

    expect(collections.patch).not.toHaveBeenCalled();
    expect(reload).not.toHaveBeenCalled();
    expect(result).toEqual(payload.zones[0]);
  });

  it('patches and removes a nested row below its owner', async () => {
    const { resolved } = aggregate({ path: [{ attrName: 'zones', index: 0 }], attrName: 'shelves', entityName: 'Shelf' });
    const shelf = { id: 's1', code: 'A1', itemVersion: 5 };

    await writer.patch(resolved, shelf, { ...shelf, code: 'B2' });
    await writer.remove(resolved, shelf);

    expect(collections.patchNested).toHaveBeenCalledWith('Warehouse', 'W1', 'zones', 'z1', 'shelves', 's1', { code: 'B2' }, '5');
    expect(collections.removeNested).toHaveBeenCalledWith('Warehouse', 'W1', 'zones', 'z1', 'shelves', 's1', '5');
  });

  it('removes a top-level row against its version', async () => {
    const { resolved, reload } = aggregate();

    await writer.remove(resolved, payload.zones[1]);

    expect(collections.remove).toHaveBeenCalledWith('Warehouse', 'W1', 'zones', 'z2', '1');
    expect(reload).toHaveBeenCalledWith('W1');
  });

  it('says what to do about a row that has no id yet, instead of guessing one', async () => {
    const { resolved } = aggregate();

    await expect(writer.remove(resolved, { name: 'Old' })).rejects.toThrow(/no id yet.*Save its Warehouse once/);
    expect(collections.remove).not.toHaveBeenCalled();
  });

  it('refuses a row nested deeper than the server addresses', async () => {
    const { resolved } = aggregate({
      path: [
        { attrName: 'zones', index: 0 },
        { attrName: 'shelves', index: 0 },
      ],
      attrName: 'bins',
      entityName: 'Bin',
    });

    await expect(writer.append(resolved, { code: 'x' })).rejects.toThrow(/nested too deep/);
  });
});
