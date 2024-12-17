import { wildcardTextMatcher } from '../wildcard-text-matcher';

export function getEnvironment(): string {
  if (wildcardTextMatcher(location.origin, 'http://localhost:8080*')) return 'docker';
  else if (wildcardTextMatcher(location.origin, 'http://localhost*')) return 'local';
  else if (wildcardTextMatcher(location.origin, 'https://t0-*-bmf-ef-test.bmf.a2.cp.cna.at')) return 't0';
  else if (wildcardTextMatcher(location.origin, 'https://t1-*-bmf-ef-test.bmf.a2.cp.cna.at')) return 't1';
  else if (wildcardTextMatcher(location.origin, 'https://t2-*-bmf-ef-test.bmf.a2.cp.cna.at')) return 't2';
  else if (wildcardTextMatcher(location.origin, 'https://q-*-bmf-ef-qs.bmf.a2.cp.cna.at')) return 'q';
  else if (wildcardTextMatcher(location.origin, 'https://p-*-bmf-ef-prod.bmf.a2.cp.cna.at')) return 'p';
  else {
    console.log('in error');
    throw Error('Unexpected base URL');
  }
}
