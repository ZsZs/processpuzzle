/**
 * Minimal RSQL/FIQL tokenizer + recursive-descent validator.
 *
 * Grammar (simplified FIQL/RSQL):
 *   input      := or-expr
 *   or-expr    := and-expr (',' and-expr)*
 *   and-expr   := constraint (';' constraint)*
 *   constraint := group | comparison
 *   group      := '(' or-expr ')'
 *   comparison := selector operator value
 *   value      := unquoted-value | "'" ... "'" | '"' ... '"' | '(' value (',' value)* ')'
 *
 * This is intentionally dependency-free so it can run both in the browser
 * (CodeMirror linter) and, if useful, be ported/mirrored server-side.
 */

export type RsqlTokenType =
  | 'selector'
  | 'operator'
  | 'value'
  | 'lparen'
  | 'rparen'
  | 'and'
  | 'or'
  | 'comma'
  | 'whitespace'
  | 'unknown';

export interface RsqlToken {
  type: RsqlTokenType;
  value: string;
  from: number;
  to: number;
}

export interface RsqlError {
  message: string;
  from: number;
  to: number;
  severity: 'error' | 'warning';
}

const OPERATOR_PATTERN = /^(==|!=|=gt=|=ge=|=lt=|=le=|=in=|=out=|=like=)/;
const SELECTOR_PATTERN = /^[a-zA-Z_][a-zA-Z0-9_.]*/;
const UNQUOTED_VALUE_PATTERN = /^[^,;()'"\s]+/;

export function tokenize(input: string): RsqlToken[] {
  const tokens: RsqlToken[] = [];
  let i = 0;

  while (i < input.length) {
    const ch = input[i];

    if (/\s/.test(ch)) {
      const start = i;
      while (i < input.length && /\s/.test(input[i])) i++;
      tokens.push({ type: 'whitespace', value: input.slice(start, i), from: start, to: i });
      continue;
    }

    if (ch === '(') {
      tokens.push({ type: 'lparen', value: '(', from: i, to: i + 1 });
      i++;
      continue;
    }

    if (ch === ')') {
      tokens.push({ type: 'rparen', value: ')', from: i, to: i + 1 });
      i++;
      continue;
    }

    if (ch === ';') {
      tokens.push({ type: 'and', value: ';', from: i, to: i + 1 });
      i++;
      continue;
    }

    if (ch === ',') {
      tokens.push({ type: 'comma', value: ',', from: i, to: i + 1 });
      i++;
      continue;
    }

    if (ch === "'" || ch === '"') {
      const quote = ch;
      const start = i;
      i++;
      while (i < input.length && input[i] !== quote) i++;
      const closed = input[i] === quote;
      i = closed ? i + 1 : i;
      tokens.push({ type: 'value', value: input.slice(start, i), from: start, to: i });
      if (!closed) {
        // mark as unknown-length token so the parser can flag unterminated string
        tokens[tokens.length - 1].type = 'unknown';
      }
      continue;
    }

    const rest = input.slice(i);

    const opMatch = rest.match(OPERATOR_PATTERN);
    // Only treat as operator if we're expecting one (after a selector) —
    // handled by caller context; here we just tokenize opportunistically
    // and let the parser decide validity by position.
    if (opMatch && looksLikeOperatorPosition(tokens)) {
      tokens.push({ type: 'operator', value: opMatch[0], from: i, to: i + opMatch[0].length });
      i += opMatch[0].length;
      continue;
    }

    if (looksLikeValuePosition(tokens)) {
      const valMatch = rest.match(UNQUOTED_VALUE_PATTERN);
      if (valMatch) {
        tokens.push({ type: 'value', value: valMatch[0], from: i, to: i + valMatch[0].length });
        i += valMatch[0].length;
        continue;
      }
    }

    const selMatch = rest.match(SELECTOR_PATTERN);
    if (selMatch) {
      tokens.push({ type: 'selector', value: selMatch[0], from: i, to: i + selMatch[0].length });
      i += selMatch[0].length;
      continue;
    }

    // Unrecognized character
    tokens.push({ type: 'unknown', value: ch, from: i, to: i + 1 });
    i++;
  }

  return tokens;
}

function lastMeaningful(tokens: RsqlToken[]): RsqlToken | undefined {
  for (let i = tokens.length - 1; i >= 0; i--) {
    if (tokens[i].type !== 'whitespace') return tokens[i];
  }
  return undefined;
}

function looksLikeOperatorPosition(tokens: RsqlToken[]): boolean {
  const last = lastMeaningful(tokens);
  return !!last && last.type === 'selector';
}

function looksLikeValuePosition(tokens: RsqlToken[]): boolean {
  const last = lastMeaningful(tokens);
  return !!last && last.type === 'operator';
}

/** Validates paren balance, clause structure and token ordering. Field/operator
 *  semantic checks (does field exist, is operator valid for its type) are layered
 *  on top by the linter using RsqlFieldMetadataProvider. */
export function validateStructure(input: string): RsqlError[] {
  const errors: RsqlError[] = [];
  const tokens = tokenize(input).filter((t) => t.type !== 'whitespace');

  if (tokens.length === 0) return errors;

  let depth = 0;
  let expect: 'selector' | 'operator' | 'value' | 'connector-or-close' = 'selector';

  for (let idx = 0; idx < tokens.length; idx++) {
    const tok = tokens[idx];

    if (tok.type === 'unknown') {
      errors.push({ message: `Unexpected character '${tok.value}'`, from: tok.from, to: tok.to, severity: 'error' });
      continue;
    }

    switch (expect) {
      case 'selector':
        if (tok.type === 'lparen') {
          depth++;
          // stay in 'selector' state for the nested expression
        } else if (tok.type === 'selector') {
          expect = 'operator';
        } else {
          errors.push({ message: `Expected a field name here`, from: tok.from, to: tok.to, severity: 'error' });
        }
        break;

      case 'operator':
        if (tok.type === 'operator') {
          expect = 'value';
        } else {
          errors.push({
            message: `Expected a comparison operator (==, !=, =gt=, =lt=, =in= ...) after '${tokens[idx - 1]?.value}'`,
            from: tok.from,
            to: tok.to,
            severity: 'error',
          });
        }
        break;

      case 'value':
        if (tok.type === 'value') {
          expect = 'connector-or-close';
        } else if (tok.type === 'lparen') {
          // start of value list e.g. field=in=(a,b,c) — consume until rparen
          let inner = 1;
          idx++;
          while (idx < tokens.length && inner > 0) {
            if (tokens[idx].type === 'lparen') inner++;
            if (tokens[idx].type === 'rparen') inner--;
            idx++;
          }
          idx--;
          expect = 'connector-or-close';
        } else {
          errors.push({ message: `Expected a value here`, from: tok.from, to: tok.to, severity: 'error' });
        }
        break;

      case 'connector-or-close':
        if (tok.type === 'and' || tok.type === 'comma') {
          expect = 'selector';
        } else if (tok.type === 'rparen') {
          depth--;
          if (depth < 0) {
            errors.push({ message: `Unmatched ')'`, from: tok.from, to: tok.to, severity: 'error' });
            depth = 0;
          }
        } else {
          errors.push({
            message: `Expected ';' (AND), ',' (OR) or ')' here`,
            from: tok.from,
            to: tok.to,
            severity: 'error',
          });
        }
        break;
    }
  }

  if (expect === 'selector' && tokens.length > 0) {
    const last = tokens[tokens.length - 1];
    errors.push({ message: `Query ends unexpectedly, expected a field name`, from: last.to, to: last.to, severity: 'error' });
  }
  if (expect === 'operator') {
    const last = tokens[tokens.length - 1];
    errors.push({ message: `Missing comparison operator`, from: last.to, to: last.to, severity: 'error' });
  }
  if (expect === 'value') {
    const last = tokens[tokens.length - 1];
    errors.push({ message: `Missing value`, from: last.to, to: last.to, severity: 'error' });
  }
  if (depth > 0) {
    const last = tokens[tokens.length - 1];
    errors.push({ message: `Missing ${depth} closing ')'`, from: last.to, to: last.to, severity: 'error' });
  }

  return errors;
}
