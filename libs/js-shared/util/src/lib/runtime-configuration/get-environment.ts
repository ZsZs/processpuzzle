import { wildcardTextMatcher } from '../wildcard-text-matcher';

export function getEnvironment(originUrl?: string): string {
  const origin = originUrl ?? location.origin;
  if (wildcardTextMatcher(origin, 'http://localhost:8080*')) return 'docker';
  else if (wildcardTextMatcher(origin, 'http://localhost*')) return 'local';
  else if (wildcardTextMatcher(origin, 'http://*.elasticbeanstalk.com')) return 'aws';
  else {
    throw new Error(`Undefined base URL: ${origin}`);
  }
}
