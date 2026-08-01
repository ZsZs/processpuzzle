import { onRequest } from 'firebase-functions/https';
import { logger, setGlobalOptions } from 'firebase-functions';
import { create, defaults, router } from 'json-server';
import db from './db.json';
import orgScopeRewrite from './org-scope.js';

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
// `/organizations/{orgKey}/rules` -> `/{orgKey}-rules`, so the org-scoped REST contract reaches
// json-server's flat collections. Must precede the router.
api.use(orgScopeRewrite);
api.use(jsonServerRouter);
server.use('/api', api);

//const port = 3000;
// server.listen(port, () => {
//   logger.info(`JSON Server is running on port ${port}`);
// });

export const jsonServer = onRequest(server);
