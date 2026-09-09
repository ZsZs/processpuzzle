import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { MarkdownComponent } from 'ngx-markdown';

/**
 * Sample page for an entity that is *only* metadata: `Dynamic Entity`, defined in `base-entity-backend`'s
 * seed data and never mentioned in this application beyond its route.
 *
 * Two things differ from its sibling samples, both for the same reason.
 *
 * It renders a `<router-outlet>` rather than a `<base-entity-container [entityDescriptor]>`: a dynamic
 * entity's descriptor does not exist when this component is constructed — it is synthesized from what the
 * backend answers — so its screens are contributed as *routes*, by `dynamicEntityScreenRoutes`, and this
 * component is only the page around them.
 *
 * And the hints come from an asset rather than an inline template, because they quote the recipe verbatim
 * and an Angular template cannot hold a `{` without escaping it — which would leave the sample's central
 * code block unreadable in the one place a reader is most likely to look.
 */
@Component({
  selector: 'dynamic-entity',
  standalone: true,
  imports: [MarkdownComponent, RouterOutlet],
  template: `
    <markdown clipboard [src]="hintsSource"></markdown>
    <router-outlet></router-outlet>
  `,
  styles: ``,
})
export class DynamicEntityContainerComponent {
  readonly hintsSource = 'assets/base-forms/dynamic-entity-hints.md';
}
