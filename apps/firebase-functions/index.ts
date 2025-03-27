import { onRequest } from 'firebase-functions/https';
import { logger, setGlobalOptions } from 'firebase-functions';
import { create, defaults, router } from 'json-server';
import db from './db.json';

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
api.use(jsonServerRouter);
server.use('/api', api);

//const port = 3000;
// server.listen(port, () => {
//   logger.info(`JSON Server is running on port ${port}`);
// });

export const jsonServer = onRequest(server);
