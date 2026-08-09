import express, { type Express, type NextFunction, type Request, type Response, Router } from 'express';
import cors from 'cors';
import { logger } from 'firebase-functions';
import { onRequest } from 'firebase-functions/https';
import { API_BASE_PATH } from './object-store.config.js';
import { ObjectStoreHandlers } from './object-store.handlers.js';

/**
 * `Location` carries the bucket name of a freshly uploaded object and `X-Object-*` the
 * object's identity — a browser cannot read either from a cross-origin response unless
 * they are explicitly exposed, and `ObjectStoreService` reads all three.
 */
const EXPOSED_HEADERS = ['Location', 'X-Object-Name', 'X-Object-Bucket'];

export function createObjectStoreRouter(handlers: ObjectStoreHandlers = new ObjectStoreHandlers()): Router {
  const router = Router();
  router.post('/objects', handlers.uploadObject);
  router.get('/objects/:bucketName/:objectID', handlers.getObjectByID);
  router.delete('/objects/:bucketName/:objectID', handlers.deleteObjectByID);
  router.get('/objects/:bucketName/:objectID/uri', handlers.getObjectUriByID);
  router.get('/objects/:bucketName/:objectID/thumbnail-uri', handlers.getThumbnailUriByID);
  return router;
}

export function createObjectStoreApp(handlers?: ObjectStoreHandlers): Express {
  const app = express();
  app.use(cors({ origin: true, exposedHeaders: EXPOSED_HEADERS }));

  const router = createObjectStoreRouter(handlers);
  // Behind the Hosting rewrite the function sees the full `/api/store/...` path; called
  // directly (emulator function URL, tests) it sees the bare path. Both are served.
  app.use(API_BASE_PATH, router);
  app.use('/', router);

  app.use((error: Error, _request: Request, response: Response, next: NextFunction) => {
    logger.error('Object store request failed', error);
    if (response.headersSent) {
      next(error);
      return;
    }
    response.status(500).json({ errorId: 'store.internal-error', errorText: error.message });
  });

  return app;
}

export const objectStore = onRequest({ region: 'europe-central2', memory: '512MiB', timeoutSeconds: 120 }, createObjectStoreApp());
