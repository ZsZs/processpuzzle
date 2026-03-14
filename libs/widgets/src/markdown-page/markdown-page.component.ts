import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { MarkdownComponent } from 'ngx-markdown';
import { MatSnackBar } from '@angular/material/snack-bar';

@Component({
  selector: 'pp-markdown-page',
  imports: [CommonModule, MarkdownComponent],
  template: ` <markdown [src]="src()" (load)="onLoad($event)" (error)="onError($event)"></markdown> `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MarkdownPageComponent {
  private snackBar = inject(MatSnackBar);
  private route = inject(ActivatedRoute);
  markdownSrcInput = input<string>('');
  markdownSrc = input<string>();
  src = computed(() => this.markdownSrcInput() || this.markdownSrc() || '');

  // region event handling methods
  onLoad($event: string) {
    // TODO: find out the use of this event
  }

  onError($event: string | Error) {
    const message = typeof $event === 'string' ? $event : $event.message;
    this.snackBar.open(`Error loading markdown: ${message}`, 'Close', {
      duration: 5000,
      horizontalPosition: 'center',
      verticalPosition: 'bottom',
    });
  }
  // endregion
}
