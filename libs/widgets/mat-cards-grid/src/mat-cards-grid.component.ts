import { Component, Input, ViewEncapsulation } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { NgFor, NgIf } from '@angular/common';
import { RouterLink } from '@angular/router';
import { TranslocoDirective } from '@jsverse/transloco';
import { CardsGridSpec } from './cards-spec';

@Component({
  selector: 'mat-cards-grid',
  standalone: true,
  imports: [MatCardModule, MatButtonModule, NgFor, NgIf, RouterLink, TranslocoDirective],
  template: `
    <ng-container *ngFor="let card of cards">
      <ng-container *transloco="let t; prefix: card.translocoPrefix">
        <mat-card class="default-mat-card">
          <mat-card-header>
            <mat-card-title>{{ card.title }}</mat-card-title>
            <mat-card-subtitle>{{ t(card.subtitle) }}</mat-card-subtitle>
          </mat-card-header>
          <mat-card-content>
            <div>
              {{ t(card.content[0]) }}
              <ul *ngIf="card.content.length > 1">
                <li *ngFor="let item of card.content.slice(1)">{{ t(item) }}</li>
              </ul>
            </div>
          </mat-card-content>
          <mat-card-actions *ngIf="card.actions && card.actions.length > 0">
            <ng-container *ngFor="let action of card.actions">
              <button [color]="action.colour" mat-flat-button [routerLink]="action.link">{{ t(action.caption) }}</button>
            </ng-container>
          </mat-card-actions>
        </mat-card>
      </ng-container>
    </ng-container>
  `,
  encapsulation: ViewEncapsulation.None,
})
export class MatCardsGridComponent {
  @Input() cards: CardsGridSpec[] = [];
}
