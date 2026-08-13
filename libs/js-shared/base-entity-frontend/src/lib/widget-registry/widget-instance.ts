import { BaseEntity } from '../base-entity/base-entity';

/**
 * Frontend model of the canonical `WidgetInstance` schema of `shared-api.yaml`.
 *
 * PLACEMENT: here rather than in base-app-frontend or base-document-frontend for the same
 * reason {@link WIDGET_REGISTRY} lives here — both need it and neither depends on the other,
 * while base-entity-frontend is the lib both already depend on. It moves to base-widget-frontend
 * together with the registry; see docs/app-definition-refinement-plan.md, phase 2.
 *
 * A `WidgetInstance` is one *placement* of a widget: a registry key plus the props bound to it
 * at this position. It is never standalone — it always lives inside a container (a base-app page
 * or region, a base-document WIDGET block). The widget *type* it names is described by a
 * `WidgetDefinition`, a separate resource; that distinction is what keeps design-time
 * configuration apart from the widget's own contract.
 */

export const WIDGET_PLACEMENTS = ['STANDALONE', 'REFERENCED'] as const;
export type WidgetPlacement = (typeof WIDGET_PLACEMENTS)[number];

/**
 * Enum-style accessor for {@link WidgetPlacement}, merged with the type of the same name so both
 * `WidgetPlacement.STANDALONE` and the `WIDGET_PLACEMENTS` array work off one declaration. The
 * array form is what `toSelectables()` needs to build the placement dropdown; the object form is
 * what base-document's editor code already reads. A TypeScript `enum` would give only the latter.
 */
export const WidgetPlacement = {
  STANDALONE: 'STANDALONE',
  REFERENCED: 'REFERENCED',
} as const satisfies Record<Uppercase<WidgetPlacement>, WidgetPlacement>;

export class WidgetInstance implements BaseEntity {
  /** Authored, not generated: it is the trackBy key of the render loop and unique within its owner. */
  id: string;
  /** Widget registry key, an open string by contract. */
  type: string;
  /**
   * A container widget type composes through `props.childIds` — the ids of siblings in this same
   * list — rather than by nesting, so this stays the only place a widget's structure is expressed.
   */
  props?: Record<string, unknown>;
  /**
   * `REFERENCED` opts the widget out of rendering at its own position, leaving it to be placed by
   * whatever names it in `props.childIds`. Left undefined rather than defaulted, so a widget the
   * designer never touched keeps the payload the schema describes — the server reads an absent
   * value as `STANDALONE`.
   */
  placement?: WidgetPlacement;
  /**
   * Maps a widget prop name to a port of the enclosing container. Used by base-document, whose
   * documents declare `inputPorts`; base-app leaves both binding maps undefined, because a page or
   * region declares no ports for them to resolve against.
   */
  inputBindings?: Record<string, string>;
  /** The reverse of {@link inputBindings}: widget event name -> container output port name. */
  outputBindings?: Record<string, string>;

  constructor(init: Partial<WidgetInstance> = {}) {
    this.id = init.id ?? '';
    this.type = init.type ?? '';
    this.props = init.props;
    this.placement = init.placement;
    this.inputBindings = init.inputBindings;
    this.outputBindings = init.outputBindings;
  }
}
