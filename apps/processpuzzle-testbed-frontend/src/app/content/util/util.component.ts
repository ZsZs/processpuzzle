import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MarkdownComponent } from 'ngx-markdown';

@Component({
  selector: 'app-utils',
  imports: [CommonModule, MarkdownComponent],
  templateUrl: './util.component.html',
})
export class UtilsComponent {
  // region event handling methods
  onLoad(): void {
    return;
  }

  onError(): void {
    return;
  }

  // endregion
}
