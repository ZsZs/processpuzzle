import { Component, computed, inject } from '@angular/core';
import { ApplicationPropertyStore } from '../app-property/app-property.store';
import { ApplicationProperty } from '../app-property/app-property';
import { MatIcon } from '@angular/material/icon';
import { MatIconButton } from '@angular/material/button';

@Component({
  selector: 'pp-like-button',
  template: `
    <div class="like-button">
      <button mat-icon-button (click)="onLike()" aria-label="Like Button">
        <mat-icon>favorite</mat-icon>
      </button>
      <span>{{ likesCount()?.value }}</span>
    </div>
  `,
  styleUrls: ['./like-button.component.css'],
  imports: [MatIcon, MatIconButton],
})
export class LikeButtonComponent {
  private readonly LIKES_PROPERTY = 'likes';
  likesCount = computed(() => this.store.entities().find((property) => property.name === this.LIKES_PROPERTY));
  readonly store = inject(ApplicationPropertyStore);

  // region event handling methods
  onLike(): void {
    const count = this.likesCount();
    if (count) {
      count.value = (parseInt(count.value, 10) + 1).toString();
      this.store.update(count);
    } else {
      this.store.add(new ApplicationProperty(this.LIKES_PROPERTY, '1'));
    }
  }

  // endregion
}
