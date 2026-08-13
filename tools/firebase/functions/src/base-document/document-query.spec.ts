import { describe, expect, it } from 'vitest';
import { applyQuery, parseOrder, parsePageNumber, parseWhere, QuerySyntaxError } from './document-query.js';
import { aStoredDocument } from './test-support.js';

const matches = (where: string, document = aStoredDocument()) => parseWhere(where)(document);

describe('parseWhere', () => {
  it('matches everything when the filter is absent or blank', () => {
    expect(parseWhere(undefined)(aStoredDocument())).toBe(true);
    expect(parseWhere('   ')(aStoredDocument())).toBe(true);
  });

  it('compares strings, booleans and numbers by the field they are compared against', () => {
    expect(matches('slug==q3-plan')).toBe(true);
    expect(matches('slug==other')).toBe(false);
    expect(matches('isPublic==false')).toBe(true);
    expect(matches('isPublic==true')).toBe(false);
    expect(matches('lockVersion==0')).toBe(true);
    expect(matches('lockVersion==7')).toBe(false);
  });

  it('treats the bare token null as the null value', () => {
    expect(matches('subject==null')).toBe(true);
    expect(matches('subject!=null')).toBe(false);
    expect(matches('subject==null', aStoredDocument({ subject: 'Planning' }))).toBe(false);
  });

  it('negates with !=', () => {
    expect(matches('slug!=other')).toBe(true);
    expect(matches('slug!=q3-plan')).toBe(false);
  });

  it('tests membership with =in= and its negation with =out=', () => {
    expect(matches('slug=in=(q3-plan,q4-plan)')).toBe(true);
    expect(matches('slug=in=(q4-plan)')).toBe(false);
    expect(matches('slug=out=(q4-plan)')).toBe(true);
    expect(matches('slug=out=(q3-plan,q4-plan)')).toBe(false);
  });

  it('matches an array field when it contains the value, which is how role filters work', () => {
    const document = aStoredDocument({ readerRoles: ['editor', 'reviewer'] });
    expect(matches('readerRoles==reviewer', document)).toBe(true);
    expect(matches('readerRoles==author', document)).toBe(false);
    expect(matches('readerRoles=in=(author,editor)', document)).toBe(true);
  });

  it('orders with =lt= =le= =gt= =ge=, including ISO timestamps', () => {
    expect(matches('lockVersion=gt=-1')).toBe(true);
    expect(matches('lockVersion=ge=0')).toBe(true);
    expect(matches('lockVersion=lt=1')).toBe(true);
    expect(matches('lockVersion=le=0')).toBe(true);
    expect(matches('lockVersion=gt=0')).toBe(false);
    expect(matches('createdAt=lt=2026-06-01T00:00:00.000Z')).toBe(true);
    expect(matches('createdAt=gt=2026-06-01T00:00:00.000Z')).toBe(false);
  });

  it('never satisfies an ordering comparison against a missing field, in either direction', () => {
    expect(matches('subject=lt=zzz')).toBe(false);
    expect(matches('subject=gt=aaa')).toBe(false);
  });

  it('conjoins with ; and disjoins with ,', () => {
    expect(matches('slug==q3-plan;sourceLocale==en')).toBe(true);
    expect(matches('slug==q3-plan;sourceLocale==hu')).toBe(false);
    expect(matches('slug==other,sourceLocale==en')).toBe(true);
    expect(matches('slug==other,sourceLocale==hu')).toBe(false);
  });

  it('gives parentheses precedence over the flat AND/OR reading', () => {
    // Without the group this would be (slug==other AND ...) OR sourceLocale==en → true.
    expect(matches('slug==other;(sourceLocale==en,sourceLocale==hu)')).toBe(false);
    expect(matches('slug==q3-plan;(sourceLocale==de,sourceLocale==en)')).toBe(true);
  });

  it('reads quoted values, keeping the characters that would otherwise end a token', () => {
    const document = aStoredDocument({ title: 'Q3; plan, (final)' });
    expect(matches('title=="Q3; plan, (final)"', document)).toBe(true);
  });

  it('unescapes a quoted quote', () => {
    const document = aStoredDocument({ title: 'The "big" plan' });
    expect(matches(String.raw`title=="The \"big\" plan"`, document)).toBe(true);
  });

  it('resolves a dotted selector into nested fields', () => {
    const document = aStoredDocument({ inputPorts: [{ name: 'customer', type: 'ENTITY_REF' }] });
    expect(matches('inputPorts.0.name==customer', document)).toBe(true);
  });

  it('rejects an operator outside the supported set rather than matching everything', () => {
    expect(() => parseWhere('title=like=plan')).toThrow(QuerySyntaxError);
    expect(() => parseWhere('title=like=plan')).toThrow(/Unsupported operator/);
  });

  it.each([
    ['a missing field name', '==value'],
    ['a missing operator', 'slug'],
    ['an unclosed group', '(slug==q3-plan'],
    ['an unclosed value list', 'slug=in=(a,b'],
    ['an unclosed quote', 'slug=="q3'],
    ['trailing junk', 'slug==q3-plan)'],
  ])('rejects %s', (_description, where) => {
    expect(() => parseWhere(where)).toThrow(QuerySyntaxError);
  });

  it('rejects a list handed to an operator that takes one value', () => {
    expect(() => parseWhere('slug==(a,b)')(aStoredDocument())).toThrow(/takes a single value/);
  });
});

