import { HttpClient } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { MatButton } from '@angular/material/button';
import { MatFormField, MatLabel } from '@angular/material/form-field';
import { MatOption, MatSelect } from '@angular/material/select';
import { TranslocoDirective } from '@jsverse/transloco';
import { RecognitionCameraComponent, RecognitionCandidateOption, RecognitionHit } from '@processpuzzle/base-ai';
import { RUNTIME_CONFIGURATION, serviceRootOf } from '@processpuzzle/util';
import { firstValueFrom } from 'rxjs';
import { BOAT_PATH } from './boat-sample.routes';
import { OBSERVATION_CODE, RACE_CODE, REGISTRATION_CODE } from './race-sample.routes';

/** The seeded boat type's definition code, which keys its recognition profile. */
export const BOAT_ENTITY_CODE = BOAT_PATH;

/** The largest page base-entity's list endpoint accepts. */
const MAX_PAGE_SIZE = 200;

export const CHECKPOINTS = ['PRE_START', 'START', 'FINISH'] as const;
export type Checkpoint = (typeof CHECKPOINTS)[number];

interface EntityObject<P> {
  id: string;
  payload?: P;
}

interface Page<P> {
  content?: EntityObject<P>[];
}

interface BoatPayload {
  sailNumber?: string;
  name?: string;
}

interface RacePayload {
  name?: string;
  rounds?: number;
}

interface RegistrationPayload {
  race?: string;
  boat?: string;
  status?: string;
}

interface ObservationPayload {
  race: string;
  round: number;
  checkpoint: Checkpoint;
  boat: string;
  capturedAt: string;
  source: 'AUTOMATIC' | 'MANUAL';
  score?: number;
  recognitionId?: string;
}

interface Race {
  id: string;
  name: string;
  rounds: number;
}

/** One boat of the selected race's entry list, and whether it was seen at the selected checkpoint yet. */
interface Entry extends RecognitionCandidateOption {
  observation?: EntityObject<ObservationPayload>;
}

/**
 * The Recognize sample: a race office's checkpoint screen, hosting base-ai's camera widget the way any
 * application would.
 *
 * Everything but the camera is this page's and the race application's data, not base-ai's. The **context** —
 * race, round, checkpoint — is chosen here. The race's **registrations** are the constraint: their boats,
 * withdrawn ones left out, are the only candidates handed to the camera, so the same shot is matched against
 * a different fleet in another race. A hit becomes a **Race Observation** of the context, saved through
 * base-entity's REST API; a boat the camera missed is ticked by hand into the same entity. base-ai only ever
 * answers "which of these boats is it".
 */
