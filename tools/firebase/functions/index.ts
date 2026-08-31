import { onRequest } from 'firebase-functions/https';
import { logger, setGlobalOptions } from 'firebase-functions';
import { create, defaults, router } from 'json-server';
import db from './db.json';

export { objectStore } from './src/object-store/object-store.function.js';
// Serves base-document-api.yaml from Firestore. It must be matched ahead of `jsonServer` in the
// `firebase.json` rewrites, which still owns the rest of `/api/**` — the third-party REST fixtures in
// `db.json`.
export { baseDocument } from './src/base-document/base-document.function.js';

setGlobalOptions({ region: 'europe-central2' });

export const helloWorld = onRequest({ region: 'europe-central2' }, (request, response) => {
  logger.info('Hello logs!', { structuredData: true });
  response.send('Hello from Firebase!');
});

logger.info(`Database: ${db}`);
const api = create();
const server = create();
const middlewares = defaults();
const jsonServerRouter = router('db.json');

api.use(middlewares);
// `db.json` is the third-party-source mock (see tools/mock-backend/README.md), so nothing here is
// org-scoped: the platform's own features are served by their backends, not by json-server.
api.use(jsonServerRouter);
server.use('/api', api);

//const port = 3000;
// server.listen(port, () => {
//   logger.info(`JSON Server is running on port ${port}`);
// });

export const jsonServer = onRequest(server);
