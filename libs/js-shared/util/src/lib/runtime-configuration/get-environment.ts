import { wildcardTextMatcher } from '../wildcard-text-matcher';

export function getEnvironment(originUrl?: string): string {
  const origin = originUrl ?? location.origin;
  // eslint-disable-next-line sonarjs/no-clear-text-protocols -- localhost URLs are inherently HTTP during development
  if (wildcardTextMatcher(origin, 'http://localhost:8080*')) return 'docker';
  // eslint-disable-next-line sonarjs/no-clear-text-protocols -- localhost URLs are inherently HTTP during development
  else if (wildcardTextMatcher(origin, 'http://localhost*')) return 'local';
  // eslint-disable-next-line sonarjs/no-clear-text-protocols -- AWS Elastic Beanstalk uses HTTP internally
  else if (wildcardTextMatcher(origin, 'http://*.elasticbeanstalk.com')) return 'aws';
  else {
    throw new Error(`Undefined base URL: ${origin}`);
  }
}
