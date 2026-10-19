import type { ReleaseState } from '@processpuzzle/util';
import { BaseEntity } from '@processpuzzle/base-entity';

/**
 * What a new definition starts from: the smallest source that validates, so the author edits rather than types
 * the structure from nothing.
 */
export const NEW_CALCULATION_SOURCE = `calculation: new-calculation
name: New calculation
parameters: {}
inputs: {}
decisions:
  result:
    expr: "1 + 1"
`;

/** One parameter a calculation declares — an entity parameter takes an object id, a plain one a value. */
export interface CalculationParameter {
  readonly name: string;
  /** An entity parameter's entity definition code, e.g. `order`. */
  readonly entity?: string;
  /** A plain parameter's type: `string`, `number`, `boolean`, `date` or `date-time`. */
  readonly type?: string;
}

/**
 * One calculation of the organization — the contract's `CalculationDefinition`.
 *
 * Authored as YAML: {@link source} is the definition, comments included, and everything else here is read from
 * it by the backend on save — the {@link id} is its `calculation` key. So only the source and the optimistic lock
 * are ever sent back.
 */
export class CalculationDefinition implements BaseEntity {
  id: string;
  name?: string;
  description?: string;
  source: string;
  /** Moves on with every save that changes the source; what a run records. */
  definitionVersion?: number;
  parameters: CalculationParameter[];
  /** The entity the output writes, e.g. `order`. */
  outputEntity?: string;
  version?: number;
  releaseState?: ReleaseState;

  constructor(init: Partial<CalculationDefinition> = {}) {
    this.id = init.id ?? '';
    this.name = init.name;
    this.description = init.description;
    this.source = init.source ?? NEW_CALCULATION_SOURCE;
    this.definitionVersion = init.definitionVersion;
    this.parameters = init.parameters ?? [];
    this.outputEntity = init.outputEntity;
    this.version = init.version;
    this.releaseState = init.releaseState;
  }
}
