import { firstValueFrom } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { BaseEntity } from '../base-entity/base-entity';
import { BaseEntityService } from './base-entity.service';
import { EmbeddedAggregateAccessor, ResolvedEmbeddedAggregate } from '../base-entity-embedded/embedded-aggregate.accessor';
import { EmbeddedEntityService } from './embedded-entity.service';

const ENTITY_NAME = 'Embedded Component';

/** The rows the testbed's `Embedded Component` carries; enough shape to tell a replace from an append. */
interface EmbeddedComponentRow extends BaseEntity {
  name?: string;
}

function anAggregate() {
  return {
    id: '1',
    name: 'entity_1',
    embeddedComponents: [
      { id: 'embedded_1_1', name: 'embedded_one' },
      { id: 'embedded_1_2', name: 'embedded_two' },
    ],
  };
}

/**
 * Stands in for the accessor, which is the piece that knows an embedded write is a write of the containing
 * document. `stageRoot` records what would have been staged.
 */
function setupService({ rootPayload = anAggregate() as unknown, path = [] as { attrName: string; index: number }[], attrName = 'embeddedComponents', referenceIdField = 'id', resolvable = true } = {}) {
  const written: Record<string, unknown>[] = [];
  const resolved = {
    context: { rootEntityName: 'Test Entity', rootId: '1', path, attrName, entityName: ENTITY_NAME, referenceIdField },
    rootStore: {} as ResolvedEmbeddedAggregate['rootStore'],
    rootPayload,
  } as ResolvedEmbeddedAggregate;

  const accessor = {
    resolve: vi.fn(() => (resolvable ? resolved : undefined)),
    stageRoot: vi.fn((_resolved: ResolvedEmbeddedAggregate, payload: Record<string, unknown>) => {
      written.push(payload);
    }),
  } as unknown as EmbeddedAggregateAccessor;

  // Typed as the interface the store actually calls: findByQuery is handed a query there, and ignoring it
  // is this repository's business, not the caller's.
  const service: BaseEntityService<EmbeddedComponentRow> = new EmbeddedEntityService<EmbeddedComponentRow>(ENTITY_NAME, accessor);
  return { service, accessor, written };
}

describe('EmbeddedEntityService', () => {
  let harness: ReturnType<typeof setupService>;

  beforeEach(() => {
    harness = setupService();
  });

  describe('reads', () => {
    it('returns the rows of its attribute', async () => {
      await expect(firstValueFrom(harness.service.findByQuery({}))).resolves.toEqual([
        { id: 'embedded_1_1', name: 'embedded_one' },
        { id: 'embedded_1_2', name: 'embedded_two' },
      ]);
    });

    it('finds a single row, and reports a miss', async () => {
      await expect(firstValueFrom(harness.service.findById('embedded_1_2'))).resolves.toEqual({ id: 'embedded_1_2', name: 'embedded_two' });
      await expect(firstValueFrom(harness.service.findById('missing'))).resolves.toBeUndefined();
    });

    it('reads rows nested one level deeper', async () => {
      const nested = setupService({
        rootPayload: { id: '1', embeddedComponents: [{ id: 'embedded_1_1', embeddedDetails: [{ id: 'detail_a' }] }] },
        path: [{ attrName: 'embeddedComponents', index: 0 }],
        attrName: 'embeddedDetails',
      });

      await expect(firstValueFrom(nested.service.findByQuery({}))).resolves.toEqual([{ id: 'detail_a' }]);
    });

    /**
     * A store loads as soon as it is injected, which can happen before the route that gives it an owner has
     * activated — so a read with no aggregate open is normal, and answered with nothing.
     */
    it('reads as empty when no aggregate is open', async () => {
      const orphaned = setupService({ resolvable: false });

      await expect(firstValueFrom(orphaned.service.findByQuery({}))).resolves.toEqual([]);
      await expect(firstValueFrom(orphaned.service.findAll())).resolves.toEqual([]);
    });
  });

  describe('writes', () => {
    it('appends a row and persists the whole document', async () => {
      const added = await firstValueFrom(harness.service.add({ id: 'embedded_1_3' }));

      expect(added).toEqual({ id: 'embedded_1_3' });
      expect(harness.written).toHaveLength(1);
      expect(harness.written[0]['embeddedComponents']).toHaveLength(3);
      // The rest of the document rides along untouched — it is replaced wholesale.
      expect(harness.written[0]['name']).toBe('entity_1');
    });

    it('replaces a row in place', async () => {
      await firstValueFrom(harness.service.update({ id: 'embedded_1_1', name: 'renamed' }));

      expect(harness.written[0]['embeddedComponents']).toEqual([{ id: 'embedded_1_1', name: 'renamed' }, { id: 'embedded_1_2', name: 'embedded_two' }]);
    });

    it('removes a row', async () => {
      await firstValueFrom(harness.service.delete('embedded_1_1'));

      expect(harness.written[0]['embeddedComponents']).toEqual([{ id: 'embedded_1_2', name: 'embedded_two' }]);
    });

    it('clears every row', async () => {
      await firstValueFrom(harness.service.deleteAll());

      expect(harness.written[0]['embeddedComponents']).toEqual([]);
    });

    it('writes a nested level without disturbing its siblings', async () => {
      const nested = setupService({
        rootPayload: { id: '1', embeddedComponents: [{ id: 'a', embeddedDetails: [] }, { id: 'b', embeddedDetails: [{ id: 'keep' }] }] },
        path: [{ attrName: 'embeddedComponents', index: 0 }],
        attrName: 'embeddedDetails',
      });

      await firstValueFrom(nested.service.add({ id: 'detail_new' }));

      const components = nested.written[0]['embeddedComponents'] as Record<string, unknown>[];
      expect(components[0]['embeddedDetails']).toEqual([{ id: 'detail_new' }]);
      expect(components[1]['embeddedDetails']).toEqual([{ id: 'keep' }]);
    });

    // App Region has no `id`; `type` identifies it.
    it('identifies rows by referenceIdField when the child has no id', async () => {
      const regions = setupService({
        rootPayload: { id: '1', regions: [{ type: 'header' }, { type: 'sidenav' }] },
        attrName: 'regions',
        referenceIdField: 'type',
      });

      await firstValueFrom(regions.service.delete('header'));

      expect(regions.written[0]['regions']).toEqual([{ type: 'sidenav' }]);
    });

    /** Silently writing nowhere would look like a successful save, so the entity is named instead. */
    it('refuses to write when no aggregate is open', async () => {
      const orphaned = setupService({ resolvable: false });

      await expect(firstValueFrom(orphaned.service.add({ id: 'x' }))).rejects.toThrow(/reachable only through the entity that contains it/);
      await expect(firstValueFrom(orphaned.service.delete('x'))).rejects.toThrow(/Embedded Component/);
    });
  });
});

