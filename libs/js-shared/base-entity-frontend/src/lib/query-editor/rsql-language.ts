import { StreamLanguage, StringStream } from '@codemirror/language';
import { tags as t } from '@lezer/highlight';
import { HighlightStyle, syntaxHighlighting } from '@codemirror/language';

interface RsqlStreamState {
  expect: 'selector' | 'operator' | 'value' | 'connector';
}

const OPERATOR_RE = /^(==|!=|=gt=|=ge=|=lt=|=le=|=in=|=out=|=like=)/;
const SELECTOR_RE = /^[a-zA-Z_][a-zA-Z0-9_.]*/;
const UNQUOTED_VALUE_RE = /^[^,;()'"\s]+/;

export const rsqlLanguage = StreamLanguage.define<RsqlStreamState>({
  startState: (): RsqlStreamState => ({ expect: 'selector' }),

  token(stream: StringStream, state: RsqlStreamState): string | null {
    if (stream.eatSpace()) return null;

    if (stream.match('(')) return 'paren';
    if (stream.match(')')) return 'paren';

    if (stream.match(';')) {
      state.expect = 'selector';
      return 'operator.and';
    }
    if (stream.match(',')) {
      state.expect = 'selector';
      return 'operator.or';
    }

    if (stream.peek() === "'" || stream.peek() === '"') {
      const quote = stream.next();
      while (!stream.eol()) {
        if (stream.next() === quote) break;
      }
      state.expect = 'connector';
      return 'string';
    }

    if (state.expect === 'operator' && stream.match(OPERATOR_RE)) {
      state.expect = 'value';
      return 'operatorKeyword';
    }

    if (state.expect === 'value' && stream.match(UNQUOTED_VALUE_RE)) {
      state.expect = 'connector';
      return 'string';
    }

    if (stream.match(SELECTOR_RE)) {
      state.expect = 'operator';
      return 'propertyName';
    }

    stream.next();
    return 'invalid';
  },
});

/** Optional highlight style — merge into your existing theme setup if you have one. */
export const rsqlHighlightStyle = HighlightStyle.define([
  { tag: t.propertyName, color: '#0b7285', fontWeight: '600' },
  { tag: t.operatorKeyword, color: '#9c36b5' },
  { tag: t.string, color: '#2f9e44' },
  { tag: t.paren, color: '#495057' },
  { tag: t.invalid, color: '#e03131', textDecoration: 'underline wavy' },
]);

export const rsqlSyntaxHighlighting = syntaxHighlighting(rsqlHighlightStyle);
