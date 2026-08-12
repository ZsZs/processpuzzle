import express, { type Express, type NextFunction, type Request, type Response, Router } from 'express';
import cors from 'cors';
import { logger } from 'firebase-functions';
import { onRequest } from 'firebase-functions/https';
import { API_BASE_PATH } from './base-document.config.js';
import { BaseDocumentHandlers } from './base-document.handlers.js';

/**
 * `BaseEntityRestService` sends `Access-Control-Allow-*` as *request* headers
 * (`base-entity-rest.service.ts:16-21`). They are meaningless there, but a preflight fails outright
 * unless the server allows them, so they are named rather than left to the cors default.
 */
const ALLOWED_HEADERS = ['Content-Type', 'Authorization', 'Access-Control-Allow-Origin', 'Access-Control-Allow-Methods', 'Access-Control-Allow-Headers'];

/** Tiptap content is the payload here, and a long document exceeds the 100 kB express default. */
const MAX_BODY_SIZE = '10mb';

export function createBaseDocumentRouter(handlers: BaseDocumentHandlers = new BaseDocumentHandlers()): Router {
  const router = Router();

  router.get('/organizations/:orgKey/documents', handlers.listDocuments);
  router.post('/organizations/:orgKey/documents', handlers.createDocument);
  router.get('/organizations/:orgKey/documents/:documentId', handlers.getDocument);
  router.put('/organizations/:orgKey/documents/:documentId', handlers.updateDocument);
  router.delete('/organizations/:orgKey/documents/:documentId', handlers.deleteDocument);
  router.put('/organizations/:orgKey/documents/:documentId/properties', handlers.updateDocumentProperties);

  router.get('/organizations/:orgKey/documents/:documentId/translations', handlers.listDocumentTranslations);
  router.post('/organizations/:orgKey/documents/:documentId/translations', handlers.addDocumentTranslation);
  router.get('/organizations/:orgKey/documents/:documentId/translations/:locale', handlers.getDocumentTranslation);
  router.delete('/organizations/:orgKey/documents/:documentId/translations/:locale', handlers.removeDocumentTranslation);

  router.post('/organizations/:orgKey/documents/:documentId/translations/:locale/blocks', handlers.appendDocumentBlock);
  // `reorder` before `:blockId`: express matches in declaration order, and the literal would
  // otherwise be captured as a block id and answered 404.
  router.put('/organizations/:orgKey/documents/:documentId/translations/:locale/blocks/reorder', handlers.reorderDocumentBlocks);
  router.put('/organizations/:orgKey/documents/:documentId/translations/:locale/blocks/:blockId', handlers.replaceDocumentBlock);
  router.delete('/organizations/:orgKey/documents/:documentId/translations/:locale/blocks/:blockId', handlers.deleteDocumentBlock);

  return router;
}

export function createBaseDocumentApp(handlers?: BaseDocumentHandlers): Express {
  const app = express();
  app.use(cors({ origin: true, allowedHeaders: ALLOWED_HEADERS }));
  app.use(express.json({ limit: MAX_BODY_SIZE }));

  const router = createBaseDocumentRouter(handlers);
  // Behind the Hosting rewrite the function sees the full `/api/organizations/...` path; called
  // directly (emulator function URL, tests) it sees the bare path. Both are served.
  app.use(API_BASE_PATH, router);
  app.use('/', router);

  app.use((error: Error, _request: Request, response: Response, next: NextFunction) => {
    logger.error('Base document request failed', error);
    if (response.headersSent) {
      next(error);
      return;
    }
    // A malformed JSON body is the one client error that reaches here, because express's own parser
    // throws before any handler runs and cannot be validated earlier.
    //
    // Neither id is feature-namespaced, and both match core's `ApiExceptionHandler`: an unparseable
    // payload and an unexpected failure are not document concerns, and a client that branches on either
    // must not be able to tell which backend served it.
    if (isBodyParseFailure(error)) {
      // express's parse message quotes the caller's own body, so echoing it tells them nothing they
      // did not send.
      //
      // Reachable only when this app is served directly, which is to say in its own specs. Verified
      // against the emulator on 2026-08-12: the Cloud Functions runtime installs its own body parser
      // ahead of the user handler, so a malformed JSON body is answered by *its* default error page —
      // 400 text/html with a SyntaxError — and nothing here runs. Left in place because it is correct
      // for the direct-mount path and would otherwise have to be rediscovered; the deployed gap is
      // real and is not ours to close from inside the handler.
      response.status(400).json({ errorId: 'request.malformed-payload', errorText: error.message });
      return;
    }

    // Generic on purpose, for the reason core's `handleUnexpected` gives: an unexpected failure's
    // message is the likeliest place for an internal detail — a Firestore path, a host, a query — to
    // leak to a caller. It is logged in full above, so nothing is lost to whoever operates the service.
    response.status(500).json({ errorId: 'internal-error', errorText: 'Unexpected server error.' });
  });

  return app;
}

function isBodyParseFailure(error: Error): boolean {
  return (error as { type?: string }).type === 'entity.parse.failed';
}

/**
 * `invoker` is stated rather than left to default for the reason spelled out on `objectStore`: the
 * default only applies when the function is *created*, so a service that never received the
 * `allUsers` grant stays private through every later deploy and IAP rejects anonymous calls before
 * the handler runs. Declaring it makes each deploy assert the grant.
 */
export const baseDocument = onRequest({ region: 'europe-central2', memory: '512MiB', timeoutSeconds: 120, invoker: 'public' }, createBaseDocumentApp());
