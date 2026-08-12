import { logger } from 'firebase-functions';
import type { Request, Response } from 'express';
import { randomUUID } from 'node:crypto';
import { MAX_LIST_SCAN } from './base-document.config.js';
import type { DocumentBlock, DocumentBlockInput, DocumentInput, DocumentPropertiesInput, DocumentTranslation, DocumentTranslationInput, StoredDocument, StoredDraft } from './base-document.model.js';
import { referencesTo } from './block-references.js';
import { type DocumentAccessPolicy, PermitAllDocumentAccessPolicy } from './document-access-policy.js';
import { applyQuery, QuerySyntaxError } from './document-query.js';
import * as mapper from './document-mapper.js';
import { type BlockMutation, type BlockMutationFailure, type DocumentStore, FirestoreDocumentStore } from './document-store.js';

const SLUG_PATTERN = /^[a-z0-9]+(-[a-z0-9]+)*$/;
const LOCALE_PATTERN = /^[a-z]{2,3}(-[A-Z]{2})?$/;
/** Firestore rejects these in a document id, and every id here becomes one. */
const ILLEGAL_ID_PATTERN = /[/\\.]|^__.*__$/;

/**
 * Fourteen of the twenty-one operations of `base-document-api.yaml`, backed by Firestore instead of
 * JPA. Status codes and payloads are those of `DocumentEndpoint`, so `BaseDocumentService` and
 * `DocumentContentService` need no knowledge of which topology they are talking to.
 *
 * Deferred, and asserted as deferred by `base-document.contract.spec.ts`: publish, unpublish,
 * discard-draft, getPublishedContent, validateDocument, importDocuments, exportDocument. Those need
 * the draft-vs-snapshot revision model and the full referential-integrity checker; the storage layout
 * already reserves a `published` subcollection for them.
 *
 * Behaviours carried over from the Java implementation because clients depend on them:
 *  - the server mints document and block ids and **ignores any id in the payload**;
 *  - creating a document also creates the source-locale draft, which is what makes the first
 *    `appendDocumentBlock` on a fresh document succeed;
 *  - a translation added with `blocks` absent copies the source locale's content, whereas an
 *    explicit `[]` means a blank page.
 */
export class BaseDocumentHandlers {
  constructor(
    private readonly store: DocumentStore = new FirestoreDocumentStore(),
    private readonly policy: DocumentAccessPolicy = new PermitAllDocumentAccessPolicy(),
  ) {}

  listDocuments = async (request: Request, response: Response): Promise<void> => {
    const orgKey = pathParam(request, 'orgKey');
    if (!this.policy.mayAccessOrganization(request, orgKey)) {
      forbidden(response, 'document.organization.access-denied', `Not permitted to read documents of organization '${orgKey}'.`);
      return;
    }

    const documents = await this.store.listDocuments(orgKey, MAX_LIST_SCAN);
    if (documents.length === MAX_LIST_SCAN) {
      logger.warn(
        `Organization '${orgKey}' has at least ${MAX_LIST_SCAN} documents; listDocuments filters in memory and is now truncating. Raise BASE_DOCUMENT_MAX_LIST_SCAN or push filtering into Firestore.`,
      );
    }

    let page;
    try {
      page = applyQuery(
        documents.filter((document) => this.policy.mayRead(request, document)),
        { where: queryParam(request, 'where'), order: queryParam(request, 'order'), page: queryParam(request, 'page'), size: queryParam(request, 'size') },
      );
    } catch (error) {
      if (error instanceof QuerySyntaxError) {
        badRequest(response, 'document.query.unsupported-filter', error.message);
        return;
      }
      throw error;
    }

    const content = await Promise.all(page.content.map((document) => this.toSummary(document)));
    response.status(200).json({ content, totalElements: page.totalElements, totalPages: page.totalPages, number: page.number, size: page.size });
  };

