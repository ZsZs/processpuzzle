import { HttpClient } from '@angular/common/http';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { ObjectStoreService } from '@processpuzzle/base-entity';
import { RUNTIME_CONFIGURATION, serviceRootOf } from '@processpuzzle/util';
import { pickTutorial, TutorialRow } from './tutorial.model';

/**
 * Plays one designer tutorial in the language the application runs in, beside its transcript. The rows are the
 * `tutorial` entity's, read anonymously — the testbed allows reads without sign-in.
 */
@Component({
  selector: 'pp-tutorial-viewer',
  standalone: true,
  imports: [TranslocoPipe],
  template: `
    <div class="tutorial-viewer">
      @if (loading()) {
        <p>{{ 'tutorial.loading' | transloco }}</p>
      } @else if (row(); as current) {
        <h2>{{ current.payload.title }}</h2>
        @if (videoUri(); as uri) {
          <video class="tutorial-video" controls [src]="uri" preload="metadata"></video>
        } @else {
          <p>{{ 'tutorial.no_video' | transloco }}</p>
        }
        @if (current.payload.transcript) {
          <div class="tutorial-transcript">{{ current.payload.transcript }}</div>
        }
      } @else {
        <p>{{ 'tutorial.not_found' | transloco }}</p>
      }
    </div>
  `,
  styles: [
    `
      .tutorial-viewer {
        max-width: 960px;
        padding: 16px;
      }
      .tutorial-video {
        width: 100%;
        background: #000;
      }
      .tutorial-transcript {
        margin-top: 16px;
        white-space: pre-wrap;
        line-height: 1.5;
      }
    `,
  ],
})
export class TutorialViewerComponent implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly objectStore = inject(ObjectStoreService);
  private readonly transloco = inject(TranslocoService);
  private readonly root = serviceRootOf(inject(RUNTIME_CONFIGURATION), 'BACKEND_SERVICE_ROOT');
  private readonly tutorialKey = inject(ActivatedRoute).snapshot.paramMap.get('key') ?? '';

  readonly loading = signal(true);
  private readonly rows = signal<TutorialRow[]>([]);
  readonly row = computed(() => pickTutorial(this.rows(), this.transloco.getActiveLang()));
  readonly videoUri = signal<string | null>(null);

  ngOnInit(): void {
    const rsql = encodeURIComponent(`tutorialKey==${this.tutorialKey}`);
    this.http.get<{ content: TutorialRow[] }>(`${this.root}/entities/tutorial?size=50&rsql=${rsql}`).subscribe({
      next: ({ content }) => {
        this.rows.set(content ?? []);
        this.loading.set(false);
        const video = this.row()?.payload.video;
        if (video) {
          this.objectStore.getObjectUriByID(video.bucket, video.objectId).subscribe(({ uri }) => this.videoUri.set(uri ?? null));
        }
      },
      error: () => this.loading.set(false),
    });
  }
}
