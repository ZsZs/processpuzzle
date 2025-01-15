import { Component, inject, OnInit, signal, WritableSignal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, NavigationEnd, Router, RouterLink, RouterOutlet } from '@angular/router';
import { MatButtonToggle, MatButtonToggleGroup } from '@angular/material/button-toggle';
import { MatDivider } from '@angular/material/divider';
import { filter, startWith } from 'rxjs';

@Component({
  selector: 'base-forms',
  standalone: true,
  imports: [CommonModule, RouterLink, RouterOutlet, MatButtonToggleGroup, MatButtonToggle, MatDivider],
  template: `
    <h2>Basis Formulare Test Seite</h2>
    <div>
      <mat-button-toggle-group name="fontStyle" [value]="selectedButton()" aria-label="Font Style">
        <mat-button-toggle routerLink="/base-forms/test-entity" value="test-entity">Test Entity</mat-button-toggle>
        <mat-button-toggle routerLink="/base-forms/test-entity-component" value="test-entity-component">Test Entity Komponente</mat-button-toggle>
        <mat-button-toggle routerLink="/base-forms/trunk-data" value="trunk-data">Stamm Data</mat-button-toggle>
      </mat-button-toggle-group>
    </div>
    <mat-divider />
    <router-outlet></router-outlet>
  `,
  styles: ``,
})
export class TestFormsComponent implements OnInit {
  route = inject(ActivatedRoute);
  router = inject(Router);
  selectedButton: WritableSignal<string> = signal('test-entity');

  // region Angular lifecycle hooks
  ngOnInit() {
    this.subscribeToRoutingEvents();
  }

  // endregion
  // protected, private helper methods
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
  // endregion
}
