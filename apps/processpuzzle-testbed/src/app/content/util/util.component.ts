import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MarkdownComponent } from 'ngx-markdown';
import 'prismjs';
import 'prismjs/components/prism-typescript.min.js';
import 'prismjs/plugins/line-numbers/prism-line-numbers.js';
import 'prismjs/plugins/line-highlight/prism-line-highlight.js';

@Component({
  selector: 'app-utils',
  imports: [CommonModule, MarkdownComponent],
  templateUrl: './util.component.html',
  styleUrl: './util.component.scss',
})
export class UtilsComponent {
  onLoad($event: string) {
    // TODO: find out the use of this event
  }

  onError($event: string | Error) {
    // TODO: find out the use of this event
  }
}
