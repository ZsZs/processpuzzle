# ProcessPuzzle Demo Video — Shot List
### Detailed screen-capture plan for each spot in the script

Each spot below maps 1:1 to a "Screen capture spot" in `processpuzzle-onboarding-video-script.md`. For each: exact screens to open, the click/type sequence, and a target recording length (record a little longer than needed — trim in editing).

> Note: exact menu labels/paths are placeholders based on ProcessPuzzle's known feature areas (entity, rule, document, state, workflow, app builders). Adjust wording once you're recording against the live screens.

---

## Spot #1 — Model the Data
**Script scene:** Scene 2 (0:20–0:50)
**Target raw length:** ~35–40s (cut down to ~25s in edit)

1. Open the **Entity Designer** (empty/new state).
2. Click "New Entity" → name it `Employee`.
3. Add fields one at a time, pausing briefly on each so it's readable on screen:
   - `name` (text)
   - `email` (text)
   - `startDate` (date)
   - `department` (text or lookup)
   - `manager` (reference/lookup to another Employee)
4. Save `Employee`.
5. Click "New Entity" again → name it `OnboardingTask`.
6. Add fields:
   - `title` (text)
   - `description` (text)
   - `dueDate` (date)
   - `status` (enum/lookup — ties into Spot #4's state machine)
7. Save `OnboardingTask`.

**Capture tip:** Slow, deliberate mouse movement — this is the first real screen the viewer sees, so it should read as "effortless," not rushed.

---

## Spot #2 — Add the Business Rules
**Script scene:** Scene 3 (0:50–1:15)
**Target raw length:** ~30s (cut to ~20–25s)

1. Open the **Rule Designer**, filtered/scoped to the `OnboardingTask` / `Employee` entities.
2. Rule 1 — "Auto due date": show the condition/action pair — e.g. `on OnboardingTask create → set dueDate = Employee.startDate - 3 days` (adjust offset per task type if the UI supports templated rules; otherwise pick one representative task).
3. Rule 2 — "Block activation": `Employee.state → Active` guarded by `all mandatory OnboardingTasks.status = Done`.
4. Rule 3 — "Overdue notification": `on OnboardingTask.dueDate passed AND status != Done → notify Employee.manager`.
5. Save each rule; let the rule list populate on screen so the viewer sees three rules stack up.

**Capture tip:** If the rule builder is visual (drag/drop or condition-builder UI), favor that over raw text/DSL — more legible at a glance in a short cut.

---

## Spot #3 — Attach the Document
**Script scene:** Scene 4 (1:15–1:35)
**Target raw length:** ~20s (cut to ~12–15s)

1. Open the **Document Designer** / document template manager.
2. Upload or select the "Employment Contract" template.
3. Link it to the `Employee` entity (so a contract instance is generated per employee).
4. Show the placeholder-merge fields if visible (e.g. `{{Employee.name}}`, `{{Employee.startDate}}`) — nice visual proof it's data-driven, not a static file.
5. Save.

---

## Spot #4 — Define the States
**Script scene:** Scene 5 (1:35–1:55)
**Target raw length:** ~25–30s (cut to ~18–20s)

1. Open the **State Machine Designer**, new state machine for `Employee`.
2. Draw/add states in order: `Invited` → `Documents Pending` → `Active`.
3. Connect transitions between them (click-drag or the tool's connector action).
4. Briefly switch to (or split-screen) the second, simpler state machine for `OnboardingTask`: `Open` → `In Progress` → `Done`.
5. Save both.

**Capture tip:** This is the most visual/diagram-like spot — good candidate for a slightly longer, satisfying "watch it connect" moment if the tool animates the connector draw.

---

## Spot #5 — Bring It Together in a Workflow
**Script scene:** Scene 6 (1:55–2:25)
**Target raw length:** ~35–40s (cut to ~25–30s)

1. Open the **Workflow Designer**, new workflow named "Onboarding."
2. Add Role 1: `HR Coordinator`. Add Role 2: `New Hire`.
3. Add workflow steps, assigning each to a role and wiring it to an artifact from earlier spots:
   - `HR Coordinator`: "Create Employee record" (→ `Employee` entity)
   - `HR Coordinator`: "Generate contract" (→ Document from Spot #3)
   - `New Hire`: "Review & sign contract" (→ Document + `Employee` state transition to `Documents Pending`→ readiness for `Active`)
   - `New Hire`: "Complete onboarding tasks" (→ `OnboardingTask` entity + its state machine)
   - `HR Coordinator`: "Monitor overdue tasks" (→ Rule 3 notification)
4. Save the workflow and show the full diagram zoomed out — this is the "everything connects" beat.

**Capture tip:** End on a wide/zoomed-out shot of the whole workflow diagram — this is the visual payoff of Scene 6's line "every artifact you just built, working together."

---

## Spot #6 — Compose the Application
**Script scene:** Scene 7 (2:25–2:50)
**Target raw length:** ~40–45s (cut to ~25–30s)

1. Open the **App Composer**.
2. Create a new app "Onboarding," and add the built artifacts: `Employee` + `OnboardingTask` entities, the 3 rules, the contract document, both state machines, and the "Onboarding" workflow.
3. Click compose/build/deploy (whatever the actual action is called).
4. Cut to the **running app**:
   - HR Coordinator view: create a new Employee record.
   - New Hire view: sign the contract, check off a task.
5. End on a clean shot of the app's task list/dashboard showing progress.

**Capture tip:** This is the emotional payoff of the whole video — give it the most polish. If possible, use realistic-looking sample data (a real name, a real-sounding department) rather than "Test Test."

---

## Recording checklist (all spots)
- [ ] Use a consistent browser window size/zoom across all spots (for clean, uniform framing in the edit)
- [ ] Hide any dev/debug UI, notifications, or personal data before recording
- [ ] Record each spot as its own clip (don't record scenes back-to-back) — makes trimming/re-takes easier
- [ ] Leave 2–3 extra seconds of static screen at the start/end of each clip for clean cut points
- [ ] Use sample data consistent across all spots (same employee name, same task titles) so the story reads as one continuous example