  createDocument = async (request: Request, response: Response): Promise<void> => {
    const orgKey = pathParam(request, 'orgKey');
    if (!this.policy.mayAccessOrganization(request, orgKey)) {
      forbidden(response, 'document.organization.access-denied', `Not permitted to create documents in organization '${orgKey}'.`);
      return;
    }

    const input = request.body as DocumentInput;
    const invalid = validateProperties(input);
    if (invalid) {
      badRequest(response, 'document.input.invalid', invalid);
      return;
    }
    if (await this.store.findDocumentBySlug(orgKey, input.slug)) {
      conflict(response, 'document.slug.already-exists', `Slug '${input.slug}' is already taken in organization '${orgKey}'.`);
      return;
    }

    const document = mapper.toNewStoredDocument(orgKey, input, this.policy.principalOf(request));
    await this.store.saveDocument(document);

    // The source-locale draft is created here rather than on first edit: the frontend posts
    // `translations: []` and then appends blocks, so without it that append would 404.
    const blocks = mapper.toBlocks(blocksOf(input.translations, input.sourceLocale) ?? []);
    await this.store.saveDraft(orgKey, document.id, mapper.newDraft(input.sourceLocale, blocks, null, document.createdAt));

    response.status(201).json(await this.toResource(document, document.sourceLocale));
  };

  getDocument = async (request: Request, response: Response): Promise<void> => {
    const document = await this.readableDocument(request, response);
    if (!document) return;

    const locale = queryParam(request, 'locale') ?? document.sourceLocale;
    response.status(200).json(await this.toResource(document, locale, isDraftRequested(request)));
  };

  updateDocument = async (request: Request, response: Response): Promise<void> => {
    const document = await this.editableDocument(request, response);
    if (!document) return;

    const input = request.body as DocumentInput;
    const invalid = validateProperties(input);
    if (invalid) {
      badRequest(response, 'document.input.invalid', invalid);
      return;
    }
    if (!(await this.slugIsFree(response, document, input.slug))) return;

    // Every named locale is checked before anything is written, so a bad locale cannot leave the
    // properties updated and the content half-applied.
    const named = (input.translations ?? []).filter((translation) => translation.blocks != null);
    const drafts = new Map<string, StoredDraft>();
    for (const translation of named) {
      const draft = await this.store.findDraft(document.orgKey, document.id, translation.locale);
      if (!draft) {
        translationNotFound(response, translation.locale);
        return;
      }
      drafts.set(translation.locale, draft);
    }

    const updated = mapper.withProperties(document, input);
    await this.store.saveDocument(updated);
    for (const translation of named) {
      const draft = drafts.get(translation.locale) as StoredDraft;
      const blocks = mapper.toBlocks(translation.blocks ?? []);
      await this.store.saveDraft(document.orgKey, document.id, { ...draft, blocks, revision: draft.revision + 1, updatedAt: updated.updatedAt });
    }

    response.status(200).json(await this.toResource(updated, updated.sourceLocale));
  };

  updateDocumentProperties = async (request: Request, response: Response): Promise<void> => {
    const document = await this.editableDocument(request, response);
    if (!document) return;

    const input = request.body as DocumentPropertiesInput;
    const invalid = validateProperties(input);
    if (invalid) {
      badRequest(response, 'document.input.invalid', invalid);
      return;
    }
    if (!(await this.slugIsFree(response, document, input.slug))) return;

    // Drafts are untouched on purpose: this is the endpoint the Properties form saves to, and the
    // e2e guard "keeps the content when the properties form is saved" exists because an earlier
    // whole-document PUT wiped the blocks.
    const updated = mapper.withProperties(document, input);
    await this.store.saveDocument(updated);
    response.status(200).json(await this.toResource(updated, updated.sourceLocale));
  };

  deleteDocument = async (request: Request, response: Response): Promise<void> => {
    const document = await this.editableDocument(request, response);
    if (!document) return;

    await this.store.deleteDocument(document.orgKey, document.id);
    response.status(204).send();
  };

  listDocumentTranslations = async (request: Request, response: Response): Promise<void> => {
    const document = await this.readableDocument(request, response);
    if (!document) return;

    response.status(200).json(await this.toTranslationSummaries(document));
  };

  addDocumentTranslation = async (request: Request, response: Response): Promise<void> => {
    const document = await this.editableDocument(request, response);
    if (!document) return;

    const input = request.body as DocumentTranslationInput;
    if (!isLocale(input?.locale)) {
      badRequest(response, 'document.input.invalid', `'locale' is required and must match ${LOCALE_PATTERN.source}.`);
      return;
    }
    if (await this.store.findDraft(document.orgKey, document.id, input.locale)) {
      conflict(response, 'document.translation.already-exists', `Document '${document.id}' already has a '${input.locale}' translation.`);
      return;
    }

    // `blocks` absent copies the source locale; an explicit `[]` is a blank page. The distinction is
    // the contract's, and collapsing it would silently blank a translator's starting point.
    const source = await this.store.findDraft(document.orgKey, document.id, document.sourceLocale);
    const blocks = input.blocks == null ? (source?.blocks ?? []) : mapper.toBlocks(input.blocks);
    const draft = mapper.newDraft(input.locale, blocks, source?.revision ?? null);
    await this.store.saveDraft(document.orgKey, document.id, draft);

    response.status(201).json(mapper.toTranslation(draft, undefined, document.sourceLocale, source));
  };

