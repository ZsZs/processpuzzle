import { Component } from '@angular/core';
import { SampleHostComponent, SampleTab } from '../common/sample-host.component';

/**
 * Host of the chat definition authoring screens. They come from `BASE_CHAT_ROUTES`, mounted as this route's
 * children in `app.routes.ts`, so what is demonstrated here is the library's own authoring branch.
 */
@Component({
  selector: 'base-chat-definitions',
  standalone: true,
  imports: [SampleHostComponent],
  template: ` <pp-sample-host prefix="base-chat" groupName="chatDefinition" ariaLabel="Chat Definitions" [tabs]="tabs" /> `,
})
export class DefinitionsComponent {
  readonly tabs: SampleTab[] = [{ route: 'chat-definition', label: 'Chat Definition' }];
}
