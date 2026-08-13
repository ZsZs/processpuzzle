import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MarkdownComponent } from 'ngx-markdown';

@Component({
  selector: 'app-ci-cd',
  imports: [CommonModule, MarkdownComponent],
  templateUrl: './ci-cd.component.html',
})
export class CiCdComponent {
  // region event handling methods
  onLoad(): void {
    return;
  }

  onError(): void {
    return;
  }

  // endregion
}