  getDocumentTranslation = async (request: Request, response: Response): Promise<void> => {
    const document = await this.readableDocument(request, response);
    if (!document) return;

    const locale = pathParam(request, 'locale');
    const translation = await this.loadTranslation(document, locale, isDraftRequested(request));
    if (!translation) {
      translationNotFound(response, locale);
      return;
    }

    response.status(200).json(translation);
  };

  removeDocumentTranslation = async (request: Request, response: Response): Promise<void> => {
    const document = await this.editableDocument(request, response);
    if (!document) return;

    const locale = pathParam(request, 'locale');
    if (locale === document.sourceLocale) {
      conflict(response, 'document.translation.source-locale-not-removable', `Locale '${locale}' is the source locale of document '${document.id}' and cannot be removed.`);
      return;
    }
    if (!(await this.store.findDraft(document.orgKey, document.id, locale))) {
      translationNotFound(response, locale);
      return;
    }

    await this.store.deletePublished(document.orgKey, document.id, locale);
    await this.store.deleteDraft(document.orgKey, document.id, locale);
    response.status(204).send();
  };

  appendDocumentBlock = async (request: Request, response: Response): Promise<void> => {
    const input = request.body as DocumentBlockInput;
    if (input?.kind !== 'TEXT' && input?.kind !== 'WIDGET') {
      badRequest(response, 'document.input.invalid', "'kind' is required and must be TEXT or WIDGET.");
      return;
    }

    const block = mapper.toBlock(randomUUID(), input);
    await this.mutateBlocks(
      request,
      response,
      (blocks) => ({ ok: true, blocks: [...blocks, block], result: block }),
      (result) => response.status(201).json(result),
    );
  };

  replaceDocumentBlock = async (request: Request, response: Response): Promise<void> => {
    const input = request.body as DocumentBlockInput;
    if (input?.kind !== 'TEXT' && input?.kind !== 'WIDGET') {
      badRequest(response, 'document.input.invalid', "'kind' is required and must be TEXT or WIDGET.");
      return;
    }

    // The path owns the identity, so a body `id` is ignored rather than honoured.
    const blockId = pathParam(request, 'blockId');
    const replacement = mapper.toBlock(blockId, input);
    await this.mutateBlocks(
      request,
      response,
      (blocks) => {
        if (!blocks.some((block) => block.id === blockId)) return { ok: false, failure: 'block-not-found' };
        return { ok: true, blocks: blocks.map((block) => (block.id === blockId ? replacement : block)), result: replacement };
      },
      (result) => response.status(200).json(result),
    );
  };

  deleteDocumentBlock = async (request: Request, response: Response): Promise<void> => {
    const blockId = pathParam(request, 'blockId');
    await this.mutateBlocks(
      request,
      response,
      (blocks) => {
        if (!blocks.some((block) => block.id === blockId)) return { ok: false, failure: 'block-not-found' };

        // Scan before removing: a removed block cannot be told apart from one that was never there,
        // so the reference check would come back empty for every block.
        const referencing = referencesTo(blocks, blockId);
        if (referencing.length > 0) return { ok: false, failure: 'block-referenced', detail: referencing };

        return { ok: true, blocks: blocks.filter((block) => block.id !== blockId), result: undefined };
      },
      () => response.status(204).send(),
    );
  };

  reorderDocumentBlocks = async (request: Request, response: Response): Promise<void> => {
    const blockIds = (request.body as { blockIds?: unknown })?.blockIds;
    if (!Array.isArray(blockIds) || blockIds.some((id) => typeof id !== 'string')) {
      badRequest(response, 'document.input.invalid', "'blockIds' is required and must be an array of strings.");
      return;
    }

    await this.mutateBlocks(
      request,
      response,
      (blocks) => {
        // An exact permutation, not a subset: accepting a partial list would make the blocks the
        // caller forgot vanish, which is indistinguishable from a deliberate delete. Comparing the
        // *distinct* ids against the block count is what rejects a duplicate padding the length —
        // `['b1','b1']` for two blocks has the right length and no unknown id, yet would drop b2.
        const byId = new Map(blocks.map((block) => [block.id, block]));
        const requested = new Set(blockIds as string[]);
        if (requested.size !== blocks.length || [...requested].some((id) => !byId.has(id))) {
          return { ok: false, failure: 'not-a-permutation' };
        }

        const reordered = (blockIds as string[]).map((id) => byId.get(id) as DocumentBlock);
        return { ok: true, blocks: reordered, result: reordered };
      },
      (result) => response.status(200).json(result),
    );
  };

