import * as path from 'path';
import { createGlobalSetup } from '@processpuzzle/e2e-testing';

export const REGISTRY_PATH = path.join(__dirname, '../../tmp/entity-registry.json');

export default createGlobalSetup({ registryPath: REGISTRY_PATH });
