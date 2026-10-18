import { Component, computed, inject, input, OnInit } from '@angular/core';
import { ContentBookStore } from '../../domain/content-book.store';
import { ContentBook } from '../../domain/content-book';
import { ContentBookWidgetComponent } from './content-book-widget.component';

/**
 * A content book's Content tab, `content-book/<id>/content`: the book itself, inline. Inline rather than in the
 * sidenav, because on this screen the sidenav is the designer's navigation and the tab bar is already the way
 * between the book's screens.
 */
@Component({
  selector: 'pp-content-book-content-tab',
  imports: [ContentBookWidgetComponent],
  template: `
    @if (slug(); as slug) {
      <pp-content-book [bookSlug]="slug" [sidenav]="false" />
    }
  `,
})
export class ContentBookContentTabComponent implements OnInit {
  /** Bound from the route's `:entityId`, as DocumentContentTabComponent receives it. */
  readonly entityId = input.required<string>();

  private readonly store = inject(ContentBookStore);

  protected readonly slug = computed(() => (this.store.currentEntity() as ContentBook | undefined)?.slug);

  ngOnInit(): void {
    this.store.setCurrentEntity(this.entityId());
  }
}