  /* ------------------------------------------------------------ shared handler plumbing --------- */

  /**
   * Resolves the document, applies the block mutation in a transaction and maps every failure onto
   * its contract status. Sequenced this way so each block handler is only its own mutation rule.
   */
  private async mutateBlocks<T>(request: Request, response: Response, mutate: (blocks: DocumentBlock[]) => BlockMutation<T>, respond: (result: T) => void): Promise<void> {
    const document = await this.editableDocument(request, response);
    if (!document) return;

    const locale = pathParam(request, 'locale');
    if (!isLocale(locale)) {
      translationNotFound(response, locale);
      return;
    }

    const outcome = await this.store.mutateDraftBlocks(document.orgKey, document.id, locale, mutate);
    switch (outcome.kind) {
      case 'draft-missing':
        translationNotFound(response, locale);
        return;
      case 'failed':
        respondToFailure(response, outcome.failure, pathParam(request, 'blockId'), outcome.detail);
        return;
      case 'applied':
        respond(outcome.result);
        return;
    }
  }

  /** 404 rather than 403 on read denial — see `DocumentAccessPolicy`. */
  private async readableDocument(request: Request, response: Response): Promise<StoredDocument | undefined> {
    const document = await this.findDocument(request);
    if (!document || !this.policy.mayRead(request, document)) {
      documentNotFound(response, pathParam(request, 'documentId'));
      return undefined;
    }
    return document;
  }

  private async editableDocument(request: Request, response: Response): Promise<StoredDocument | undefined> {
    const document = await this.readableDocument(request, response);
    if (!document) return undefined;

    if (!this.policy.mayEdit(request, document)) {
      forbidden(response, 'document.access-denied', `Not permitted to modify document '${document.id}'.`);
      return undefined;
    }
    return document;
  }

  private async findDocument(request: Request): Promise<StoredDocument | undefined> {
    const documentId = pathParam(request, 'documentId');
    // A malformed id cannot name a stored document, and passing it to Firestore throws rather than
    // missing, so it is answered as "not found" instead of reaching the driver.
    if (!documentId || ILLEGAL_ID_PATTERN.test(documentId)) return undefined;
    return this.store.findDocument(pathParam(request, 'orgKey'), documentId);
  }

  private async slugIsFree(response: Response, document: StoredDocument, slug: string): Promise<boolean> {
    if (slug === document.slug) return true;

    const owner = await this.store.findDocumentBySlug(document.orgKey, slug);
    if (owner && owner.id !== document.id) {
      conflict(response, 'document.slug.already-exists', `Slug '${slug}' is already taken in organization '${document.orgKey}'.`);
      return false;
    }
    return true;
  }

  /**
   * `draft=true` reads the working copy, `draft=false` the published snapshot — which is why a
   * document that has never been published answers 404 for `draft=false`, exactly as the contract
   * describes. Publishing is not implemented yet, so today that is every document.
   */
  private async loadTranslation(document: StoredDocument, locale: string, draft: boolean): Promise<DocumentTranslation | undefined> {
    if (!isLocale(locale)) return undefined;

    const [stored, published, source] = await Promise.all([
      this.store.findDraft(document.orgKey, document.id, locale),
      this.store.findPublished(document.orgKey, document.id, locale),
      this.store.findDraft(document.orgKey, document.id, document.sourceLocale),
    ]);

    if (draft) {
      return stored ? mapper.toTranslation(stored, published, document.sourceLocale, source) : undefined;
    }
    if (!published) return undefined;

    // The snapshot's blocks, but the draft's revision counters — a reader needs to know the published
    // content, and a designer needs to know whether the draft has moved past it.
    return {
      locale,
      blocks: published.blocks,
      status: mapper.deriveStatus(stored, published),
      revision: stored?.revision,
      publishedRevision: published.publishedRevision,
      basedOnRevision: stored?.basedOnRevision ?? null,
      outOfDate: stored ? mapper.isOutOfDate(stored, document.sourceLocale, source) : false,
      publishedAt: published.publishedAt,
      updatedAt: stored?.updatedAt ?? published.publishedAt,
    };
  }

