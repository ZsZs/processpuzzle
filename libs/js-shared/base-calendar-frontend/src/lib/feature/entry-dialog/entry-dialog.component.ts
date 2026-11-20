import { ChangeDetectionStrategy, Component, computed, DestroyRef, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { provideTranslocoScope, TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { BASE_CALENDAR_TRANSLOCO_ALIAS, BASE_CALENDAR_TRANSLOCO_SCOPE } from '../../base-calendar.i18n';
import { failureKindOf, failureTextOf } from '../../domain/calendar.service';
import { CalendarStore } from '../../domain/calendar.store';
import { AttendanceAnswerValue, AttendeeDraft, CalendarEntry, CalendarFailureKind, CalendarInfo, CalendarSlot, DirectoryMatch, EntryKind } from '../../domain/calendar.types';
import { bookableKinds, EntryFormValue, formForEntry, formForSlot, isFormComplete, toEntryInput } from '../../domain/entry-form';

/**
 * What the dialog is opened with: `create` from a slot, `edit` for an entry the caller may change, `view` for one
 * they may only read. `calendars` are the calendars the new entry may go on — the ones the caller may book on.
 */
export type EntryDialogData = { mode: 'create'; calendars: readonly CalendarInfo[]; slot: CalendarSlot } | { mode: 'edit' | 'view'; calendars: readonly CalendarInfo[]; entry: CalendarEntry };

/** What the dialog tells whoever opened it: something changed, so the calendar has to be read again. */
export type EntryDialogResult = 'saved' | 'cancelled' | undefined;

/** The text under the form when a call failed: the server's own explanation when it gave one, else a translated line. */
interface Failure {
  kind: CalendarFailureKind;
  text?: string;
}

/** How long the user must stop typing before the directory is asked, and how many characters it takes to ask. */
const SEARCH_DEBOUNCE_MS = 300;
const SEARCH_MIN_LENGTH = 2;

const TITLE_KEY_OF_MODE: Record<EntryDialogData['mode'], string> = { create: 'new', edit: 'edit', view: 'view' };

const FAILURE_KEYS: Record<CalendarFailureKind, string> = {
  forbidden: 'base_calendar.errors.forbidden',
  'not-found': 'base_calendar.errors.not_found',
  conflict: 'base_calendar.errors.conflict',
  invalid: 'base_calendar.errors.invalid',
  unavailable: 'base_calendar.errors.unavailable',
};

/**
 * Books, edits, shows and cancels one entry of a calendar.
 *
 * An entry the caller may not change is shown, not edited; one they see only as busy shows nothing but the time.
 * The form edits wall-clock values in the organization's zone and sends instants (see `entry-form.ts`). A refusal
 * stays in the dialog, under the form, so the user can correct it without losing what they typed.
 */
@Component({
  selector: 'pp-entry-dialog',
  standalone: true,
  imports: [FormsModule, MatButtonModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatSelectModule, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [provideTranslocoScope({ scope: BASE_CALENDAR_TRANSLOCO_SCOPE, alias: BASE_CALENDAR_TRANSLOCO_ALIAS })],
  template: `
    <h2 mat-dialog-title data-testid="entry-dialog-title">{{ titleKey | transloco }}</h2>
    <mat-dialog-content class="pp-entry-dialog">
      @if (editable()) {
        <form id="pp-entry-form" class="pp-entry-dialog__form" (ngSubmit)="save()">
          @if (data.mode === 'create' && data.calendars.length > 1) {
            <mat-form-field>
              <mat-label>{{ 'base_calendar.entry.calendar' | transloco }}</mat-label>
              <mat-select name="calendar" data-testid="entry-calendar" [ngModel]="calendar().key" (ngModelChange)="pickCalendar($event)">
                @for (candidate of data.calendars; track candidate.key) {
                  <mat-option [value]="candidate.key">{{ candidate.name }}</mat-option>
                }
              </mat-select>
            </mat-form-field>
          }
          @if (kinds().length > 1 && data.mode === 'create') {
            <mat-form-field>
              <mat-label>{{ 'base_calendar.entry.kind' | transloco }}</mat-label>
              <mat-select name="kind" data-testid="entry-kind" [ngModel]="value().kind" (ngModelChange)="patch({ kind: $event })">
                @for (kind of kinds(); track kind) {
                  <mat-option [value]="kind">{{ kindKey(kind) | transloco }}</mat-option>
                }
              </mat-select>
            </mat-form-field>
          }
          <mat-form-field>
            <mat-label>{{ 'base_calendar.entry.title' | transloco }}</mat-label>
            <input matInput name="title" data-testid="entry-title" maxlength="200" required [ngModel]="value().title" (ngModelChange)="patch({ title: $event })" />
          </mat-form-field>
          @if (value().kind === 'ALL_DAY') {
            <div class="pp-entry-dialog__row">
              <mat-form-field>
                <mat-label>{{ 'base_calendar.entry.first_day' | transloco }}</mat-label>
                <input matInput type="date" name="startDay" data-testid="entry-start-day" [ngModel]="value().startDay" (ngModelChange)="patch({ startDay: $event })" />
              </mat-form-field>
              <mat-form-field>
                <mat-label>{{ 'base_calendar.entry.last_day' | transloco }}</mat-label>
                <input matInput type="date" name="lastDay" data-testid="entry-last-day" [ngModel]="value().lastDay" (ngModelChange)="patch({ lastDay: $event })" />
              </mat-form-field>
            </div>
          } @else {
            <div class="pp-entry-dialog__row">
              <mat-form-field>
                <mat-label>{{ 'base_calendar.entry.start' | transloco }}</mat-label>
                <input matInput type="datetime-local" name="startLocal" data-testid="entry-start" [ngModel]="value().startLocal" (ngModelChange)="patch({ startLocal: $event })" />
              </mat-form-field>
              <mat-form-field>
                <mat-label>{{ 'base_calendar.entry.end' | transloco }}</mat-label>
                <input matInput type="datetime-local" name="endLocal" data-testid="entry-end" [ngModel]="value().endLocal" (ngModelChange)="patch({ endLocal: $event })" />
              </mat-form-field>
            </div>
          }
          <mat-form-field>
            <mat-label>{{ 'base_calendar.entry.location' | transloco }}</mat-label>
            <input matInput name="location" data-testid="entry-location" maxlength="200" [ngModel]="value().location" (ngModelChange)="patch({ location: $event })" />
          </mat-form-field>
          <mat-form-field>
            <mat-label>{{ 'base_calendar.entry.category' | transloco }}</mat-label>
            <input matInput name="category" data-testid="entry-category" maxlength="64" [ngModel]="value().category" (ngModelChange)="patch({ category: $event })" />
          </mat-form-field>
          <mat-form-field>
            <mat-label>{{ 'base_calendar.entry.description' | transloco }}</mat-label>
            <textarea matInput rows="3" name="description" data-testid="entry-description" maxlength="4000" [ngModel]="value().description" (ngModelChange)="patch({ description: $event })"></textarea>
          </mat-form-field>
          <mat-form-field>
            <mat-label>{{ 'base_calendar.entry.visibility' | transloco }}</mat-label>
            <mat-select name="visibility" data-testid="entry-visibility" [ngModel]="value().visibility" (ngModelChange)="patch({ visibility: $event })">
              <mat-option value="PUBLIC">{{ 'base_calendar.entry.visibility_public' | transloco }}</mat-option>
              <mat-option value="PRIVATE">{{ 'base_calendar.entry.visibility_private' | transloco }}</mat-option>
            </mat-select>
          </mat-form-field>
          <section class="pp-entry-dialog__invitations" data-testid="entry-invitations">
            <h3>{{ 'base_calendar.entry.invitations.title' | transloco }}</h3>
            @if (data.mode === 'edit' && data.entry.organizerDisplayName) {
              <p class="pp-entry-dialog__organizer" data-testid="entry-organizer">{{ 'base_calendar.entry.organizer' | transloco }}: {{ data.entry.organizerDisplayName }}</p>
            }
            <mat-form-field>
              <mat-label>{{ 'base_calendar.entry.invitations.search' | transloco }}</mat-label>
              <input
                matInput
                type="search"
                name="inviteSearch"
                data-testid="invite-search"
                autocomplete="off"
                [ngModel]="query()"
                (ngModelChange)="onQuery($event)"
                (keydown.enter)="$event.preventDefault()"
              />
            </mat-form-field>
            @for (match of matches(); track match.sub) {
              <button mat-button type="button" class="pp-entry-dialog__match" data-testid="invite-match" (click)="addUser(match)">{{ match.displayName }}</button>
            }
            @if (roles().length > 0) {
              <mat-form-field>
                <mat-label>{{ 'base_calendar.entry.invitations.roles' | transloco }}</mat-label>
                <mat-select name="inviteRole" data-testid="invite-role" [ngModel]="''" (ngModelChange)="addRole($event)">
                  @for (role of roles(); track role) {
                    <mat-option [value]="role">{{ role }}</mat-option>
                  }
                </mat-select>
              </mat-form-field>
            }
            @if (value().attendees.length === 0) {
              <p class="pp-entry-dialog__hint">{{ 'base_calendar.entry.invitations.none' | transloco }}</p>
            }
            <ul class="pp-entry-dialog__attendees">
              @for (draft of value().attendees; track draft.type + ':' + draft.principal) {
                <li data-testid="invite-draft">
                  <span
                    >{{ draft.displayName }}
                    @if (draft.type === 'ROLE') {
                      ({{ 'base_calendar.entry.invitations.role' | transloco }})
                    }
                  </span>
                  <button mat-button type="button" data-testid="invite-remove" (click)="removeAttendee(draft)">{{ 'base_calendar.entry.invitations.remove' | transloco }}</button>
                </li>
              }
            </ul>
          </section>
        </form>
      } @else if (shown(); as entry) {
        @if (entry.busy) {
          <p data-testid="entry-busy">{{ 'base_calendar.busy' | transloco }}</p>
          <p class="pp-entry-dialog__when">{{ when(entry) }}</p>
        } @else {
          <h3 class="pp-entry-dialog__name" data-testid="entry-view-title">{{ entry.title }}</h3>
          <p class="pp-entry-dialog__when" data-testid="entry-view-when">{{ when(entry) }}</p>
          @if (entry.location) {
            <p data-testid="entry-view-location">{{ entry.location }}</p>
          }
          @if (entry.category) {
            <p>{{ entry.category }}</p>
          }
          @if (entry.description) {
            <p class="pp-entry-dialog__description">{{ entry.description }}</p>
          }
          @if (entry.organizerDisplayName) {
            <p class="pp-entry-dialog__organizer">{{ 'base_calendar.entry.organizer' | transloco }}: {{ entry.organizerDisplayName }}</p>
          }
          @if (entry.attendees && entry.attendees.length > 0) {
            <section data-testid="entry-attendees">
              <h3>{{ 'base_calendar.entry.invitations.title' | transloco }}</h3>
              <ul class="pp-entry-dialog__attendees">
                @for (attendee of entry.attendees; track attendee.type + ':' + attendee.displayName) {
                  <li data-testid="entry-attendee">
                    <span>{{ attendee.displayName }}</span>
                    @if (attendee.type === 'ROLE') {
                      <span class="pp-entry-dialog__hint">({{ 'base_calendar.entry.invitations.role' | transloco }})</span>
                    } @else if (attendee.response) {
                      <span class="pp-entry-dialog__hint" data-testid="entry-attendee-response">{{ 'base_calendar.attendance.' + attendee.response | transloco }}</span>
                    }
                  </li>
                }
              </ul>
            </section>
          }
          @if (entry.canRespond) {
            <div class="pp-entry-dialog__rsvp" data-testid="entry-rsvp">
              <span>{{ 'base_calendar.entry.invitations.respond' | transloco }}</span>
              <button mat-button type="button" data-testid="rsvp-accept" [disabled]="working()" (click)="respond(entry, 'ACCEPTED')">{{ 'base_calendar.entry.invitations.accept' | transloco }}</button>
              <button mat-button type="button" data-testid="rsvp-tentative" [disabled]="working()" (click)="respond(entry, 'TENTATIVE')">
                {{ 'base_calendar.entry.invitations.tentative' | transloco }}
              </button>
              <button mat-button type="button" data-testid="rsvp-decline" [disabled]="working()" (click)="respond(entry, 'DECLINED')">
                {{ 'base_calendar.entry.invitations.decline' | transloco }}
              </button>
            </div>
          }
        }
      }
      @if (failure(); as problem) {
        <p class="pp-entry-dialog__error" role="alert" data-testid="entry-error">{{ problem.text ?? (failureKey(problem.kind) | transloco) }}</p>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      @if (data.mode === 'edit') {
        @if (confirmingCancel()) {
          <span class="pp-entry-dialog__confirm">{{ 'base_calendar.entry.confirm_cancel' | transloco }}</span>
          <button mat-button type="button" data-testid="entry-keep" (click)="confirmingCancel.set(false)">{{ 'base_calendar.entry.keep' | transloco }}</button>
          <button mat-flat-button type="button" data-testid="entry-cancel-confirmed" [disabled]="working()" (click)="cancelEntry()">{{ 'base_calendar.entry.cancel_confirmed' | transloco }}</button>
        } @else {
          <button mat-button type="button" data-testid="entry-cancel" (click)="confirmingCancel.set(true)">{{ 'base_calendar.entry.cancel' | transloco }}</button>
        }
      }
      @if (!confirmingCancel()) {
        <button mat-button type="button" data-testid="entry-close" [mat-dialog-close]="answered() ? 'saved' : undefined">{{ 'base_calendar.entry.close' | transloco }}</button>
        @if (editable()) {
          <button mat-flat-button type="submit" form="pp-entry-form" data-testid="entry-save" [disabled]="!complete() || working()">
            {{ (working() ? 'base_calendar.entry.saving' : 'base_calendar.entry.save') | transloco }}
          </button>
        }
      }
    </mat-dialog-actions>
  `,
  styles: `
    .pp-entry-dialog__form {
      box-sizing: border-box;
      display: flex;
      flex-direction: column;
      max-width: 100%;
      width: min(36rem, 90vw);
    }
    .pp-entry-dialog__form mat-form-field {
      display: block;
      min-width: 0;
      width: 100%;
    }
    /* Two fields side by side may shrink below an input's intrinsic width, or a date-time input overflows the dialog. */
    .pp-entry-dialog__row {
      display: flex;
      gap: 1rem;
    }
    .pp-entry-dialog__row mat-form-field {
      flex: 1 1 0;
      min-width: 0;
    }
    .pp-entry-dialog__name {
      margin: 0 0 0.25rem;
    }
    .pp-entry-dialog__when,
    .pp-entry-dialog__organizer {
      color: var(--mat-sys-on-surface-variant, #555);
    }
    .pp-entry-dialog__description {
      white-space: pre-wrap;
    }
    .pp-entry-dialog__error {
      color: var(--mat-sys-error, #b3261e);
    }
    .pp-entry-dialog__attendees {
      list-style: none;
      margin: 0;
      padding: 0;
    }
    .pp-entry-dialog__hint {
      color: var(--mat-sys-on-surface-variant, #555);
      margin: 0.25rem 0;
    }
    .pp-entry-dialog__match {
      align-self: flex-start;
    }
    .pp-entry-dialog__rsvp {
      align-items: center;
      display: flex;
      gap: 0.5rem;
    }
    .pp-entry-dialog__confirm {
      margin-right: auto;
    }
  `,
})
export class EntryDialogComponent {
  protected readonly data = inject<EntryDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject<MatDialogRef<EntryDialogComponent, EntryDialogResult>>(MatDialogRef);
  private readonly store = inject(CalendarStore);
  private readonly transloco = inject(TranslocoService);
  private searchTimer: ReturnType<typeof setTimeout> | undefined;

  protected readonly calendar = signal<CalendarInfo>(this.initialCalendar());
  protected readonly value = signal<EntryFormValue>(this.initialValue());
  protected readonly working = signal(false);
  protected readonly confirmingCancel = signal(false);
  protected readonly failure = signal<Failure | null>(null);
  protected readonly query = signal('');
  protected readonly matches = signal<DirectoryMatch[]>([]);
  protected readonly roles = signal<string[]>([]);
  /** The entry as read-only mode shows it; an answer replaces it with the server's copy. */
  private readonly viewed = signal<CalendarEntry | null>(this.data.mode === 'view' ? this.data.entry : null);
  /** Whether the user answered an invitation, so closing the dialog tells the calendar to reload. */
  protected readonly answered = signal(false);

  protected readonly editable = computed(() => this.data.mode !== 'view');
  protected readonly kinds = computed(() => bookableKinds(this.calendar()));
  protected readonly complete = computed(() => isFormComplete(this.value()));
  /** The entry as shown read-only; none when the dialog edits. */
  protected readonly shown = this.viewed.asReadonly();
  protected readonly titleKey = `base_calendar.entry.${TITLE_KEY_OF_MODE[this.data.mode]}`;

  constructor() {
    inject(DestroyRef).onDestroy(() => clearTimeout(this.searchTimer));
    if (this.editable()) {
      void this.store.roleCatalog().then(
        (catalog) => this.roles.set(catalog.map((role) => role.name)),
        () => this.roles.set([]),
      );
    }
  }

  /** Asks the directory once the user has paused typing; a short text clears the matches without asking. */
  protected onQuery(text: string): void {
    this.query.set(text);
    clearTimeout(this.searchTimer);
    const wanted = text.trim();
    if (wanted.length < SEARCH_MIN_LENGTH) {
      this.matches.set([]);
      return;
    }
    this.searchTimer = setTimeout(() => void this.search(wanted), SEARCH_DEBOUNCE_MS);
  }

  protected addUser(match: DirectoryMatch): void {
    this.addDraft({ type: 'USER', principal: match.sub, displayName: match.displayName, required: true });
    this.query.set('');
    this.matches.set([]);
  }

  protected addRole(name: string): void {
    if (name) this.addDraft({ type: 'ROLE', principal: name, displayName: name, required: true });
  }

  protected removeAttendee(draft: AttendeeDraft): void {
    this.patch({ attendees: this.value().attendees.filter((candidate) => candidate !== draft) });
  }

  protected async respond(entry: CalendarEntry, response: AttendanceAnswerValue): Promise<void> {
    await this.run(async () => {
      this.viewed.set(await this.store.answer(entry.calendarKey, entry.id, response));
      this.answered.set(true);
    });
  }

  private addDraft(draft: AttendeeDraft): void {
    const present = this.value().attendees.some((candidate) => candidate.type === draft.type && candidate.principal === draft.principal);
    if (!present) this.patch({ attendees: [...this.value().attendees, draft] });
  }

  private async search(wanted: string): Promise<void> {
    try {
      const found = await this.store.searchDirectory(wanted);
      if (this.query().trim() === wanted) this.matches.set(found);
    } catch {
      this.matches.set([]);
    }
  }

  protected patch(change: Partial<EntryFormValue>): void {
    this.value.update((current) => ({ ...current, ...change }));
  }

  /** Switches the calendar of a new entry; the kind falls back to one the new calendar accepts. */
  protected pickCalendar(key: string): void {
    const next = this.data.calendars.find((candidate) => candidate.key === key);
    if (!next) return;
    this.calendar.set(next);
    if (!bookableKinds(next).includes(this.value().kind)) {
      this.patch({ kind: bookableKinds(next)[0] });
    }
  }

  protected async save(): Promise<void> {
    const entry = this.data.mode === 'edit' ? this.data.entry : undefined;
    const input = toEntryInput(this.value(), this.calendar(), entry?.version);
    if (!input || !this.complete()) return;
    await this.run(async () => {
      await (entry ? this.store.update(this.calendar().key, entry.id, input) : this.store.book(this.calendar().key, input));
      this.dialogRef.close('saved');
    });
  }

  protected async cancelEntry(): Promise<void> {
    if (this.data.mode !== 'edit') return;
    const { entry } = this.data;
    await this.run(async () => {
      await this.store.cancel(entry.calendarKey, entry.id);
      this.dialogRef.close('cancelled');
    });
  }

  protected kindKey(kind: EntryKind): string {
    return kind === 'ALL_DAY' ? 'base_calendar.entry.kind_all_day' : 'base_calendar.entry.kind_timed';
  }

  protected failureKey(kind: CalendarFailureKind): string {
    return FAILURE_KEYS[kind];
  }

  /** When the entry takes place, written the way the user's language writes it, in the organization's zone. */
  protected when(entry: CalendarEntry): string {
    const locale = this.transloco.getActiveLang();
    if (entry.kind === 'ALL_DAY' && entry.startDate && entry.endDate) {
      const format = new Intl.DateTimeFormat(locale, { dateStyle: 'medium', timeZone: 'UTC' });
      const first = new Date(`${entry.startDate}T00:00:00Z`);
      const last = new Date(new Date(`${entry.endDate}T00:00:00Z`).getTime() - 24 * 3600 * 1000);
      return last > first ? `${format.format(first)} – ${format.format(last)}` : format.format(first);
    }
    if (!entry.start || !entry.end) return '';
    const format = new Intl.DateTimeFormat(locale, { dateStyle: 'medium', timeStyle: 'short', timeZone: this.calendar().effectiveTimeZone });
    return format.formatRange(new Date(entry.start), new Date(entry.end));
  }

  private async run(action: () => Promise<void>): Promise<void> {
    this.working.set(true);
    this.failure.set(null);
    try {
      await action();
    } catch (error) {
      this.failure.set({ kind: failureKindOf(error), text: failureTextOf(error) });
      this.confirmingCancel.set(false);
    } finally {
      this.working.set(false);
    }
  }

  private initialCalendar(): CalendarInfo {
    if (this.data.mode === 'create') return this.data.calendars[0];
    const key = this.data.entry.calendarKey;
    return this.data.calendars.find((candidate) => candidate.key === key) ?? this.data.calendars[0];
  }

  private initialValue(): EntryFormValue {
    const calendar = this.initialCalendar();
    if (this.data.mode === 'create') return formForSlot(calendar, this.data.slot);
    return formForEntry(calendar, this.data.entry);
  }
}
