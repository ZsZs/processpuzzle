/** Why a fundamental-type operation refused its input. The Java side (`FundamentalTypeException.Reason`) uses the same names. */
export type FundamentalTypeErrorReason =
  | 'INVALID_AMOUNT'
  | 'UNKNOWN_UNIT'
  | 'UNKNOWN_CURRENCY'
  | 'UNKNOWN_KIND'
  | 'KIND_MISMATCH'
  | 'CURRENCY_MISMATCH'
  | 'OFFSET_ARITHMETIC'
  | 'DIVISION_BY_ZERO'
  | 'INVALID_DURATION'
  | 'CALENDAR_DURATION'
  | 'INVALID_CATALOG';

export class FundamentalTypeError extends Error {
  constructor(
    readonly reason: FundamentalTypeErrorReason,
    message: string,
  ) {
    super(message);
    this.name = 'FundamentalTypeError';
  }
}