  private async toTranslationSummaries(document: StoredDocument) {
    const [drafts, published] = await Promise.all([this.store.listDrafts(document.orgKey, document.id), this.store.listPublished(document.orgKey, document.id)]);
    return mapper.toTranslationSummaries(document, drafts, published);
  }

  private async toSummary(document: StoredDocument) {
    return mapper.toDocumentSummary(document, await this.toTranslationSummaries(document));
  }

  private async toResource(document: StoredDocument, locale: string, draft = true) {
    const [summaries, translation] = await Promise.all([this.toTranslationSummaries(document), this.loadTranslation(document, locale, draft)]);
    return mapper.toDocumentResource(document, summaries, translation ?? null);
  }
}

/* -------------------------------------------------------------------- module helpers ----------- */

/**
 * Express 5 types a path parameter as `string | string[]` because a wildcard can match repeatedly.
 * None of these routes use a wildcard, so the array case is unreachable — narrowed here rather than
 * cast at each of the eighteen call sites.
 */
function pathParam(request: Request, name: string): string {
  const value = request.params[name] as string | string[] | undefined;
  if (Array.isArray(value)) return value[0] ?? '';
  return value ?? '';
}

function queryParam(request: Request, name: string): string | undefined {
  const value = request.query[name];
  return typeof value === 'string' ? value : undefined;
}

/** The contract's `DraftParam` defaults to false. */
function isDraftRequested(request: Request): boolean {
  return queryParam(request, 'draft') === 'true';
}

function isLocale(value: unknown): value is string {
  return typeof value === 'string' && LOCALE_PATTERN.test(value);
}

/** The three required `DocumentPropertiesInput` fields, plus the patterns the schema declares. */
function validateProperties(input: DocumentPropertiesInput | undefined): string | undefined {
  if (!input || typeof input !== 'object') return 'A JSON body is required.';
  if (typeof input.slug !== 'string' || !SLUG_PATTERN.test(input.slug)) return `'slug' is required and must match ${SLUG_PATTERN.source}.`;
  if (typeof input.title !== 'string' || input.title.trim() === '') return "'title' is required.";
  if (!isLocale(input.sourceLocale)) return `'sourceLocale' is required and must match ${LOCALE_PATTERN.source}.`;
  return undefined;
}

function blocksOf(translations: readonly DocumentTranslationInput[] | undefined, locale: string): DocumentBlockInput[] | undefined {
  return translations?.find((translation) => translation.locale === locale)?.blocks ?? undefined;
}

function respondToFailure(response: Response, failure: BlockMutationFailure, blockId: string, detail: readonly string[] | undefined): void {
  switch (failure) {
    case 'block-not-found':
      notFound(response, 'document.block.not-found', 'No such block in this translation.');
      return;
    case 'block-referenced':
      // The referring ids live in `errorText`, not in a second key: `ErrorResponse` is exactly
      // `errorId` + `errorText`, and an undeclared key is the one thing a client cannot rely on. The
      // wording matches DocumentBlockReferencedException's message so the two backends are
      // indistinguishable here, which is the whole point of implementing one contract twice.
      conflict(response, 'document.block.referenced', `Block '${blockId}' is still referenced by: [${(detail ?? []).join(', ')}]`);
      return;
    case 'not-a-permutation':
      badRequest(response, 'document.blocks.not-a-permutation', "'blockIds' must be an exact permutation of the translation's current block ids.");
      return;
  }
}

function badRequest(response: Response, errorId: string, errorText: string): void {
  response.status(400).json({ errorId, errorText });
}

function forbidden(response: Response, errorId: string, errorText: string): void {
  response.status(403).json({ errorId, errorText });
}

function notFound(response: Response, errorId: string, errorText: string): void {
  response.status(404).json({ errorId, errorText });
}

function conflict(response: Response, errorId: string, errorText: string): void {
  response.status(409).json({ errorId, errorText });
}

function documentNotFound(response: Response, documentId: string | undefined): void {
  notFound(response, 'document.not-found', `Document '${documentId}' does not exist.`);
}

function translationNotFound(response: Response, locale: string): void {
  notFound(response, 'document.translation.not-found', `No '${locale}' translation exists for this document.`);
}
