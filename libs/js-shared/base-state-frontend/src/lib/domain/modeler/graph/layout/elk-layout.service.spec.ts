import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { State, Transition } from '../../../definition/state-machine-definition';
import { STATE_NODE_TYPE, StateNode, TransitionEdge } from '../state-machine-graph';
import { ElkLayoutService } from './elk-layout.service';

/**
 * `ElkLayoutService` is deliberately not exported from `public-api.ts` — exporting it pulls elkjs, a
 * large non-ESM bundle, into every consuming application for a service nothing calls yet. That leaves it
 * outside the library build's reach, so this spec is what keeps it compiled and honest: without it the
 * file would rot silently, exactly as the modeler's first scaffold did.
 */
describe('ElkLayoutService', () => {
  let service: ElkLayoutService;

  const node = (key: string, size?: { width: number; height: number }): StateNode => ({
    id: key,
    type: STATE_NODE_TYPE,
    position: { x: 0, y: 0 },
    ...(size ? { size } : {}),
    data: { state: new State({ key, name: key }), label: key, initial: false },
  });

  const edge = (key: string, source: string, target: string): TransitionEdge => ({
    id: key,
    source,
    target,
    data: { transition: new Transition({ key, sourceStateKey: source, targetStateKey: target }), label: key },
  });

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [ElkLayoutService] });
    service = TestBed.inject(ElkLayoutService);
  });

  it('spreads the graph out left to right', async () => {
    const nodes = [node('DRAFT'), node('DELIVERED')];

    const placed = await service.layout(nodes, [edge('confirm', 'DRAFT', 'DELIVERED')]);

    expect(placed.map((placedNode) => placedNode.id)).toEqual(['DRAFT', 'DELIVERED']);
    expect(placed[1].position.x).toBeGreaterThan(placed[0].position.x);
  });

  it('does not mutate the nodes it was given', async () => {
    const nodes = [node('DRAFT'), node('DELIVERED')];

    await service.layout(nodes, [edge('confirm', 'DRAFT', 'DELIVERED')]);

    expect(nodes.map((given) => given.position)).toEqual([
      { x: 0, y: 0 },
      { x: 0, y: 0 },
    ]);
  });

  // ELK rejects an edge whose ends it does not know, and a transition may name a dropped state.
  it('ignores an edge whose ends are not both in the graph', async () => {
    const placed = await service.layout([node('DRAFT')], [edge('cancel', 'DRAFT', 'GONE')]);

    expect(placed).toHaveLength(1);
  });
});
