import { describe, expect, it } from 'vitest';
import { activeEdgeRouting, DEFAULT_EDGE_ROUTING, EDGE_ROUTING_CHOICES } from './edge-routing-options';

describe('edge routing options', () => {
  it('offers the three built-in routings, right angles first', () => {
    expect(EDGE_ROUTING_CHOICES).toEqual(['orthogonal', 'polyline', 'bezier']);
  });

  describe('activeEdgeRouting', () => {
    it('reads back a routing the edge names', () => {
      expect(activeEdgeRouting('bezier')).toBe('bezier');
    });

    // The normal case: most edges have never been through the menu.
    it('reads an unset routing as the default', () => {
      expect(activeEdgeRouting(undefined)).toBe(DEFAULT_EDGE_ROUTING);
    });

    /**
     * A layout written by a later version, or edited by hand. The default is what ng-diagram falls back to
     * for an unknown routing anyway, so ticking it says what is on screen — and leaves the menu usable.
     */
    it('reads a routing it does not offer as the default rather than ticking nothing', () => {
      expect(activeEdgeRouting('sinusoid')).toBe(DEFAULT_EDGE_ROUTING);
    });
  });
});
