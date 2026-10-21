/** The contract's `TraceEntry`: one decision's value — single, per row, or per group — or its error. */
export interface TraceEntry {
  readonly decision: string;
  readonly kind: string;
  /** `scalar`, `row:<input>` or `group:<attribute>`. */
  readonly scope: string;
  readonly value?: unknown;
  readonly error?: string | null;
}

/** The contract's `EvaluationResult`. */
export interface EvaluationResult {
  readonly values?: Record<string, unknown>;
  readonly rows?: Record<string, unknown>;
  readonly groups?: Record<string, unknown>;
  readonly order?: string[];
  readonly trace?: TraceEntry[];
  /** Why the evaluation stopped; the trace then ends with the failing decision. */
  readonly error?: string | null;
}

/** The contract's `DecisionNode`. */
export interface DecisionNode {
  readonly name: string;
  readonly kind?: string;
  readonly scope: string;
}

/** The contract's `ValidationReport`: the errors, and for a source that parses, the dependency graph. */
export interface ValidationReport {
  readonly valid: boolean;
  readonly errors: string[];
  readonly order?: string[];
  readonly decisions?: DecisionNode[];
  readonly dependencies?: { readonly from: string; readonly to: string }[];
}

export type RunStatus = 'COMPLETED' | 'FAILED';

/** The contract's `CalculationRun`, without its trace — read that with `CalculationApiService.trace`. */
export interface CalculationRun {
  readonly id: string;
  readonly calculationId: string;
  readonly definitionVersion?: number;
  readonly channel?: 'DRAFT' | 'RELEASED';
  readonly triggeredBy?: 'MANUAL' | 'ON_CHANGE' | 'STATE' | 'FREEZE_STATE';
  readonly subjectType?: string | null;
  readonly subjectId?: string | null;
  readonly parameters?: Record<string, unknown>;
  readonly status: RunStatus;
  readonly error?: string | null;
  readonly result?: EvaluationResult;
  readonly written?: number;
  readonly frozen?: boolean;
  readonly frozenAt?: string | null;
  readonly definitionSource?: string;
  readonly startedAt?: string;
  readonly completedAt?: string | null;
}

/** Which row of a definition a request computes with. */
export type Revision = 'draft' | 'released';

/**
 * The contract's `CalculationOutput`: one attribute a released calculation stores its result in.
 * `parameter` is the entity parameter whose object is the output entity, `null` when there is none.
 */
export interface CalculationOutput {
  readonly calculationId: string;
  readonly attribute: string;
  readonly decision: string;
  readonly parameter?: string | null;
}
