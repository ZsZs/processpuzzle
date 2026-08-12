import { Component, inject, OnInit, signal, WritableSignal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { NavigationEnd, Router, RouterLink, RouterOutlet } from '@angular/router';
import { MatButtonToggle, MatButtonToggleGroup } from '@angular/material/button-toggle';
import { MatDivider } from '@angular/material/divider';
import { filter, startWith } from 'rxjs';
import { TranslocoDirective } from '@jsverse/transloco';

/**
 * The live document screen, mounted here the same way `base-apps` mounts `App Definition`: this tab's route
 * declares `BASE_DOCUMENT_ROUTES` as static children and this component renders them through its outlet.
 *
 * Static children rather than `loadChildren` is what makes the entity addressable by the generated e2e
 * suites. `EntityRouteRegistry` derives a descriptor's base path by walking the router's configuration, and a
 * `loadChildren` branch that has never been entered is not in it — which is why `/design/document`, real as
 * it is, leaves `Document` in the registry with no route at all and the suites fall back to guessing
 * `/base-entity/samples/document`. Mounting the same routes under a statically declared branch reports the
 * path, so `[Document] CRUD` / `LIST` / `RELATIONSHIP` address the screen the application actually has.
 *
 * `/design/document` stays exactly as it was — the design section is where a user reaches documents; this is
 * the testbed's own copy of the screen, next to every other feature's sample.
 */
@Component({
  selector: 'base-documents-samples',
  standalone: true,
  imports: [CommonModule, RouterLink, RouterOutlet, MatButtonToggleGroup, MatButtonToggle, MatDivider, TranslocoDirective],
  template: `
    <ng-container *transloco="let t; prefix: 'base-documents'">
      <div style="margin-bottom: 20px">{{ t('samples_desc_1') }}</div>
      <div>
        <strong>{{ t('samples_desc_2') }}</strong>
      </div>
      <div style="margin-top: 20px">
        <mat-button-toggle-group name="documentSample" [value]="selectedButton()" aria-label="Document Sample">
          <mat-button-toggle routerLink="document" value="document">Document</mat-button-toggle>
        </mat-button-toggle-group>
      </div>
      <mat-divider />
      <router-outlet></router-outlet>
    </ng-container>
  `,
})
export class SamplesComponent implements OnInit {
  router = inject(Router);
  selectedButton: WritableSignal<string> = signal('document');

  ngOnInit() {
    this.subscribeToRoutingEvents();
  }

  private subscribeToRoutingEvents() {
    this.router.events
      .pipe(
        filter((event) => event instanceof NavigationEnd),
        startWith(this.router),
      )
      .subscribe((event) => {
        const currentUrl: string = event.url;
        if (currentUrl) {
          if (currentUrl.includes('document')) this.selectedButton.set('document');
          else this.selectedButton.set('');
        }
      });
  }
}
