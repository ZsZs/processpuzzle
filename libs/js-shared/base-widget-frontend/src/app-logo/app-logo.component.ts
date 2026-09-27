import { Component, computed, inject, input } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { ArtifactAttr, ObjectStoreService } from '@processpuzzle/base-entity';
import { catchError, map, of, switchMap } from 'rxjs';
import { APPLICATION_CONTEXT } from '../app-context/application-context';

/**
 * The application's logo, small enough for a header row, linking home.
 *
 * The image is the `logo` prop when one is set — an artifact of the object store, which is why the designer
 * edits it with the ARTIFACT control — and otherwise the logo URL of the {@link APPLICATION_CONTEXT}. A logo
 * whose URL cannot be resolved renders nothing rather than a broken image.
 */
@Component({
  selector: 'pp-app-logo',
  standalone: true,
  imports: [RouterLink],
  template: `
    @if (src(); as src) {
      @if (link(); as link) {
        <a class="pp-app-logo__link" [routerLink]="link"><img class="pp-app-logo" [src]="src" [alt]="altText()" /></a>
      } @else {
        <img class="pp-app-logo" [src]="src" [alt]="altText()" />
      }
    }
  `,
  styles: [
    `
      :host {
        display: inline-flex;
        align-items: center;
      }
      .pp-app-logo {
        display: block;
        max-height: 40px;
        max-width: 160px;
        object-fit: contain;
      }
    `,
  ],
})
export class AppLogoComponent {
  readonly logo = input<ArtifactAttr | undefined>(undefined);
  readonly alt = input('');
  /** Router link the logo navigates to. Empty for a logo that is only an image. */
  readonly link = input('/');

  private readonly context = inject(APPLICATION_CONTEXT, { optional: true });
  private readonly objectStore = inject(ObjectStoreService);

  private readonly artifactUrl = toSignal(
    toObservable(this.logo).pipe(
      switchMap((logo) =>
        logo?.bucket && logo.objectId
          ? this.objectStore.getObjectUriByID(logo.bucket, logo.objectId).pipe(
              map((response) => response?.uri ?? undefined),
              catchError(() => of(undefined)),
            )
          : of(undefined),
      ),
    ),
  );

  protected readonly src = computed(() => (this.logo() ? this.artifactUrl() : this.context?.logoUrl()));
  protected readonly altText = computed(() => this.alt() || this.context?.name() || 'Logo');
}
