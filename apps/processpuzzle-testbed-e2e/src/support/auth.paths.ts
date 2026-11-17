import path from 'node:path';

/** Where `auth.setup.ts` leaves the signed-in sessions and the accounts it registered. `tmp` is git-ignored. */
export const AUTH_DIRECTORY = path.join(__dirname, '..', '..', 'tmp', 'auth');

/**
 * The browser a Playwright project belongs to: `setup-firefox` and `firefox-auth` are both Firefox's. Each browser signs
 * in itself and keeps its own session. One session made in Chromium and handed to the others is not restored by Firefox
 * or WebKit (the header stays anonymous), so the browser matrix would only ever test signed-in behaviour in Chromium.
 */
export function browserOf(projectName: string): string {
  return projectName.replace(/^setup-/, '').replace(/-auth$/, '');
}

/** The session and the account of the browser a project runs in. */
export function authFilesFor(projectName: string): { storageState: string; credentials: string } {
  const browser = browserOf(projectName);
  return {
    storageState: path.join(AUTH_DIRECTORY, `user.${browser}.json`),
    credentials: path.join(AUTH_DIRECTORY, `credentials.${browser}.json`),
  };
}

export interface E2eUser {
  email: string;
  password: string;
  firstName: string;
  lastName: string;
  displayName: string;
}
