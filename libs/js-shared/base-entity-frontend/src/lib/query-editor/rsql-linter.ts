import { linter, Diagnostic } from '@codemirror/lint';
import { EditorView } from '@codemirror/view';
import { tokenize, validateStructure, RsqlToken } from './rsql-parser';
import { RsqlFieldMetadataProvider } from './rsql-field-metadata.model';

/**
 * Structural + semantic linter.
 * - Structural errors (unbalanced parens, missing operator/value, etc.) come from validateStructure().
 * - Semantic errors (unknown field, operator not valid for field type) are checked here
 *   against the injected RsqlFieldMetadataProvider — purely client-side, no round trip.
 *
 * If you also want server-side validation (e.g. value format checks against a live
 * enum list), debounce a call to your /rsql/validate endpoint and merge its diagnostics
 * in with these before returning — CodeMirror's linter() supports async source functions.
 */
export function createRsqlLinter(fieldMetadata: RsqlFieldMetadataProvider) {
  return linter((view: EditorView): Diagnostic[] => {
    const text = view.state.doc.toString();
    if (!text.trim()) return [];

    const diagnostics: Diagnostic[] = [];

    for (const err of validateStructure(text)) {
      diagnostics.push({
        from: err.from,
        to: Math.max(err.to, err.from + 1),
        severity: err.severity,
        message: err.message,
      });
    }

    // Only run semantic checks if structure is otherwise sound, to avoid noisy
    // double-reporting while the user is still mid-token.
    if (diagnostics.length === 0) {
      const tokens = tokenize(text).filter((t) => t.type !== 'whitespace');
      diagnostics.push(...checkSemantics(tokens, fieldMetadata));
    }

    return diagnostics;
  });
}

function checkSemantics(tokens: RsqlToken[], provider: RsqlFieldMetadataProvider): Diagnostic[] {
  const diagnostics: Diagnostic[] = [];

  for (let i = 0; i < tokens.length; i++) {
    const tok = tokens[i];
    if (tok.type !== 'selector') continue;

    const field = provider.getField(tok.value);
    if (!field) {
      diagnostics.push({
        from: tok.from,
        to: tok.to,
        severity: 'error',
        message: `Unknown field '${tok.value}'`,
      });
      continue;
    }

    const opTok = tokens[i + 1];
    if (opTok?.type === 'operator') {
      const allowed = field.operators ?? [];
      if (allowed.length > 0 && !allowed.includes(opTok.value)) {
        diagnostics.push({
          from: opTok.from,
          to: opTok.to,
          severity: 'error',
          message: `Operator '${opTok.value}' is not valid for '${field.name}' (${field.type}). Allowed: ${allowed.join(', ')}`,
        });
      }
    }

    if (field.type === 'enum' && field.enumValues?.length) {
      const valTok = tokens[i + 2];
      if (valTok?.type === 'value') {
        const raw = stripQuotes(valTok.value);
        const candidates = raw.split(',').map((v) => v.trim());
        for (const c of candidates) {
          if (c && !field.enumValues.includes(c)) {
            diagnostics.push({
              from: valTok.from,
              to: valTok.to,
              severity: 'warning',
              message: `'${c}' is not a known value for '${field.name}'. Expected one of: ${field.enumValues.join(', ')}`,
            });
          }
        }
      }
    }
  }

  return diagnostics;
}

function stripQuotes(v: string): string {
  return v.replace(/^['"]|['"]$/g, '');
}
