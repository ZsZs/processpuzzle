import { Component, inject, OnInit, signal, WritableSignal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, NavigationEnd, Router, RouterLink, RouterOutlet } from '@angular/router';
import { MatButtonToggle, MatButtonToggleGroup } from '@angular/material/button-toggle';
import { MatDivider } from '@angular/material/divider';
import { filter, startWith } from 'rxjs';
import { MatTab, MatTabGroup } from '@angular/material/tabs';
import { MarkdownComponent } from 'ngx-markdown';

@Component({
  selector: 'base-entity',
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
        <div style="margin-bottom: 20px">The sample entities, to demonstrate the functionality this library are as follows:</div>
        <img src="https://github.com/ZsZs/processpuzzle/blob/develop/docs/base-entity-sample_entities.png?raw=true" width="600px" alt="Sample Entities" />
        <div><strong>The tables and forms below are generated from the entities end their attribute descriptors, run-time.</strong></div>
        <div style="margin-top: 20px">
          <mat-button-toggle-group name="fontStyle" [value]="selectedButton()" aria-label="Font Style">
            <mat-button-toggle routerLink="/base-entity/test-entity" value="test-entity">Test Entity</mat-button-toggle>
            <mat-button-toggle routerLink="/base-entity/test-entity-component" value="test-entity-component">Test Entity Komponente</mat-button-toggle>
            <mat-button-toggle routerLink="/base-entity/trunk-data" value="trunk-data">Stamm Data</mat-button-toggle>
            <mat-button-toggle routerLink="/base-entity/firestore-doc" value="firestore-doc">Firestore Document</mat-button-toggle>
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
