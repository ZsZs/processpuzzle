// Copies the built <pp-login-form> bundle and the card artwork into the Keycloak login theme.
// Plain Node so it runs the same on Windows (cmd has no `mkdir -p` / `cp`) and in CI.
import { cpSync, mkdirSync } from 'node:fs';

const resources = 'tools/docker/keycloak/themes/processpuzzle/login/resources';

mkdirSync(`${resources}/js`, { recursive: true });
cpSync('dist/apps/login-element/browser/main.js', `${resources}/js/main.js`);
mkdirSync(`${resources}/img`, { recursive: true });
cpSync('libs/js-shared/auth/feature/assets/auth/pp_card.svg', `${resources}/img/pp_card.svg`);
