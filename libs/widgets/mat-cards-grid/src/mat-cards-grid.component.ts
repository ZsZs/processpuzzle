import { Component, inject, Input } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { NgClass } from '@angular/common';
import { RouterLink } from '@angular/router';
import { TranslocoDirective } from '@jsverse/transloco';
import { CardsGridSpec } from './cards-spec';
import { LayoutService } from '@processpuzzle/util';

@Component({
  selector: 'mat-cards-grid',
  standalone: true,
  imports: [MatCardModule, MatButtonModule, RouterLink, TranslocoDirective, NgClass],
  template: `
    <div [ngClass]="layoutService.layoutClass()">
      @for (card of cards; track $index) {
        <ng-container *transloco="let t; prefix: card.translocoPrefix">
          <mat-card class="default-mat-card">
            <mat-card-header>
              @if (hasValue(card.title)) {
                <mat-card-title>{{ t(card.title) }}</mat-card-title>
              }
              @if (hasValue(card.subtitle)) {
                <mat-card-subtitle>{{ t(card.subtitle) }}</mat-card-subtitle>
              }
            </mat-card-header>
            @if (hasValue(card.content)) {
              <mat-card-content>
                <div>
                  {{ t(card.content[0]) }}
                  @if (card.content.length > 1) {
                    <ul>
                      @for (item of card.content.slice(1); track $index) {
                        <li>{{ t(item) }}</li>
                      }
                    </ul>
                  }
                </div>
              </mat-card-content>
            }
            @if (card.actions && card.actions.length > 0) {
              <mat-card-actions>
                @for (action of card.actions; track $index) {
                  <ng-container>
                    <!-- <button [matButton]="action.buttonType ?? 'elevated'" [routerLink]="action.link">{{ t(action.caption) }}</button> -->
                    <button mat-flat-button [color]="action.colour ?? 'primary'" [routerLink]="action.link">{{ t(action.caption) }}</button>
                  </ng-container>
                }
              </mat-card-actions>
            }
          </mat-card>
        </ng-container>
      }
    </div>
  `,
  styleUrls: ['./mat-cards-grid.component.css'],
})
export class MatCardsGridComponent {
  @Input() cards: CardsGridSpec[] = [];
  readonly layoutService = inject(LayoutService);

  hasValue(textValue: string | Array<string>) {
    return textValue.length > 0;
  }
}
