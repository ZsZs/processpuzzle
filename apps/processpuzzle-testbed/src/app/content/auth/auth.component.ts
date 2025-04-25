import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MarkdownComponent } from 'ngx-markdown';

@Component({
  selector: 'app-auth',
  imports: [CommonModule, MarkdownComponent],
  templateUrl: './auth.component.html',
  styleUrl: './auth.component.scss',
})
export class AuthComponent {
  // region event handling methods
  onLoad($event: string) {
    // TODO: find out the use of this event
  }

  onError($event: string | Error) {
    // TODO: find out the use of this event
  }

  // endregion
}
