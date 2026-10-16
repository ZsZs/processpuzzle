import { Component, computed, inject, signal } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { BaseEntity } from '../../base-entity/base-entity';
import { DateFormat } from '../../base-entity/date-format';
import { EntityUserContext } from '../../base-entity-user/entity-user.context';
import { DateFormatPipe } from '../../i18n/date-format.pipe';
import { EntityLabelPipe } from '../../i18n/entity-label.pipe';
import { isRichTextEmpty, RichTextContent, RichTextEditorComponent } from '../../rich-text/rich-text-editor.component';
import { BaseFormControlComponent } from '../base-form-control.component';
import { commentsOf, EntityComment } from './entity-comment';

const UNKNOWN_AUTHOR_KEY = 'base_entity.comments.unknown_author';

/**
 * A `COMMENTS` attribute: the saved comments as a read-only history, and below it an editor for a new one.
 *
 * There is no "Add" button — a comment is written into the editor and saved with the record. While the draft
 * holds text the control's value is the history plus one entry stamped with the current user and the time of
 * the last keystroke, and the control is dirty, so the form's Save carries it. Emptying the draft puts the
 * history back. Once saved, the form is rebuilt from the stored record and the draft has become a history card.
 *
 * The history is the value the form was built with and is never written to, so the control can only append —
 * the backend enforces the same rule for every other client.
 */
@Component({
  selector: 'base-comments',
  standalone: true,
  imports: [ReactiveFormsModule, RichTextEditorComponent, DateFormatPipe, EntityLabelPipe, TranslocoPipe],
  template: `
    @if (config().visible) {
      <section class="pp-comments" [id]="config().attrName">
        <h3 class="pp-comments__title">{{ config().i18nKey() | ppLabel: config().label }}</h3>
        @for (comment of history(); track $index) {
          <article class="pp-comments__card" data-testid="comment-card">
            <header class="pp-comments__meta">
              <span class="pp-comments__author">{{ comment.author || (unknownAuthorKey | transloco) }}</span>
              <time class="pp-comments__time" [attr.datetime]="comment.createdAt">{{ comment.createdAt | ppDate: timestampFormat }}</time>
            </header>
            <pp-rich-text-editor [content]="comment.content" [editable]="false" testIdPrefix="comment" />
          </article>
        }
        @if (isEditable()) {
          <pp-rich-text-editor
            class="pp-comments__new"
            [content]="draft()"
            [placeholder]="'base_entity.comments.new_placeholder' | transloco"
            testIdPrefix="new-comment"
            (contentChange)="onDraftChange($event)"
          />
        }
      </section>
    }
  `,
  styles: [
    `
      :host {
        display: block;
        margin-bottom: 16px;
      }
      .pp-comments__title {
        margin: 0 0 8px;
        font-size: 1rem;
        font-weight: 500;
      }
      .pp-comments__card {
        margin-bottom: 8px;
        padding: 8px 12px;
        border-left: 3px solid var(--pp-color-dark-blue, rgb(24, 111, 206));
        border-radius: 4px;
        background-color: rgba(0, 0, 0, 0.03);
      }
      .pp-comments__meta {
        display: flex;
        flex-wrap: wrap;
        gap: 8px;
        margin-bottom: 4px;
        font-size: 0.85rem;
        color: rgba(0, 0, 0, 0.6);
      }
      .pp-comments__author {
        font-weight: 500;
      }
    `,
  ],
})
export class CommentsComponent<Entity extends BaseEntity> extends BaseFormControlComponent<Entity> {
  protected readonly unknownAuthorKey = UNKNOWN_AUTHOR_KEY;
  protected readonly timestampFormat: DateFormat = { dateStyle: 'medium', timeStyle: 'short' };
  /** The comments saved with the record — the value the form was built with, never the draft. */
  readonly history = computed<EntityComment[]>(() => commentsOf(this.value()));
  readonly draft = signal<RichTextContent | null>(null);

  private readonly userContext = inject(EntityUserContext);
  private readonly transloco = inject(TranslocoService, { optional: true });

  /** A disabled attribute or control shows the history alone. */
  isEditable(): boolean {
    return !this.config().disabled && this.formGroup?.get(this.config().attrName)?.enabled !== false;
  }

  onDraftChange(content: RichTextContent): void {
    this.draft.set(content);
    const control = this.formGroup.get(this.config().attrName);
    if (!control) return;

    if (isRichTextEmpty(content)) {
      control.setValue(this.history());
      control.markAsPristine();
      return;
    }
    control.setValue([...this.history(), this.stamp(content)]);
    control.markAsDirty();
    control.markAsTouched();
  }

  private stamp(content: RichTextContent): EntityComment {
    const authorId = this.userContext.userId();
    const author = this.userContext.displayName() || authorId || this.unknownAuthor();
    return { author, ...(authorId ? { authorId } : {}), createdAt: new Date().toISOString(), content };
  }

  /** Stamped rather than left blank: an entry needs an author, and a reader needs more than an empty line. */
  private unknownAuthor(): string {
    const label = this.transloco?.translate(UNKNOWN_AUTHOR_KEY);
    return label && label !== UNKNOWN_AUTHOR_KEY ? label : 'Unknown';
  }
}
