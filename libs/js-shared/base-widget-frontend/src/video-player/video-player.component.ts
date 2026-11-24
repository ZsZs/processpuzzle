import { HttpClient } from '@angular/common/http';
import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { ObjectStoreService } from '@processpuzzle/base-entity';
import { RUNTIME_CONFIGURATION, serviceRootOf } from '@processpuzzle/util';
import { pickVideoRow, storedObjectOf, VideoPlayerRow } from './video-player.model';

/**
 * Plays the video an entity row holds, in the language the application runs in, beside the row's text. Which rows
 * are read is the designer's choice: an entity name, an RSQL query over it, and the attribute that holds the video.
 * Rows are read anonymously, as the testbed allows reads without sign-in.
 */
@Component({
  selector: 'pp-video-player',
  standalone: true,
  imports: [TranslocoPipe],
  template: `
    <div class="pp-video-player">
      @if (loading()) {
        <p>{{ 'widgets.video_player.loading' | transloco }}</p>
      } @else if (row(); as current) {
        @if (videoUri(); as uri) {
          <video class="pp-video-player__video" controls [src]="uri" preload="metadata"></video>
        } @else {
          <p>{{ 'widgets.video_player.no_video' | transloco }}</p>
        }
        @if (text(current); as text) {
          <div class="pp-video-player__text">{{ text }}</div>
        }
      } @else {
        <p>{{ 'widgets.video_player.not_found' | transloco }}</p>
      }
    </div>
  `,
  styles: [
    `
      .pp-video-player {
        max-width: 960px;
        padding: 16px;
      }
      .pp-video-player__video {
        width: 100%;
        background: #000;
      }
      .pp-video-player__text {
        margin-top: 16px;
        white-space: pre-wrap;
        line-height: 1.5;
      }
    `,
  ],
})
export class VideoPlayerComponent {
  private readonly http = inject(HttpClient);
  private readonly objectStore = inject(ObjectStoreService);
  private readonly transloco = inject(TranslocoService);
  private readonly root = serviceRootOf(inject(RUNTIME_CONFIGURATION), 'BACKEND_SERVICE_ROOT');

  /** The entity whose rows are read, by its definition code. */
  readonly entityName = input.required<string>();
  /** An RSQL query over that entity, such as `name==entity-designer`. */
  readonly query = input.required<string>();
  /** The ARTIFACT attribute that holds the video. */
  readonly attribute = input.required<string>();
  /** A text attribute shown beneath the video, when there is one. */
  readonly textAttribute = input<string>('');

  readonly loading = signal(true);
  private readonly rows = signal<VideoPlayerRow[]>([]);
  private readonly language = toSignal(this.transloco.langChanges$, { initialValue: this.transloco.getActiveLang() });
  readonly row = computed(() => pickVideoRow(this.rows(), this.language()));
  readonly videoUri = signal<string | null>(null);

  constructor() {
    effect((onCleanup) => {
      const entityName = this.entityName();
      const query = encodeURIComponent(this.query());
      const subscription = this.http.get<{ content: VideoPlayerRow[] }>(`${this.root}/entities/${entityName}?size=50&rsql=${query}`).subscribe({
        next: ({ content }) => {
          this.rows.set(content ?? []);
          this.loading.set(false);
        },
        error: () => this.loading.set(false),
      });
      onCleanup(() => subscription.unsubscribe());
    });

    effect((onCleanup) => {
      const stored = storedObjectOf(this.row(), this.attribute());
      if (!stored) {
        this.videoUri.set(null);
        return;
      }
      const subscription = this.objectStore.getObjectUriByID(stored.bucket, stored.objectId).subscribe(({ uri }) => this.videoUri.set(uri ?? null));
      onCleanup(() => subscription.unsubscribe());
    });
  }

  protected text(row: VideoPlayerRow): string {
    const value = row.payload[this.textAttribute()];
    return typeof value === 'string' ? value : '';
  }
}
