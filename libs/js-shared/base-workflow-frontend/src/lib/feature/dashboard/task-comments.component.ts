import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal, viewChild } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup } from '@angular/forms';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { BaseEntityAttrDescriptor, commentsOf, CommentsComponent, FormControlType, RichTextContent } from '@processpuzzle/base-entity';
import { TASK_DASHBOARD_I18N_SCOPE } from '../../base-workflow.i18n';
import { TaskInstance } from '../../domain/execution/workflow-instance';

const COMMENTS_ATTR = 'comments';

/**
 * The notes left on the open task: its history, and — while the task is ACTIVE — an editor for one more.
 *
 * **base-entity's COMMENTS control, not a look-alike**, so a task's comments read exactly as a record's do. That
 * control saves with its form, and the dashboard has none, so this component is the form: one `comments` control,
 * and an Add button that posts the draft — the entry the control appends to the history.
 *
 * What is posted is the content alone. The control stamps its draft with the session user for display, but the
 * backend re-stamps author and time from the token; a client cannot put words in anyone else's name.
 *
 * The host re-creates this component when the task's comment count changes, which is what clears the editor
 * after a save — see `TaskDetailComponent`. Until then {@link sent} keeps the same draft from being posted twice.
 */
@Component({
  selector: 'pp-task-comments',
  standalone: true,
  imports: [CommentsComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <base-comments [config]="descriptor()" [entity]="task()" entityName="Task Instance" [value]="task().comments" />
    @if (editable()) {
      <div class="comments__actions">
        <button type="button" class="comments__add" [disabled]="!hasDraft() || sent() || isBusy()" data-testid="task-comment-add" (click)="add()">
          {{ addKey | transloco }}
        </button>
      </div>
    }
  `,
  styles: `
    .comments__actions {
      display: flex;
      justify-content: flex-end;
    }
    .comments__add {
      padding: 5px 14px;
      border: 1px solid var(--pp-color-dark-blue, rgb(24, 111, 206));
      border-radius: 4px;
      background-color: var(--pp-button-primary-bg, rgb(24, 111, 206));
      color: var(--pp-button-primary-text, #eeeeee);
      font: inherit;
      font-size: 13px;
      cursor: pointer;
    }
    .comments__add:disabled {
      opacity: 0.5;
      cursor: default;
    }
  `,
})
export class TaskCommentsComponent {
  readonly task = input.required<TaskInstance>();
  /** Whether a new comment may be written — an ACTIVE task. The history shows either way. */
  readonly editable = input(false);
  readonly isBusy = input(false);
  /** Resolves whether the comment was saved. */
  readonly commentRequested = input.required<(content: RichTextContent) => Promise<boolean>>();

  protected readonly addKey = `${TASK_DASHBOARD_I18N_SCOPE}.comment_add`;
  protected readonly sent = signal(false);

  private readonly control = new FormControl<unknown>([]);
  private readonly form = new FormGroup({ [COMMENTS_ATTR]: this.control });
  private readonly commentsControl = viewChild.required(CommentsComponent);
  private readonly value = toSignal(this.control.valueChanges, { initialValue: [] as unknown });
  private readonly label = toSignal(inject(TranslocoService).selectTranslate(`${TASK_DASHBOARD_I18N_SCOPE}.comments`), { initialValue: '' });

  protected readonly descriptor = computed(() => {
    const descriptor = new BaseEntityAttrDescriptor(COMMENTS_ATTR, FormControlType.COMMENTS, this.label());
    descriptor.disabled = !this.editable();
    return descriptor;
  });

  /** The control's value is the history plus one draft entry exactly while the editor holds text. */
  protected readonly hasDraft = computed(() => commentsOf(this.value()).length > this.task().comments.length);

  constructor() {
    // The COMMENTS control reads its control from a plain `formGroup` field, as the entity form builder sets it.
    effect(() => {
      this.commentsControl().formGroup = this.form;
    });
  }

  protected async add(): Promise<void> {
    const draft = commentsOf(this.control.value).at(-1);
    if (!this.hasDraft() || !draft) return;
    this.sent.set(true);
    const saved = await this.commentRequested()(draft.content);
    if (!saved) this.sent.set(false);
  }
}
