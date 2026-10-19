import path from 'node:path';

/** Where `auth.setup.ts` leaves the signed-in session and the account it registered. `tmp` is git-ignored. */
export const AUTH_DIRECTORY = path.join(__dirname, '..', '..', 'tmp', 'auth');
export const STORAGE_STATE = path.join(AUTH_DIRECTORY, 'user.json');
export const USER_FILE = path.join(AUTH_DIRECTORY, 'credentials.json');

export interface E2eUser {
  email: string;
  password: string;
  firstName: string;
  lastName: string;
  displayName: string;
}
