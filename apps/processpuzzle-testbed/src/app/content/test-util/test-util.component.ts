import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MarkdownComponent } from 'ngx-markdown';

@Component({
  selector: 'app-test-utils',
  imports: [CommonModule, MarkdownComponent],
  templateUrl: './test-util.component.html',
  styleUrl: './test-util.component.css',
})
export class TestUtilsComponent {
  // region event handling methods
  onLoad($event: string) {
    // TODO: find out the use of this event
  }

  onError($event: string | Error) {
    // TODO: find out the use of this event
  }

  // endregion
}
