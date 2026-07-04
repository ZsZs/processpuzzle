import { CompletionContext, CompletionResult, Completion } from '@codemirror/autocomplete';
import { tokenize } from './rsql-parser';
import { RsqlFieldMetadataProvider, RSQL_OPERATORS_BY_TYPE } from './rsql-field-metadata.model';

/**
 * Determines what kind of token is expected at the cursor by looking at the
 * last meaningful token before it, then offers the appropriate completions:
 * field names -> operators (scoped to that field's type) -> values (enum/bool/date hint) -> connectors.
 */
export function createRsqlCompletionSource(fieldMetadata: RsqlFieldMetadataProvider) {
  return (context: CompletionContext): CompletionResult | null => {
    const textBefore = context.state.doc.toString().slice(0, context.pos);
    const tokens = tokenize(textBefore).filter((t) => t.type !== 'whitespace');
    const last = tokens[tokens.length - 1];

    // Start of query, start of a new clause, or right after '(' -> suggest fields
    if (!last || last.type === 'and' || last.type === 'comma' || last.type === 'lparen') {
      return fieldCompletions(context, fieldMetadata, context.pos);
    }

    // Mid-typing a field name
    if (last.type === 'selector' && isCursorInsideToken(context.pos, last)) {
      return fieldCompletions(context, fieldMetadata, last.from);
    }

    // Field name complete -> suggest operators for that field
    if (last.type === 'selector' && !isCursorInsideToken(context.pos, last)) {
      const field = fieldMetadata.getField(last.value);
      const ops = field ? field.operators ?? RSQL_OPERATORS_BY_TYPE[field.type] : Object.values(RSQL_OPERATORS_BY_TYPE).flat();
      const unique = Array.from(new Set(ops));
      return {
        from: context.pos,
        options: unique.map((op) => ({ label: op, type: 'keyword' })),
      };
    }

    // Operator complete -> suggest values based on field type
    if (last.type === 'operator') {
      const selectorTok = tokens[tokens.length - 2];
      const field = selectorTok ? fieldMetadata.getField(selectorTok.value) : undefined;
      return {
        from: context.pos,
        options: valueCompletions(field),
      };
    }

    // Value complete -> suggest connectors
    if (last.type === 'value' || last.type === 'rparen') {
      return {
        from: context.pos,
        options: [
          { label: ';', displayLabel: '; (AND)', type: 'keyword' },
          { label: ',', displayLabel: ', (OR)', type: 'keyword' },
        ],
      };
    }

    return null;
  };
}

function fieldCompletions(
  context: CompletionContext,
  fieldMetadata: RsqlFieldMetadataProvider,
  from: number,
): CompletionResult {
  const options: Completion[] = fieldMetadata.getFields().map((f) => ({
    label: f.name,
    displayLabel: f.label ?? f.name,
    type: 'property',
    detail: f.type,
  }));
  return { from, options, validFor: /^[a-zA-Z_][a-zA-Z0-9_.]*$/ };
}

function valueCompletions(field: ReturnType<RsqlFieldMetadataProvider['getField']>): Completion[] {
  if (!field) return [];

  switch (field.type) {
    case 'boolean':
      return [
        { label: 'true', type: 'constant' },
        { label: 'false', type: 'constant' },
      ];
    case 'enum':
      return (field.enumValues ?? []).map((v) => ({ label: v, type: 'enum' }));
    case 'date':
      return [{ label: new Date().toISOString().slice(0, 10), type: 'text', detail: 'YYYY-MM-DD' }];
    case 'datetime':
      return [{ label: new Date().toISOString(), type: 'text', detail: 'ISO 8601' }];
    default:
      return [];
  }
}

function isCursorInsideToken(pos: number, tok: { from: number; to: number }): boolean {
  return pos > tok.from && pos <= tok.to;
}