@Component({
  selector: 'base-ai-recognize-sample',
  standalone: true,
  imports: [MatButton, MatFormField, MatLabel, MatOption, MatSelect, RecognitionCameraComponent, TranslocoDirective],
  template: `
    <div class="recognize" *transloco="let t; prefix: 'base-ai'">
      <section class="recognize__context">
        <mat-form-field>
          <mat-label>{{ t('recognize_race') }}</mat-label>
          <mat-select [value]="raceId()" (valueChange)="selectRace($event)" data-testid="race-select">
            @for (race of races(); track race.id) {
              <mat-option [value]="race.id">{{ race.name }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
        <mat-form-field>
          <mat-label>{{ t('recognize_round') }}</mat-label>
          <mat-select [value]="round()" (valueChange)="selectRound($event)" data-testid="round-select">
            @for (number of rounds(); track number) {
              <mat-option [value]="number">{{ number }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
        <mat-form-field>
          <mat-label>{{ t('recognize_checkpoint') }}</mat-label>
          <mat-select [value]="checkpoint()" (valueChange)="selectCheckpoint($event)" data-testid="checkpoint-select">
            @for (point of checkpoints; track point) {
              <mat-option [value]="point">{{ t('recognize_checkpoint_' + point) }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
      </section>

      @if (error(); as message) {
        <p class="recognize__error" role="alert">{{ message }}</p>
      }

      <div class="recognize__body">
        <section class="recognize__camera">
          <pp-recognition-camera [entityName]="entityName" [candidates]="candidates()" (recognized)="record($event)" />
        </section>

        <section class="recognize__entries">
          <strong>{{ t('recognize_entries') }} · {{ t('recognize_seen_count', { seen: seenCount(), total: entries().length }) }}</strong>
          @if (!raceId()) {
            <p>{{ t('recognize_no_race') }}</p>
          } @else if (entries().length === 0) {
            <p>{{ t('recognize_no_entries') }}</p>
          }
          <ul>
            @for (entry of entries(); track entry.objectId) {
              <li [class.recognize__seen]="!!entry.observation" data-testid="entry">
                <span class="recognize__label">{{ entry.label }}</span>
                @if (entry.observation?.payload; as seen) {
                  <span>{{ time(seen.capturedAt) }} · {{ t(seen.source === 'AUTOMATIC' ? 'recognize_automatic' : 'recognize_manual') }}</span>
                } @else {
                  <button mat-stroked-button type="button" (click)="tick(entry)" data-testid="tick">{{ t('recognize_tick') }}</button>
                }
              </li>
            }
          </ul>
        </section>
      </div>
    </div>
  `,
  styles: `
    .recognize__context {
      display: flex;
      flex-wrap: wrap;
      gap: 12px;
    }
    .recognize__body {
      display: grid;
      grid-template-columns: minmax(280px, 3fr) minmax(220px, 2fr);
      gap: 16px;
      align-items: start;
    }
    .recognize__entries ul {
      list-style: none;
      margin: 8px 0 0;
      padding: 0;
    }
    .recognize__entries li {
      display: flex;
      justify-content: space-between;
      align-items: center;
      gap: 8px;
      padding: 4px 0;
      border-bottom: 1px solid #e0e0e0;
    }
    .recognize__seen .recognize__label {
      font-weight: 600;
    }
    .recognize__error {
      color: #d9534f;
    }
    @media (max-width: 900px) {
      .recognize__body {
        grid-template-columns: 1fr;
      }
    }
  `,
})
export class RecognizeSampleComponent {
  protected readonly entityName = BOAT_ENTITY_CODE;
  protected readonly checkpoints = CHECKPOINTS;

  protected readonly races = signal<Race[]>([]);
  protected readonly raceId = signal<string | undefined>(undefined);
  protected readonly round = signal(1);
  protected readonly checkpoint = signal<Checkpoint>('START');
  protected readonly error = signal<string | undefined>(undefined);

  private readonly boats = signal<ReadonlyMap<string, string>>(new Map());
  private readonly enteredBoatIds = signal<string[]>([]);
  private readonly observations = signal<EntityObject<ObservationPayload>[]>([]);

  protected readonly rounds = computed(() => {
    const count = this.races().find((race) => race.id === this.raceId())?.rounds ?? 1;
    return Array.from({ length: Math.max(1, count) }, (_, index) => index + 1);
  });
  protected readonly entries = computed<Entry[]>(() => {
    const seen = new Map(this.observations().map((observation) => [observation.payload?.boat, observation]));
    return this.enteredBoatIds().map((objectId) => ({ objectId, label: this.boats().get(objectId) ?? objectId, observation: seen.get(objectId) }));
  });
  protected readonly candidates = computed<RecognitionCandidateOption[]>(() => this.entries().map(({ objectId, label }) => ({ objectId, label })));
  protected readonly seenCount = computed(() => this.entries().filter((entry) => entry.observation).length);

  private readonly http = inject(HttpClient);
  private readonly root = serviceRootOf(inject(RUNTIME_CONFIGURATION), 'ENTITY_SERVICE_ROOT');

  constructor() {
    void this.load();
  }

  protected async selectRace(raceId: string): Promise<void> {
    this.raceId.set(raceId);
    this.round.set(1);
    await this.attempt(async () => {
      await this.loadEntries();
      await this.loadObservations();
    });
  }

  protected async selectRound(round: number): Promise<void> {
    this.round.set(round);
    await this.attempt(() => this.loadObservations());
  }

  protected async selectCheckpoint(checkpoint: Checkpoint): Promise<void> {
    this.checkpoint.set(checkpoint);
    await this.attempt(() => this.loadObservations());
  }

