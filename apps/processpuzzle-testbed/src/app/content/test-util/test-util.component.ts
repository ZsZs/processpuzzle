import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MarkdownComponent } from 'ngx-markdown';

@Component({
  selector: 'app-test-utils',
  imports: [CommonModule, MarkdownComponent],
  templateUrl: './test-util.component.html',
})
export class TestUtilsComponent {
  // region event handling methods
  onLoad(): void {
    return;
  }

  onError(): void {
    return;
  }

  // endregion
}
