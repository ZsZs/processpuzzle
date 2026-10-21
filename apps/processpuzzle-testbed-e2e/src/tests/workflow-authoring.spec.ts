import { expect, Page, test } from '@playwright/test';
import { chooseOption, fillText, pickRelated, setChecked } from '../support/embedded-form';
import { PlatformApi } from '../support/platform.api';
import { serviceRoots } from '../support/race-sample.api';
import { importOwnWorkflow, OwnWorkflow, removeOwnWorkflow } from '../support/workflow-fixtures';

const WORKFLOWS = '/base-workflow/samples/workflow';

/**
 * Start events and events are authored on embedded forms under a workflow, which the generated CRUD suites do not
 * reach (Workflow is excluded there). Each test adds one through the form and reads the workflow back from the
 * backend: what was *stored* is the assertion, not what the form shows.
 */
test.describe('Workflow authoring', () => {
  let platform: PlatformApi;
  let own: OwnWorkflow;

  test.beforeEach(async ({ request, baseURL }) => {
    platform = new PlatformApi(request, await serviceRoots(request, baseURL as string));
    own = await importOwnWorkflow(platform);
  });

  test.afterEach(async () => {
    await removeOwnWorkflow(platform, own);
  });

  /** An embedded form saves into the workflow it belongs to, which is only stored once the workflow's own form is. */
  const saveWorkflow = async (page: Page) => {
    await expect(page.getByTestId('workflow-save')).toBeEnabled();
    await page.getByTestId('workflow-save').click();
  };

  const newStartEvent = (workflowId: string) => `${WORKFLOWS}/${workflowId}/details/workflow-start-event/new/details`;
  const newEvent = (workflowId: string) => `${WORKFLOWS}/${workflowId}/details/workflow-event-use/new/details`;
  const storedStartEvent = async (id: string) => (await platform.workflow(own.workflow)).startEvents?.find((event) => event.id === id);
  const storedEvent = async (id: string) => (await platform.workflow(own.workflow)).events?.find((event) => event.id === id);

  test('a time-based start event keeps its timer', async ({ page }) => {
    await page.goto(newStartEvent(own.workflow));
    await fillText(page, 'workflowStartEvent-id', 'nightly');
    await fillText(page, 'workflowStartEvent-name', 'Every hour');
    await chooseOption(page, 'workflowStartEvent-startType', 'TIME_BASED_PRECONDITION');
    await chooseOption(page, 'workflowStartEvent-timerType', 'CYCLE');
    await fillText(page, 'workflowStartEvent-timerExpression', 'R/PT1H');
    await page.getByTestId('workflowStartEvent-save').click();
    await saveWorkflow(page);

    await expect.poll(() => storedStartEvent('nightly')).toMatchObject({
      name: 'Every hour',
      startType: 'TIME_BASED_PRECONDITION',
      timer: { type: 'CYCLE', expression: 'R/PT1H' },
    });
  });

  test('an interrupting timer on a task, which the modeler then draws on that task', async ({ page }) => {
    await page.goto(newEvent(own.workflow));
    await fillText(page, 'workflowEventUse-id', 'escalate');
    await fillText(page, 'workflowEventUse-name', 'Escalate');
    await chooseOption(page, 'workflowEventUse-direction', 'CATCH');
    await chooseOption(page, 'workflowEventUse-timerType', 'DURATION');
    await fillText(page, 'workflowEventUse-timerExpression', 'PT1H');
    await fillText(page, 'workflowEventUse-attachedTo', own.first);
    await setChecked(page, 'workflowEventUse-interrupting', true);
    await page.getByTestId('workflowEventUse-save').click();
    await saveWorkflow(page);

    await expect.poll(() => storedEvent('escalate')).toMatchObject({
      direction: 'CATCH',
      attachedTo: own.first,
      interrupting: true,
      timer: { type: 'DURATION', expression: 'PT1H' },
    });

    await page.goto(`${WORKFLOWS}/${own.workflow}/modeler`);
    // A boundary event sits on its task and carries no label of its own: it is the only event the workflow has.
    await expect(page.getByTestId('workflow-node-event')).toHaveCount(1, { timeout: 20_000 });
    await expect(page.getByTestId('workflow-node-task')).toHaveCount(2);
  });

  // Choosing a catalogued event is a round trip through the event catalog's list. The form being left keeps its
  // values and gets them back, and the workflow's own form, which the start event's sits inside, is left alone.
  const valueOf = (page: Page, controlId: string) => page.getByTestId(controlId).locator('input').first();

  test('a start event triggered by a catalogued event, chosen last, and chosen again for the saved one', async ({ page }) => {
    await page.goto(`${WORKFLOWS}/${own.workflow}/details`);
    await page.getByTestId('workflow-startEvents').locator('fieldset.base-entity-form-field').first().focus();
    await page.getByRole('button', { name: 'Add Workflow Start Event' }).click();
    await fillText(page, 'workflowStartEvent-id', 'order-placed');
    await fillText(page, 'workflowStartEvent-name', 'Order placed');
    await chooseOption(page, 'workflowStartEvent-startType', 'TRIGGERING_EVENT');

    await pickRelated(page, 'workflowStartEvent-eventType', 'Event Definition', 'OrderCreatedEvent');

    await expect(valueOf(page, 'workflowStartEvent-id')).toHaveValue('order-placed');
    await expect(valueOf(page, 'workflowStartEvent-name')).toHaveValue('Order placed');
    await expect(valueOf(page, 'workflow-id')).toHaveValue(own.workflow);
    await page.getByTestId('workflowStartEvent-save').click();
    await saveWorkflow(page);
    await expect.poll(() => storedStartEvent('order-placed')).toMatchObject({ startType: 'TRIGGERING_EVENT', eventType: 'OrderCreatedEvent' });

    // The saved start event, with another event chosen for it.
    await page.goto(`${WORKFLOWS}/${own.workflow}/details`);
    await page.getByTestId('workflow-startEvents').getByRole('link', { name: 'order-placed' }).click();
    await pickRelated(page, 'workflowStartEvent-eventType', 'Event Definition', 'OrderConfirmedEvent');

    await expect(valueOf(page, 'workflowStartEvent-id')).toHaveValue('order-placed');
    await expect(valueOf(page, 'workflow-id'), 'the workflow keeps its own id, not the one of the start event').toHaveValue(own.workflow);
    await page.getByTestId('workflowStartEvent-save').click();
    await saveWorkflow(page);
    await expect.poll(() => storedStartEvent('order-placed')).toMatchObject({ eventType: 'OrderConfirmedEvent' });
  });

  test('an event that throws a catalogued message', async ({ page }) => {
    await page.goto(`${WORKFLOWS}/${own.workflow}/details`);
    await page.getByTestId('workflow-events').locator('fieldset.base-entity-form-field').first().focus();
    await page.getByRole('button', { name: 'Add Workflow Event Use' }).click();
    await fillText(page, 'workflowEventUse-id', 'request-invoice');
    await fillText(page, 'workflowEventUse-name', 'Request the invoice');
    await chooseOption(page, 'workflowEventUse-direction', 'THROW');
    // A message is matched to the run waiting for it by what both carry: the backend refuses one with no key.
    await fillText(page, 'workflowEventUse-correlationKey', 'orderId');

    await pickRelated(page, 'workflowEventUse-eventDefinitionId', 'Event Definition', 'InvoiceRequested');

    await expect(valueOf(page, 'workflowEventUse-id')).toHaveValue('request-invoice');
    await expect(valueOf(page, 'workflowEventUse-correlationKey')).toHaveValue('orderId');
    await expect(valueOf(page, 'workflow-id')).toHaveValue(own.workflow);
    await page.getByTestId('workflowEventUse-save').click();
    await saveWorkflow(page);

    await expect.poll(() => storedEvent('request-invoice')).toMatchObject({ direction: 'THROW', eventDefinitionId: 'InvoiceRequested' });
  });
});
