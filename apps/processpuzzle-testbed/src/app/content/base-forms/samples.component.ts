import { Component, inject, OnInit, signal, WritableSignal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, NavigationEnd, Router, RouterLink, RouterOutlet } from '@angular/router';
import { MatButtonToggle, MatButtonToggleGroup } from '@angular/material/button-toggle';
import { MatDivider } from '@angular/material/divider';
import { filter, startWith } from 'rxjs';
import { TranslocoDirective } from '@jsverse/transloco';

@Component({
  selector: 'base-entity-samples',
  standalone: true,
  imports: [CommonModule, RouterLink, RouterOutlet, MatButtonToggleGroup, MatButtonToggle, MatDivider, TranslocoDirective],
  template: `
    <ng-container *transloco="let t; prefix: 'base-entity'">
      <div style="margin-bottom: 20px">{{ t('samples_desc_1') }}</div>
      <img src="https://github.com/ZsZs/processpuzzle/blob/develop/docs/base-entity-sample_entities.png?raw=true" width="600px" alt="Sample Entities" />
      <div>
        <strong>{{ t('samples_desc_1') }}</strong>
      </div>
      <div style="margin-top: 20px">
        <mat-button-toggle-group name="fontStyle" [value]="selectedButton()" aria-label="Font Style">
          <mat-button-toggle routerLink="test-entity" value="test-entity">Test Entity</mat-button-toggle>
          <mat-button-toggle routerLink="test-entity-component" value="test-entity-component">Test Entity Komponente</mat-button-toggle>
          <mat-button-toggle routerLink="trunk-data" value="trunk-data">Stamm Data</mat-button-toggle>
          <mat-button-toggle routerLink="firestore-doc" value="firestore-doc">Firestore Document</mat-button-toggle>
        </mat-button-toggle-group>
      </div>
      <mat-divider />
      <router-outlet></router-outlet>
    </ng-container>
  `,
})
export class SamplesComponent implements OnInit {
  route = inject(ActivatedRoute);
  router = inject(Router);
  selectedButton: WritableSignal<string> = signal('test-entity');

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
          if (currentUrl.includes('test-entity-component')) this.selectedButton.set('test-entity-component');
          else if (currentUrl.includes('test-entity')) this.selectedButton.set('test-entity');
          else if (currentUrl.includes('trunk-data')) this.selectedButton.set('trunk-data');
          else this.selectedButton.set('');
        }
      });
  }
}
