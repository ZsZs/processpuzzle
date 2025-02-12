import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MarkdownComponent } from 'ngx-markdown';

@Component({
  selector: 'app-utils',
  imports: [CommonModule, MarkdownComponent],
  templateUrl: './util.component.html',
  styleUrl: './util.component.scss',
})
export class UtilsComponent {
  // region event handling methods
  onLoad($event: string) {
    // TODO: find out the use of this event
  }

  onError($event: string | Error) {
    // TODO: find out the use of this event
  }

  // endregion
}
