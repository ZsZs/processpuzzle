import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MarkdownComponent } from 'ngx-markdown';

@Component({
  selector: 'app-widgets',
  imports: [CommonModule, MarkdownComponent],
  templateUrl: './widgets.component.html',
  styleUrl: './widgets.component.scss',
})
export class WidgetsComponent {
  // region event handling methods
  onLoad(): void {
    return;
  }

  onError(): void {
    return;
  }

  // endregion
}
