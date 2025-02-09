import { Component, inject, OnInit, signal, WritableSignal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, NavigationEnd, Router, RouterLink, RouterOutlet } from '@angular/router';
import { MatButtonToggle, MatButtonToggleGroup } from '@angular/material/button-toggle';
import { MatDivider } from '@angular/material/divider';
import { filter, startWith } from 'rxjs';
import { MatTab, MatTabGroup } from '@angular/material/tabs';
import { MarkdownComponent } from 'ngx-markdown';

@Component({
  selector: 'base-forms',
  standalone: true,
  imports: [CommonModule, RouterLink, RouterOutlet, MatButtonToggleGroup, MatButtonToggle, MatDivider, MarkdownComponent, MatTabGroup, MatTab],
  template: `
    <mat-tab-group>
      <mat-tab label="Overview">
        <markdown
          clipboard
          mermaid
          [src]="'https://raw.githubusercontent.com/ZsZs/processpuzzle/refs/heads/develop/libs/base-entity/README.md'"
          (load)="onLoad($event)"
          (error)="onError($event)"
          ngPreserveWhitespaces
        ></markdown>
      </mat-tab>
      <mat-tab label="Samples">
        <div>The following tables and forms helps to manage these custom entities:</div>
        <img src="https://github.com/ZsZs/processpuzzle/blob/develop/docs/base-entity-sample_entities.png?raw=true" alt="Sample Entities" />
        <div>
          <mat-button-toggle-group name="fontStyle" [value]="selectedButton()" aria-label="Font Style">
            <mat-button-toggle routerLink="/base-forms/test-entity" value="test-entity">Test Entity</mat-button-toggle>
            <mat-button-toggle routerLink="/base-forms/test-entity-component" value="test-entity-component">Test Entity Komponente</mat-button-toggle>
            <mat-button-toggle routerLink="/base-forms/trunk-data" value="trunk-data">Stamm Data</mat-button-toggle>
          </mat-button-toggle-group>
        </div>
        <mat-divider />
        <router-outlet></router-outlet>
      </mat-tab>
    </mat-tab-group>
  `,
  styles: ``,
})
export class SampleFormsComponent implements OnInit {
  route = inject(ActivatedRoute);
  router = inject(Router);
  selectedButton: WritableSignal<string> = signal('test-entity');

  // region Angular lifecycle hooks
  ngOnInit() {
    this.subscribeToRoutingEvents();
  }

  // endregion

  // region event handling methods
  onLoad($event: string) {
    // TODO: find out the use of this event
  }

  onError($event: string | Error) {
    // TODO: find out the use of this event
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