  /** A hit of the camera becomes an observation; a correction replaces the observation of the same recognition. */
  protected async record(hit: RecognitionHit): Promise<void> {
    await this.attempt(async () => {
      const corrected = this.observations().find((observation) => observation.payload?.recognitionId === hit.recognitionId);
      if (corrected) await firstValueFrom(this.http.delete(this.url(OBSERVATION_CODE, corrected.id)));
      await this.save({ boat: hit.objectId, capturedAt: hit.capturedAt, source: hit.automatic ? 'AUTOMATIC' : 'MANUAL', score: hit.score, recognitionId: hit.recognitionId });
    });
  }

  /** A boat the camera did not catch, ticked by the race office: the same observation, without the AI. */
  protected async tick(entry: Entry): Promise<void> {
    await this.attempt(() => this.save({ boat: entry.objectId, capturedAt: new Date().toISOString(), source: 'MANUAL' }));
  }

  protected time(iso: string): string {
    return new Date(iso).toLocaleTimeString();
  }

  private async save(sighting: Pick<ObservationPayload, 'boat' | 'capturedAt' | 'source' | 'score' | 'recognitionId'>): Promise<void> {
    const raceId = this.raceId();
    if (!raceId) return;
    const payload: ObservationPayload = { race: raceId, round: this.round(), checkpoint: this.checkpoint(), ...sighting };
    await firstValueFrom(this.http.post(this.url(OBSERVATION_CODE), { entityDefinitionCode: OBSERVATION_CODE, payload: withoutUndefined(payload) }));
    await this.loadObservations();
  }

  private async load(): Promise<void> {
    await this.attempt(async () => {
      const [boats, races] = await Promise.all([this.list<BoatPayload>(BOAT_ENTITY_CODE), this.list<RacePayload>(RACE_CODE)]);
      this.boats.set(new Map(boats.map((boat) => [boat.id, [boat.payload?.sailNumber, boat.payload?.name].filter(Boolean).join(' ') || boat.id])));
      this.races.set(races.map((race) => ({ id: race.id, name: race.payload?.name ?? race.id, rounds: Number(race.payload?.rounds) || 1 })));
    });
    const first = this.races()[0];
    if (first) await this.selectRace(first.id);
  }

  /** The race's entry list: its registrations, withdrawn boats left out. */
  private async loadEntries(): Promise<void> {
    const registrations = await this.list<RegistrationPayload>(REGISTRATION_CODE, `race==${this.raceId()}`);
    const entered = registrations.filter((registration) => registration.payload?.status !== 'WITHDRAWN').map((registration) => registration.payload?.boat);
    this.enteredBoatIds.set([...new Set(entered.filter((boat): boat is string => !!boat))]);
  }

  private async loadObservations(): Promise<void> {
    if (!this.raceId()) {
      this.observations.set([]);
      return;
    }
    const where = `race==${this.raceId()};round==${this.round()};checkpoint==${this.checkpoint()}`;
    this.observations.set(await this.list<ObservationPayload>(OBSERVATION_CODE, where));
  }

  private async list<P>(code: string, rsql?: string): Promise<EntityObject<P>[]> {
    // The list endpoint pages at most 200 objects; a sample's boats, races and checkpoint sightings fit in one page.
    const params: Record<string, string | number> = { size: MAX_PAGE_SIZE };
    if (rsql) params['rsql'] = rsql;
    const page = await firstValueFrom(this.http.get<Page<P>>(this.url(code), { params }));
    return page.content ?? [];
  }

  private url(code: string, id?: string): string {
    return `${this.root}/entities/${code}${id ? `/${encodeURIComponent(id)}` : ''}`;
  }

  private async attempt(action: () => Promise<void>): Promise<void> {
    this.error.set(undefined);
    try {
      await action();
    } catch (error) {
      const body = (error as { error?: { errorText?: string } })?.error;
      this.error.set(body?.errorText ?? (error instanceof Error ? error.message : String(error)));
    }
  }
}

function withoutUndefined<T extends object>(value: T): Partial<T> {
  return Object.fromEntries(Object.entries(value).filter(([, item]) => item !== undefined)) as Partial<T>;
}
