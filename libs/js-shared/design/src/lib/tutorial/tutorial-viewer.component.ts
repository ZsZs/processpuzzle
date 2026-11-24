import { Component, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { VIDEO_PLAYER_WIDGET, WidgetHostComponent, WidgetInstance } from '@processpuzzle/widgets';

/** The entity the designer's tutorials are published to: one row per tutorial and language. */
export const TUTORIAL_ENTITY = 'processpuzzle-tutorial';

/**
 * Mounts the video-player widget on the route a designer card's menu opens, for the tutorial named by the route. The
 * widget does the reading and the playing; this only says which rows it reads.
 */
@Component({
  selector: 'pp-tutorial-viewer',
  standalone: true,
  imports: [WidgetHostComponent],
  template: `<pp-widget-host [widget]="widget" />`,
})
export class TutorialViewerComponent {
  private readonly tutorialKey = inject(ActivatedRoute).snapshot.paramMap.get('key') ?? '';

  protected readonly widget = new WidgetInstance({
    id: 'tutorial',
    type: VIDEO_PLAYER_WIDGET,
    props: {
      entityName: TUTORIAL_ENTITY,
      query: `name==${this.tutorialKey}`,
      attribute: 'video',
      textAttribute: 'text',
    },
  });
}
