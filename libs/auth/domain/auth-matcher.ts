import { UrlSegment } from '@angular/router';

// Custom matcher function to match any URL containing 'auth'
export function authMatcher(segments: UrlSegment[]) {
  const hasAuth = segments.some((segment) => segment.path === 'auth');
  if (hasAuth) {
    // Find the index of 'auth' segment
    const authIndex = segments.findIndex((segment) => segment.path === 'auth');
    // Return consumed segments up to and including 'auth'
    return {
      consumed: segments.slice(0, authIndex + 1),
      posParams: {},
    };
  }
  return null;
}
