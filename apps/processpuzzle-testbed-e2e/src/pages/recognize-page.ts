import { expect, Locator, Page } from '@playwright/test';

/**
 * The race sample's checkpoint screen, `/base-ai/samples/recognize`: a context picker (race, round,
 * checkpoint), base-ai's camera widget, and the race's entry list with what was seen at the checkpoint.
 */
export class RecognizePage {
  readonly camera: Locator;
  readonly shoot: Locator;
  readonly pickFrames: Locator;
  readonly framePicker: Locator;
  readonly cancel: Locator;
  readonly answer: Locator;
  readonly hit: Locator;
  readonly choices: Locator;
  readonly entries: Locator;

  constructor(readonly page: Page) {
    this.camera = page.locator('pp-recognition-camera');
    this.shoot = page.getByTestId('shoot');
    this.pickFrames = page.getByTestId('pick-frames');
    this.framePicker = page.getByTestId('frame-picker');
    this.cancel = page.getByTestId('cancel');
    this.answer = page.getByTestId('recognition-answer');
    this.hit = page.getByTestId('recognition-hit');
    this.choices = page.getByTestId('recognition-choice');
    this.entries = page.getByTestId('entry');
  }

  async goto(): Promise<void> {
    await this.page.goto('/base-ai/samples/recognize');
    await expect(this.camera).toBeVisible();
  }

  async selectRace(name: string): Promise<void> {
    await this.choose('race-select', name);
  }

  async selectRound(round: number): Promise<void> {
    await this.choose('round-select', String(round));
  }

  async selectCheckpoint(label: string): Promise<void> {
    await this.choose('checkpoint-select', label);
  }

  /** The entry list row of one boat, by the label the page gives it — sail number and name. */
  entry(label: string): Locator {
    return this.entries.filter({ hasText: label });
  }

  /** The hand-tick button of a boat not seen yet at the selected checkpoint; absent once it was seen. */
  tick(label: string): Locator {
    return this.entry(label).getByTestId('tick');
  }

  /**
   * Opened from the keyboard and its option looked up in the panel it owns, as base-entity's e2e control
   * tester does — see its DROPDOWN tester for why a click on a mat-select is not reliable.
   */
  private async choose(testId: string, option: string): Promise<void> {
    const select = this.page.getByTestId(testId);
    await select.focus();
    await select.press('Enter');
    await expect(select).toHaveAttribute('aria-expanded', 'true');
    const panelId = await select.getAttribute('aria-controls');
    const panel = panelId ? this.page.locator(`[id="${panelId}"]`) : this.page.locator('.mat-mdc-select-panel');
    await panel.locator('mat-option').filter({ hasText: new RegExp(`^\\s*${escape(option)}\\s*$`) }).first().click();
    await expect(select).toHaveAttribute('aria-expanded', 'false');
    await expect(select).toContainText(option);
  }
}

function escape(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}
