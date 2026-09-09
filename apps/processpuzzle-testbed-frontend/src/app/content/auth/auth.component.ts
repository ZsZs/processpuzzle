import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MarkdownComponent } from 'ngx-markdown';

@Component({
  selector: 'app-auth',
  imports: [CommonModule, MarkdownComponent],
  templateUrl: './auth.component.html',
})
export class AuthComponent {
  // region event handling methods
  onLoad(): void {
    return;
  }

  onError(): void {
    return;
  }

  // endregion
}
