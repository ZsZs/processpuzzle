import { describe, expect, it } from 'vitest';
import type { DocumentInput, StoredPublished } from './base-document.model.js';
import {
  deriveStatus,
  isOutOfDate,
  newDraft,
  toBlock,
  toBlocks,
  toDocumentResource,
  toDocumentSummary,
  toNewStoredDocument,
  toStoredProperties,
  toTranslation,
  toTranslationSummaries,
  toTranslationSummary,
  withProperties,
} from './document-mapper.js';
import { aStoredDocument, aStoredDraft, aTextBlock, ORG_KEY } from './test-support.js';

const anInput = (overrides: Partial<DocumentInput> = {}): DocumentInput => ({ slug: 'q3-plan', title: 'Q3 plan', sourceLocale: 'en', ...overrides });

const aPublished = (overrides: Partial<StoredPublished> = {}): StoredPublished => ({
  locale: 'en',
  blocks: [],
  publishedRevision: 1,
  publishedAt: '2026-02-01T00:00:00.000Z',
  publishedBy: null,
  ...overrides,
});

describe('toStoredProperties', () => {
  it('applies the schema defaults so nothing is stored as undefined', () => {
    expect(toStoredProperties(anInput())).toEqual({
      slug: 'q3-plan',
      title: 'Q3 plan',
      subject: null,
      description: null,
      author: null,
      sourceLocale: 'en',
      isPublic: false,
      readerRoles: [],
      editorRoles: [],
      publisherRoles: [],
      inputPorts: [],
      outputPorts: [],
    });
  });

  it('drops keys outside the twelve contract fields', () => {
    // The frontend's toDto is a naked spread of its entity, so a create body also carries these.
    const withExtras = { ...anInput(), translations: [{ locale: 'en' }], translation: { locale: 'en' }, version: 5, id: 'client-chosen' };

    expect(Object.keys(toStoredProperties(withExtras)).sort()).toEqual([
      'author',
      'description',
      'editorRoles',
      'inputPorts',
      'isPublic',
      'outputPorts',
      'publisherRoles',
      'readerRoles',
      'slug',
      'sourceLocale',
      'subject',
      'title',
    ]);
  });

  it('keeps values that were supplied', () => {
    const properties = toStoredProperties(anInput({ subject: 'Planning', isPublic: true, readerRoles: ['reviewer'] }));
    expect(properties.subject).toBe('Planning');
    expect(properties.isPublic).toBe(true);
    expect(properties.readerRoles).toEqual(['reviewer']);
  });
});

describe('toNewStoredDocument', () => {
  it('mints an id and ignores the one in the payload', () => {
    const document = toNewStoredDocument(ORG_KEY, anInput({ id: 'client-chosen' }), null);
    expect(document.id).not.toBe('client-chosen');
    expect(document.id).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/);
  });

  it('starts unpublished at lockVersion 0 with matching timestamps', () => {
    const document = toNewStoredDocument(ORG_KEY, anInput(), 'alice@example.com', '2026-03-01T00:00:00.000Z');
    expect(document).toMatchObject({
      orgKey: ORG_KEY,
      lockVersion: 0,
      createdBy: 'alice@example.com',
      createdAt: '2026-03-01T00:00:00.000Z',
      updatedAt: '2026-03-01T00:00:00.000Z',
      publishedAt: null,
    });
  });
});

describe('withProperties', () => {
  it('bumps lockVersion and updatedAt', () => {
    const updated = withProperties(aStoredDocument({ lockVersion: 4 }), anInput({ title: 'Renamed' }), '2026-04-01T00:00:00.000Z');
    expect(updated.lockVersion).toBe(5);
    expect(updated.updatedAt).toBe('2026-04-01T00:00:00.000Z');
    expect(updated.title).toBe('Renamed');
  });

  it('leaves identity and publication history alone', () => {
    const existing = aStoredDocument({ createdBy: 'alice@example.com', createdAt: '2020-01-01T00:00:00.000Z', publishedAt: '2021-01-01T00:00:00.000Z' });
    const updated = withProperties(existing, anInput());
    expect(updated.id).toBe(existing.id);
    expect(updated.createdBy).toBe('alice@example.com');
    expect(updated.createdAt).toBe('2020-01-01T00:00:00.000Z');
    expect(updated.publishedAt).toBe('2021-01-01T00:00:00.000Z');
  });
});

