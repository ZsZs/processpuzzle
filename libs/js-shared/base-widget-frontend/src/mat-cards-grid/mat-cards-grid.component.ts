import { Component, inject, Input } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { NgClass } from '@angular/common';
import { RouterLink } from '@angular/router';
import { TranslocoDirective } from '@jsverse/transloco';
import { CardsGridSpec } from './cards-spec';
import { LayoutService } from '@processpuzzle/util';
import { MatIcon } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';

@Component({
  selector: 'mat-cards-grid',
  standalone: true,
  imports: [MatCardModule, MatButtonModule, RouterLink, TranslocoDirective, NgClass, MatIcon, MatMenuModule],
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
              @if (card.icon) {
                <span class="toolbar-spacer"></span>
                <button mat-icon-button>
                  <mat-icon class="icon-large material-symbols-outlined">{{ card.icon }}</mat-icon>
                </button>
              }
              @if (card.menuItems && card.menuItems.length > 0) {
                @if (!card.icon) {
                  <span class="toolbar-spacer"></span>
                }
                <button mat-icon-button [matMenuTriggerFor]="cardMenu" aria-label="Card menu">
                  <mat-icon class="icon-large material-symbols-outlined">more_vert</mat-icon>
                </button>
                <mat-menu #cardMenu="matMenu">
                  @for (menuItem of card.menuItems; track $index) {
                    <button mat-menu-item [routerLink]="menuItem.link">
                      @if (menuItem.icon) {
                        <mat-icon class="material-symbols-outlined">{{ menuItem.icon }}</mat-icon>
                      }
                      <span>{{ t(menuItem.label) }}</span>
                    </button>
                  }
                </mat-menu>
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
                    <button [matButton]="action.buttonType ?? 'elevated'" [routerLink]="action.link">{{ t(action.caption) }}</button>
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
