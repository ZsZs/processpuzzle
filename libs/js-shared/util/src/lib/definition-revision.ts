import { HttpContext, HttpContextToken, HttpInterceptorFn } from '@angular/common/http';
import { inject, InjectionToken } from '@angular/core';

/**
 * Which row of a definition the application reads: the `draft` a designer edits and the Preview shows, or the one
 * the organization last `released`, which is what the running application uses (see core's
 * `docs/definition-release.md`).
 */
export type DefinitionRevision = 'draft' | 'released';

/**
 * Answers which revision of the definitions to read right now. Called on every read, so a host can answer from the
 * route it is on — the custom application answers `draft` inside its designer and `released` everywhere else.
 *
 * **`draft` unless the host says otherwise**, and on purpose: it is what every definition endpoint serves without
 * being asked, and so what a host that has not wired this up has always read. A `released` default would show such a
 * host's designer the released rows, and its next save would write them back over the drafts.
 */
export const DEFINITION_REVISION = new InjectionToken<() => DefinitionRevision>('DEFINITION_REVISION', {
  providedIn: 'root',
  factory: () => () => 'draft',
});

/** Marks a request as a read of definitions, which {@link definitionRevisionInterceptor} addresses to a revision. */
export const DEFINITION_READ = new HttpContextToken<boolean>(() => false);

/** A request context marking a read of definitions; see {@link DEFINITION_READ}. */
export function definitionRead(context: HttpContext = new HttpContext()): HttpContext {
  return context.set(DEFINITION_READ, true);
}

/**
 * Adds `revision=<current>` to every GET a definition reader marked with {@link DEFINITION_READ}, from
 * {@link DEFINITION_REVISION}. One place deciding, so that no reader can pick a revision of its own and disagree with
 * the others about what the application is showing.
 *
 * Opt-in: a host registers it with its `HttpClient`. Without it nothing is added, and the endpoints serve drafts.
 */
export const definitionRevisionInterceptor: HttpInterceptorFn = (request, next) => {
  if (request.method !== 'GET' || !request.context.get(DEFINITION_READ) || request.params.has('revision')) {
    return next(request);
  }
  const revision = inject(DEFINITION_REVISION)();
  return next(request.clone({ setParams: { revision } }));
};
