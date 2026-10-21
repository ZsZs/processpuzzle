import { expect, Locator, Page, test } from '@playwright/test';
import { PlatformApi } from '../support/platform.api';
import { serviceRoots } from '../support/race-sample.api';
import { importOwnWorkflow, OwnWorkflow, removeOwnWorkflow } from '../support/workflow-fixtures';

const MODELER = '/base-workflow/samples/workflow/order-fulfillment-workflow/modeler';

/** The diagram is drawn once the workflow and its catalogs have loaded: generous, because a loaded stack takes a while. */
const DIAGRAM_TIMEOUT = 20_000;

/**
 * The read-only diagram of the seeded order-fulfillment workflow. There is nothing to drag: the canvas is drawn
 * from the definition, authoring happens in the forms (see workflow-authoring.spec.ts), so these specs are about
 * the drawing being right and the panel and layer controls around it.
 */
test.describe('Workflow modeler', () => {
  const node = (page: Page, kind: string, text?: string) => {
    const nodes = page.getByTestId(`workflow-node-${kind}`);
    return text ? nodes.filter({ hasText: text }) : nodes;
  };

  /** Opens the seeded workflow's modeler. Not a `beforeEach`: the layout specs below run on workflows of their own. */
  const openSeeded = async (page: Page) => {
    await page.goto(MODELER);
    await expect(page.getByTestId('workflow-modeler-diagram')).toBeVisible({ timeout: DIAGRAM_TIMEOUT });
    await expect(page.getByTestId('workflow-node-task').first()).toBeVisible({ timeout: DIAGRAM_TIMEOUT });
  };

  test('draws the workflow: lanes per role, its tasks, events, start and ends', async ({ page }) => {
    await openSeeded(page);
    await expect(page.getByTestId('workflow-lane')).toHaveText([/Order Clerk/, /Order Manager/]);
    await expect(node(page, 'task')).toHaveCount(3);
    for (const task of ['Review Order', 'Approve Shipment', 'Confirm Delivery']) await expect(node(page, 'task', task)).toHaveCount(1);
    await expect(node(page, 'start')).toContainText('OrderCreatedEvent');
    // The throw and the catch of the invoice exchange, and the boundary events on the tasks.
    await expect(node(page, 'event', 'Invoice requested')).toHaveCount(1);
    await expect(node(page, 'event', 'Invoice issued')).toHaveCount(1);
    await expect(node(page, 'event')).toHaveCount(5);
    await expect(node(page, 'end', 'Order cancelled')).toHaveCount(1);
  });

  test('selecting an element shows its properties, and nothing selected says so', async ({ page }) => {
    await openSeeded(page);
    await expect(page.getByTestId('workflow-modeler-no-selection')).toBeVisible();

    await node(page, 'task', 'Confirm Delivery').click();

    await expect(page.getByTestId('workflow-modeler-no-selection')).toBeHidden();
    await expect(page.getByTestId('element-name')).toHaveText('Confirm Delivery');
    await expect(page.getByTestId('element-id')).toHaveText('confirm-delivery');
    await expect(page.getByTestId('element-kind')).toContainText(/task/i);
    await expect(page.getByTestId('element-steps')).toContainText('Generate Invoice');
  });

  test('an event shows its own properties', async ({ page }) => {
    await openSeeded(page);
    await node(page, 'event', 'Invoice requested').click();

    await expect(page.getByTestId('element-name')).toHaveText('Invoice requested');
    await expect(page.getByTestId('element-kind')).toContainText(/event/i);
  });

  test('the layer toggles hide and show lanes, data and tools', async ({ page }) => {
    await openSeeded(page);
    const layers: [string, () => ReturnType<typeof node>, number][] = [
      ['data', () => node(page, 'artifact'), 5],
      ['tools', () => node(page, 'tool'), 1],
    ];
    for (const [layer, nodes, count] of layers) {
      await expect(nodes()).toHaveCount(count);
      await page.getByTestId(`modeler-toggle-${layer}`).locator('input').uncheck();
      await expect(nodes()).toHaveCount(0);
      await page.getByTestId(`modeler-toggle-${layer}`).locator('input').check();
      await expect(nodes()).toHaveCount(count);
    }

    await page.getByTestId('modeler-toggle-lanes').locator('input').uncheck();
    await expect(page.getByTestId('workflow-lane')).toHaveCount(0);
    await page.getByTestId('modeler-toggle-lanes').locator('input').check();
    await expect(page.getByTestId('workflow-lane')).toHaveCount(2);
  });

  test.describe('saved layout', () => {
    // The arrangement is saved per workflow and cannot be deleted through the API, so this runs on a workflow of its
    // own: the seeded one keeps its automatic layout for the specs above.
    let platform: PlatformApi;
    let own: OwnWorkflow;

    test.beforeEach(async ({ request, baseURL }) => {
      platform = new PlatformApi(request, await serviceRoots(request, baseURL as string));
      own = await importOwnWorkflow(platform);
    });

    test.afterEach(async () => {
      await removeOwnWorkflow(platform, own);
    });

    const centre = async (locator: Locator) => {
      const box = await locator.boundingBox();
      if (!box) throw new Error('element is not on screen');
      return { x: box.x + box.width / 2, y: box.y + box.height / 2 };
    };

    /**
     * Where the second task sits relative to the first, in task widths. Pixels would not do: the canvas fits itself
     * to the view on every load and does not always pick the same zoom (the same layout measured 192 px and 240 px
     * apart), whereas a distance in units of the task's own width does not depend on the zoom or the viewport.
     */
    const offset = async (page: Page) => {
      const firstBox = await node(page, 'task', 'First').first().boundingBox();
      if (!firstBox) throw new Error('the first task is not on screen');
      const first = await centre(node(page, 'task', 'First').first());
      const second = await centre(node(page, 'task', 'Second').first());
      return { dx: (second.x - first.x) / firstBox.width, dy: (second.y - first.y) / firstBox.width };
    };

    const near = (a: { dx: number; dy: number }, b: { dx: number; dy: number }, tolerance: number) => Math.abs(a.dx - b.dx) <= tolerance && Math.abs(a.dy - b.dy) <= tolerance;
    /** The drag below is about 0.7 x 1.1 task widths; anything past this tolerance is a different arrangement. */
    const DIFFERENT = 0.3;
    const SAME = 0.06;

    /** The offset once it has stopped changing: the canvas is still fitting itself to the view just after load. */
    const settledOffset = async (page: Page) => {
      let last = await offset(page);
      for (let attempt = 0; attempt < 10; attempt++) {
        await page.waitForTimeout(300);
        const now = await offset(page);
        if (near(now, last, 0.02)) return now;
        last = now;
      }
      return last;
    };

    const openModeler = async (page: Page) => {
      await page.goto(`/base-workflow/samples/workflow/${own.workflow}/modeler`);
      await expect(node(page, 'task')).toHaveCount(2, { timeout: DIAGRAM_TIMEOUT });
    };

    /** Drags the second task down and to the right of where the automatic layout puts it. */
    const moveSecondTask = async (page: Page) => {
      // The canvas can start below the fold: measure only after the page has scrolled, or the mouse misses the node.
      await node(page, 'task', 'Second').first().scrollIntoViewIfNeeded();
      const from = await centre(node(page, 'task', 'Second').first());
      await page.mouse.move(from.x, from.y);
      await page.mouse.down();
      await page.mouse.move(from.x + 90, from.y + 140, { steps: 12 });
      await page.mouse.up();
    };

    test('a moved task stays where it was put after saving and reloading', async ({ page }) => {
      await openModeler(page);
      expect(await platform.workflowLayout(own.workflow), 'never arranged yet').toBeUndefined();
      const automatic = await settledOffset(page);

      await moveSecondTask(page);
      const moved = await settledOffset(page);
      expect(near(moved, automatic, DIFFERENT), `the drag moved the task: ${JSON.stringify(automatic)} -> ${JSON.stringify(moved)}`).toBe(false);

      await page.getByTestId('workflow-modeler-save').click();
      await expect.poll(() => platform.workflowLayout(own.workflow), { message: 'the layout is stored' }).toBeDefined();

      await page.reload();
      await expect(node(page, 'task')).toHaveCount(2, { timeout: DIAGRAM_TIMEOUT });
      const reloaded = await settledOffset(page);
      expect(near(reloaded, moved, SAME), `after reload ${JSON.stringify(reloaded)}, saved ${JSON.stringify(moved)}`).toBe(true);
      expect(near(reloaded, automatic, DIFFERENT), 'not the automatic layout again').toBe(false);
    });

    test('a layout that was not saved is not kept', async ({ page }) => {
      await openModeler(page);
      const automatic = await settledOffset(page);
      await moveSecondTask(page);
      const moved = await settledOffset(page);
      expect(near(moved, automatic, DIFFERENT), 'the drag moved the task').toBe(false);

      await page.reload();
      await expect(node(page, 'task')).toHaveCount(2, { timeout: DIAGRAM_TIMEOUT });

      const reloaded = await settledOffset(page);
      expect(near(reloaded, automatic, SAME), `after reload ${JSON.stringify(reloaded)}, automatic ${JSON.stringify(automatic)}`).toBe(true);
      expect(await platform.workflowLayout(own.workflow)).toBeUndefined();
    });
  });
});
