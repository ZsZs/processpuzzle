/** One tab of the Calculations section: the child route it opens, its icon and its `design` scope label. */
export interface CalculationDesignerTab {
  /** Path of the child route, relative to `calculations`. */
  path: string;
  icon: string;
  /** Key in the `design` transloco scope — the scope the `/design` branch registers. */
  label: string;
}

/**
 * The tabs of the Calculations section, in the order they are shown: the definitions the author came for,
 * then the runs they produce — read-only apart from freezing, the same place the Workflows section gives
 * its instances.
 *
 * `calculation-definition` is `BASE_CALCULATION_ROUTES`' segment, `snakeCaseName('Calculation Definition')`,
 * which `BaseFormNavigatorSingletonStore` builds the details URL from; `calculation-runs` is
 * `CALCULATION_RUNS_ROUTES`'. A list of its own rather than derived from the children's `menuTitle`, whose
 * keys belong to the `base_calculation` scope — see `WORKFLOW_DESIGNER_TABS`, and `design.routes.spec.ts`,
 * which keeps the two in step. Separate from `design.routes.ts` to avoid the import cycle.
 */
export const CALCULATION_DESIGNER_TABS: CalculationDesignerTab[] = [
  { path: 'calculation-definition', icon: 'calculate', label: 'design.calculation-definitions' },
  { path: 'calculation-runs', icon: 'history', label: 'design.calculation-runs' },
];