describe('EmbeddedEntityService — in a working context', () => {
  const rows = [
    { id: 'z1', name: 'Cold', itemVersion: 2 },
    { id: 'z2', name: 'Dry', itemVersion: 1 },
  ];

  function setup(viaWorkingContext: string | undefined) {
    const resolved = {
      context: { rootEntityName: 'Warehouse', rootId: 'W1', viaWorkingContext, path: [], attrName: 'zones', entityName: ENTITY_NAME, referenceIdField: 'id' },
      rootStore: {} as ResolvedEmbeddedAggregate['rootStore'],
      rootPayload: { id: 'W1', zones: rows },
    } as ResolvedEmbeddedAggregate;
    const accessor = { resolve: vi.fn(() => resolved), stageRoot: vi.fn() } as unknown as EmbeddedAggregateAccessor;
    const direct = {
      handles: vi.fn((aggregate: ResolvedEmbeddedAggregate) => aggregate.context.viaWorkingContext !== undefined),
      append: vi.fn(async (_resolved: ResolvedEmbeddedAggregate, row: object) => ({ ...row, id: 'new', itemVersion: 1 })),
      patch: vi.fn(async (_resolved: ResolvedEmbeddedAggregate, _before: object, after: object) => ({ ...after, itemVersion: 3 })),
      remove: vi.fn(async () => undefined),
    };
    const service: BaseEntityService<EmbeddedComponentRow> = new EmbeddedEntityService<EmbeddedComponentRow>(ENTITY_NAME, accessor, direct as never);
    return { service, accessor, direct };
  }

  it('appends through the writer, and stages nothing in the root document', async () => {
    const { service, accessor, direct } = setup('warehouse');

    const stored = await firstValueFrom(service.add({ name: 'Ambient' } as EmbeddedComponentRow));

    expect(direct.append).toHaveBeenCalledTimes(1);
    expect(stored).toMatchObject({ id: 'new', itemVersion: 1 });
    expect(accessor.stageRoot).not.toHaveBeenCalled();
  });

  it('updates through the writer, handing it the stored row to compare with', async () => {
    const { service, accessor, direct } = setup('warehouse');

    const stored = await firstValueFrom(service.update({ id: 'z1', name: 'Frozen', itemVersion: 2 } as never));

    expect(direct.patch).toHaveBeenCalledWith(expect.anything(), rows[0], { id: 'z1', name: 'Frozen', itemVersion: 2 });
    expect(stored).toMatchObject({ name: 'Frozen', itemVersion: 3 });
    expect(accessor.stageRoot).not.toHaveBeenCalled();
  });

  it('deletes through the writer', async () => {
    const { service, direct } = setup('warehouse');

    await firstValueFrom(service.delete('z2'));

    expect(direct.remove).toHaveBeenCalledWith(expect.anything(), rows[1]);
  });

  it('still stages the whole document where the aggregate is not a working context', async () => {
    const { service, accessor, direct } = setup(undefined);

    await firstValueFrom(service.delete('z2'));

    expect(direct.remove).not.toHaveBeenCalled();
    expect(accessor.stageRoot).toHaveBeenCalledTimes(1);
  });
});
