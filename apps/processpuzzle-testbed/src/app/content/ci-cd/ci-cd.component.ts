import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MarkdownComponent } from 'ngx-markdown';

@Component({
  selector: 'app-ci-cd',
  imports: [CommonModule, MarkdownComponent],
  templateUrl: './ci-cd.component.html',
  styleUrl: './ci-cd.component.scss',
})
export class CiCdComponent {
  // region event handling methods
  onLoad($event: string) {
    // TODO: find out the use of this event
  }

  onError($event: string | Error) {
    // TODO: find out the use of this event
  }

  // endregion
}