describe('parseOrder', () => {
  it('is empty for an absent or blank order', () => {
    expect(parseOrder(undefined)).toEqual([]);
    expect(parseOrder('  ')).toEqual([]);
  });

  it('defaults to ascending when no direction token follows the property', () => {
    expect(parseOrder('title')).toEqual([{ property: 'title', descending: false }]);
  });

  it('reads a direction token as belonging to the property before it', () => {
    expect(parseOrder('title,desc,slug')).toEqual([
      { property: 'title', descending: true },
      { property: 'slug', descending: false },
    ]);
  });

  it('accepts a direction in any case', () => {
    expect(parseOrder('title,DESC')).toEqual([{ property: 'title', descending: true }]);
  });

  it('rejects an empty property', () => {
    expect(() => parseOrder('title,,slug')).toThrow(QuerySyntaxError);
  });
});

describe('parsePageNumber', () => {
  it('falls back when absent or blank', () => {
    expect(parsePageNumber(undefined, 'size', 20)).toBe(20);
    expect(parsePageNumber('', 'size', 20)).toBe(20);
  });

  it('reads a non-negative integer', () => {
    expect(parsePageNumber('0', 'page', 0)).toBe(0);
    expect(parsePageNumber('3', 'page', 0)).toBe(3);
  });

  it.each(['-1', '1.5', 'abc'])('rejects %s', (value) => {
    expect(() => parsePageNumber(value, 'page', 0)).toThrow(QuerySyntaxError);
  });
});

describe('applyQuery', () => {
  const documents = [
    aStoredDocument({ id: 'a', slug: 'alpha', title: 'Beta' }),
    aStoredDocument({ id: 'b', slug: 'beta', title: 'Alpha' }),
    aStoredDocument({ id: 'c', slug: 'gamma', title: 'Gamma', isPublic: true }),
  ];

  it('counts matches rather than reads, so totalElements survives paging', () => {
    const page = applyQuery(documents, { page: '0', size: '2' });
    expect(page.content).toHaveLength(2);
    expect(page.totalElements).toBe(3);
    expect(page.totalPages).toBe(2);
    expect(page.number).toBe(0);
    expect(page.size).toBe(2);
  });

  it('filters before counting', () => {
    const page = applyQuery(documents, { where: 'isPublic==true' });
    expect(page.content.map((document) => document.id)).toEqual(['c']);
    expect(page.totalElements).toBe(1);
  });

  it('sorts by the requested property, not by storage order', () => {
    expect(applyQuery(documents, { order: 'title' }).content.map((document) => document.id)).toEqual(['b', 'a', 'c']);
    expect(applyQuery(documents, { order: 'title,desc' }).content.map((document) => document.id)).toEqual(['c', 'a', 'b']);
  });

  it('keeps documents that lack the sort field, unlike a Firestore orderBy', () => {
    const withGaps = [aStoredDocument({ id: 'a', subject: 'zzz' }), aStoredDocument({ id: 'b', subject: null })];
    expect(applyQuery(withGaps, { order: 'subject' }).content.map((document) => document.id)).toEqual(['a', 'b']);
    expect(applyQuery(withGaps, { order: 'subject' }).totalElements).toBe(2);
  });

  it('breaks ties on id so paging cannot show the same document twice', () => {
    const tied = [aStoredDocument({ id: 'b', title: 'Same' }), aStoredDocument({ id: 'a', title: 'Same' })];
    expect(applyQuery(tied, { order: 'title' }).content.map((document) => document.id)).toEqual(['a', 'b']);
  });

  it('returns the requested page', () => {
    const page = applyQuery(documents, { order: 'slug', page: '1', size: '2' });
    expect(page.content.map((document) => document.id)).toEqual(['c']);
    expect(page.number).toBe(1);
  });

  it('applies the contract default page size of 20', () => {
    expect(applyQuery(documents, {}).size).toBe(20);
  });

  it('returns nothing for size 0 without dividing by it', () => {
    const page = applyQuery(documents, { size: '0' });
    expect(page.content).toEqual([]);
    expect(page.totalPages).toBe(0);
    expect(page.totalElements).toBe(3);
  });
});