describe('toBlock', () => {
  it('takes the id from its argument, never from the input', () => {
    expect(toBlock('server-id', { id: 'client-id', kind: 'TEXT' }).id).toBe('server-id');
  });

  it('omits absent optionals rather than storing undefined, which Firestore rejects', () => {
    expect(Object.keys(toBlock('b1', { kind: 'TEXT' }))).toEqual(['id', 'kind']);
  });

  it('omits optionals that were explicitly null', () => {
    expect(Object.keys(toBlock('b1', { kind: 'WIDGET', type: null, props: null, editable: null, inputBindings: null, outputBindings: null }))).toEqual(['id', 'kind']);
  });

  it('keeps every supplied field', () => {
    const block = toBlock('b1', {
      kind: 'WIDGET',
      editable: false,
      content: { type: 'doc' },
      placement: 'REFERENCED',
      type: 'entity-table',
      props: { childIds: ['b2'] },
      inputBindings: { rows: 'customers' },
      outputBindings: { selected: 'customer' },
    });
    expect(block).toEqual({
      id: 'b1',
      kind: 'WIDGET',
      editable: false,
      content: { type: 'doc' },
      placement: 'REFERENCED',
      type: 'entity-table',
      props: { childIds: ['b2'] },
      inputBindings: { rows: 'customers' },
      outputBindings: { selected: 'customer' },
    });
  });

  it('keeps editable false, which a truthiness check would have dropped', () => {
    expect(toBlock('b1', { kind: 'TEXT', editable: false }).editable).toBe(false);
  });
});

describe('toBlocks', () => {
  it('keeps an incoming id when one is given and mints one otherwise', () => {
    const blocks = toBlocks([{ id: 'given', kind: 'TEXT' }, { kind: 'TEXT' }]);
    expect(blocks[0].id).toBe('given');
    expect(blocks[1].id).toBeTruthy();
    expect(blocks[1].id).not.toBe('given');
  });
});

describe('deriveStatus', () => {
  it('is DRAFT while nothing is published', () => {
    expect(deriveStatus(aStoredDraft(), undefined)).toBe('DRAFT');
  });

  it('is PUBLISHED when the draft has not moved past the snapshot', () => {
    expect(deriveStatus(aStoredDraft({ revision: 3 }), aPublished({ publishedRevision: 3 }))).toBe('PUBLISHED');
  });

  it('is PUBLISHED_WITH_DRAFT_CHANGES once the draft moves ahead', () => {
    expect(deriveStatus(aStoredDraft({ revision: 4 }), aPublished({ publishedRevision: 3 }))).toBe('PUBLISHED_WITH_DRAFT_CHANGES');
  });

  it('is PUBLISHED when a snapshot survives without a draft', () => {
    expect(deriveStatus(undefined, aPublished())).toBe('PUBLISHED');
  });
});

describe('isOutOfDate', () => {
  it('is false for the source locale, which cannot lag itself', () => {
    expect(isOutOfDate(aStoredDraft({ locale: 'en', basedOnRevision: 1 }), 'en', aStoredDraft({ revision: 9 }))).toBe(false);
  });

  it('is true once the source has moved past the revision the translation branched from', () => {
    expect(isOutOfDate(aStoredDraft({ locale: 'hu', basedOnRevision: 2 }), 'en', aStoredDraft({ revision: 5 }))).toBe(true);
  });

  it('is false while the source is still where the translation branched from', () => {
    expect(isOutOfDate(aStoredDraft({ locale: 'hu', basedOnRevision: 5 }), 'en', aStoredDraft({ revision: 5 }))).toBe(false);
  });

  it('is false when there is nothing to compare against', () => {
    expect(isOutOfDate(aStoredDraft({ locale: 'hu', basedOnRevision: null }), 'en', aStoredDraft({ revision: 5 }))).toBe(false);
    expect(isOutOfDate(aStoredDraft({ locale: 'hu', basedOnRevision: 2 }), 'en', undefined)).toBe(false);
  });
});

