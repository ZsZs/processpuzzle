import { type EnvironmentProviders, InjectionToken, makeEnvironmentProviders } from '@angular/core';

/**
 * Metadata of a **working context**: an entity the user picks once and then works inside, instead of passing
 * through List → Form on every visit. Nothing in the platform knows what the entity is — a Race, a Customer, a
 * Warehouse — it only reads this declaration.
 *
 * See `docs/active-entity-concept/working-context-spec.md`.
 */
export interface WorkingContextDefinition {
  /** The context's name, and its URL segment: `ctx/<name>/<id>/...`. Lower-case, no slashes. */
  name: string;
  /** Entity type acting as the context root. Any entity with a registered descriptor and store may be named. */
  entityName: string;
  /** A parent context. Selecting another parent clears this context, because its routes nest below the parent's. */
  parent?: string;
  /**
   * Absolute path the `ctx/<name>` routes are mounted under, for selecting from a screen outside them — the
   * entity's own list. Inside a context route the URL already says where it is, and that wins.
   */
  basePath?: string;
  picker?: WorkingContextPicker;
  display?: WorkingContextDisplay;
  /**
   * Conditions on the **active** entity that make switching away from it — or clearing it — ask first. Keys are
   * attribute names; `state` names the entity's STATE attribute when it has no attribute of that name. A value
   * may be a list of alternatives. `{ state: 'Running' }`.
   */
  confirmSwitchWhen?: Record<string, unknown>;
  /** `url+localStorage` (default) restores the last context when a context view is opened without one. */
  persist?: WorkingContextPersistence;
}

export type WorkingContextPersistence = 'url' | 'url+localStorage';

export interface WorkingContextPicker {
  /** Attributes the picker's search matches against; the entity's title attribute when omitted. */
  searchFields?: string[];
  /** How many recently active entities the picker offers on top. Default 3; 0 turns the section off. */
  recent?: number;
}

/** What the status bar shows of the active entity. Templates use `{{attr}}` / `{{attr.nested}}` placeholders. */
export interface WorkingContextDisplay {
  /** The crumb's text; the entity's title attribute when omitted. */
  label?: string;
  /** Attribute rendered as a badge; `state` names the STATE attribute, as in {@link WorkingContextDefinition.confirmSwitchWhen}. */
  badge?: string;
  /** A second, quieter piece of text after the label — the current round, say. */
  extra?: string;
}

export const DEFAULT_RECENT_CONTEXTS = 3;

export const WORKING_CONTEXT_DEFINITIONS = new InjectionToken<WorkingContextDefinition[][]>('WORKING_CONTEXT_DEFINITIONS', { factory: () => [] });

/**
 * Declares working contexts. May be called more than once — an application and each module it mounts can
 * contribute their own — and the declarations add up.
 */
export function provideWorkingContexts(...definitions: WorkingContextDefinition[]): EnvironmentProviders {
  return makeEnvironmentProviders([{ provide: WORKING_CONTEXT_DEFINITIONS, useValue: definitions, multi: true }]);
}
