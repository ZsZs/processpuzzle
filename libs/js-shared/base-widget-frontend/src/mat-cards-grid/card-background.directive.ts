import { Directive, input } from '@angular/core';
import { moduleAssetUrl } from '../module-asset/module-asset-context';

/**
 * Draws a card's `backgroundImage` behind it: an ordinary URL as is, a `module:` asset fetched with the
 * caller's credentials. Nothing is drawn until the asset has arrived.
 */
@Directive({
  selector: '[ppCardBackground]',
  standalone: true,
  host: { '[style.background-image]': 'cssImage()' },
})
export class CardBackgroundDirective {
  readonly ppCardBackground = input<string | undefined>();
  private readonly url = moduleAssetUrl(() => this.ppCardBackground());

  cssImage(): string | null {
    const url = this.url();
    return url ? `url("${url.replace(/"/g, '%22')}")` : null;
  }
}