describe('toTranslation', () => {
  it('carries the blocks, derived status and both revision counters', () => {
    const draft = aStoredDraft({ locale: 'en', blocks: [aTextBlock('b1')], revision: 4 });
    expect(toTranslation(draft, aPublished({ publishedRevision: 3 }), 'en', draft)).toEqual({
      locale: 'en',
      blocks: [aTextBlock('b1')],
      status: 'PUBLISHED_WITH_DRAFT_CHANGES',
      revision: 4,
      publishedRevision: 3,
      basedOnRevision: null,
      outOfDate: false,
      publishedAt: '2026-02-01T00:00:00.000Z',
      updatedAt: '2026-01-01T00:00:00.000Z',
    });
  });

  it('reports null rather than undefined for what has never been published', () => {
    const translation = toTranslation(aStoredDraft(), undefined, 'en', undefined);
    expect(translation.publishedRevision).toBeNull();
    expect(translation.publishedAt).toBeNull();
  });

  it('presents a draft with no blocks as an empty list', () => {
    const draft = { ...aStoredDraft(), blocks: undefined } as unknown as ReturnType<typeof aStoredDraft>;
    expect(toTranslation(draft, undefined, 'en', undefined).blocks).toEqual([]);
  });
});

describe('toTranslationSummary', () => {
  it('replaces the blocks with their count', () => {
    const draft = aStoredDraft({ blocks: [aTextBlock('b1'), aTextBlock('b2')] });
    const summary = toTranslationSummary(draft, undefined, 'en', draft);
    expect(summary.blockCount).toBe(2);
    expect(summary).not.toHaveProperty('blocks');
  });
});

describe('toTranslationSummaries', () => {
  it('puts the source locale first and the rest alphabetically', () => {
    const document = aStoredDocument({ sourceLocale: 'en' });
    const drafts = [aStoredDraft({ locale: 'hu' }), aStoredDraft({ locale: 'de' }), aStoredDraft({ locale: 'en' })];
    expect(toTranslationSummaries(document, drafts, []).map((summary) => summary.locale)).toEqual(['en', 'de', 'hu']);
  });

  it('attributes each snapshot to its own locale', () => {
    const document = aStoredDocument({ sourceLocale: 'en' });
    const drafts = [aStoredDraft({ locale: 'en', revision: 2 }), aStoredDraft({ locale: 'hu', revision: 1 })];
    const summaries = toTranslationSummaries(document, drafts, [aPublished({ locale: 'hu', publishedRevision: 1 })]);
    expect(summaries.find((summary) => summary.locale === 'en')?.status).toBe('DRAFT');
    expect(summaries.find((summary) => summary.locale === 'hu')?.status).toBe('PUBLISHED');
  });

  it('does not mutate the drafts it was given while sorting', () => {
    const drafts = [aStoredDraft({ locale: 'hu' }), aStoredDraft({ locale: 'en' })];
    toTranslationSummaries(aStoredDocument(), drafts, []);
    expect(drafts.map((draft) => draft.locale)).toEqual(['hu', 'en']);
  });
});

describe('toDocumentSummary', () => {
  it('emits every contract field of DocumentSummary and no content', () => {
    const summary = toDocumentSummary(aStoredDocument(), []);
    expect(Object.keys(summary).sort()).toEqual([
      'author',
      'createdAt',
      'createdBy',
      'description',
      'editorRoles',
      'id',
      'inputPorts',
      'isPublic',
      'lockVersion',
      'orgKey',
      'outputPorts',
      'publishedAt',
      'publisherRoles',
      'readerRoles',
      'slug',
      'sourceLocale',
      'subject',
      'title',
      'translations',
      'updatedAt',
    ]);
  });
});

describe('toDocumentResource', () => {
  it('adds the selected translation to the summary shape', () => {
    const draft = aStoredDraft();
    const resource = toDocumentResource(aStoredDocument(), [], toTranslation(draft, undefined, 'en', draft));
    expect(resource.translation?.locale).toBe('en');
  });

  it('carries an explicit null when the requested locale has none', () => {
    expect(toDocumentResource(aStoredDocument(), [], null).translation).toBeNull();
  });
});

describe('newDraft', () => {
  it('starts at revision 1 with the timestamps equal', () => {
    expect(newDraft('en', [], null, '2026-05-01T00:00:00.000Z')).toEqual({
      locale: 'en',
      blocks: [],
      revision: 1,
      basedOnRevision: null,
      createdAt: '2026-05-01T00:00:00.000Z',
      updatedAt: '2026-05-01T00:00:00.000Z',
    });
  });
});
