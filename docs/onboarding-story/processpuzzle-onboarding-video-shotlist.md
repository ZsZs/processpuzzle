# ProcessPuzzle Demo Video — Shot List
### Detailed screen-capture plan for each spot in the script

Each spot below maps 1:1 to a "Screen capture spot" in `processpuzzle-onboarding-video-script.md`. For each: exact screens to open, the click/type sequence, and a target recording length (record a little longer than needed — trim in editing).

> Note: exact menu labels/paths are placeholders based on ProcessPuzzle's known feature areas (entity, rule, document, state, workflow, app builders). Adjust wording once you're recording against the live screens.

**The story's model in one view** — two entities, three rules, one document, one state machine, one workflow:

| Artifact | What it is |
| --- | --- |
| `Department` entity | The organizational unit a new hire joins; `Employee.department` references it |
| `Employee` entity | The person being onboarded; carries the state attribute |
| 3 rules (context `Employee`) | Start date ahead, company email, contract signed before activation |
| Employment Contract document | Shows the Employee's data through widget bindings |
| `Employee` state machine | Invited → Documents Pending → Active; the last transition is guarded by a rule |
| "Onboarding" workflow | HR Coordinator + New Hire; the onboarding to-dos are its **tasks**, not entity records |

The onboarding to-dos (set up workstation, safety training…) are deliberately **workflow tasks**, not an
entity: the task dashboard already claims, completes and skips them, and timers on the tasks give them due
dates and overdue escalation.

---

## Spot #1 — Model the Data
**Script scene:** Scene 2 (0:20–0:50)
**Target raw length:** ~35–40s (cut down to ~25s in edit)

1. Open the **Entity Designer** (empty/new state).
2. Click "New Entity" → name it `Department`.
3. Add fields:
   - `name` (TEXT, required)
   - `costCenter` (TEXT)
4. Save `Department`.
5. Click "New Entity" again → name it `Employee`.
6. Add fields one at a time, pausing briefly on each so it's readable on screen:
   - `name` (TEXT, required)
   - `email` (TEXT, required)
   - `startDate` (DATE, required)
   - `department` (REFERENCE → `Department`, lookup control) — the beat that shows entities relate to each other
   - `manager` (REFERENCE → `Employee`, lookup control)
   - `contractSigned` (BOOLEAN, checkbox) — set by the New Hire in Spot #6, checked by Rule 3
   - `state` (ENUM: `INVITED`, `DOCUMENTS_PENDING`, `ACTIVE`; disabled on the form) — the attribute the state machine of Spot #4 owns
7. Save `Employee`.
8. *(Off camera, before Spot #6)* Create two or three departments (e.g. "Engineering", "Finance") so the lookup has something to offer.

**Capture tip:** Slow, deliberate mouse movement — this is the first real screen the viewer sees, so it should read as "effortless," not rushed. If the cut is tight, keep `Department` to a quick 3–4s and spend the time on `Employee`.

---

## Spot #2 — Add the Business Rules
**Script scene:** Scene 3 (0:50–1:15)
**Target raw length:** ~30s (cut to ~20–25s)

A rule is a boolean PPCL expression (JavaScript) evaluated against `entity`, with a severity. ERROR blocks
the save; WARNING and INFO only give feedback on the form. All three rules use context `Employee`.

1. Open the **Rule Designer**.
2. Rule 1 — "Start date ahead", severity ERROR:
   `new Date(entity.startDate) >= new Date(new Date().toDateString())`
3. Rule 2 — "Company email", severity WARNING:
   `entity.email.endsWith('@acme.example')` *(use the sample company's domain)*
4. Rule 3 — "Contract signed before activation", severity ERROR:
   `entity.state !== 'DOCUMENTS_PENDING' || entity.contractSigned === true`
   — enforced at the `activate` transition in Spot #4. It reads the *current* state (the guard sees the
   object before it moves), which is why it names `DOCUMENTS_PENDING` rather than `ACTIVE`.
5. Save each rule; let the rule list populate on screen so the viewer sees three rules stack up.

**Try by hand first:**
- Open an Employee form and enter a past start date, then a non-company email. You should get an error and a warning.
- Side effect of Rule 3: while an employee is `DOCUMENTS_PENDING`, the form refuses to save them with
  `contractSigned` unchecked. That is acceptable for the story, since the New Hire's form edit is exactly what checks it.

**Capture tip:** Show the rules list at the end rather than the expression text for long. Hold the name and severity columns on screen; viewers won't read the expressions.

---

## Spot #3 — Attach the Document
**Script scene:** Scene 4 (1:15–1:35)
**Target raw length:** ~20s (cut to ~12–15s)

1. Open the **Document Designer**.
2. Create the "Employment Contract" document (or open it, if prepared off camera): a few text blocks of contract prose.
3. Add a widget block that displays the Employee's data — name, start date, department — through the block's **input bindings** to the Employee.
4. Show the bindings panel briefly: that is the visual proof the document is data-driven, not a static file.
5. Save.

**Try by hand first:** base-document has no `{{placeholder}}` merge fields and no e-signature. "Signing" in
Spot #6 means the New Hire completes the review task and ticks `contractSigned`. Check which widget renders
the Employee's attributes best before you record.

---

## Spot #4 — Define the States
**Script scene:** Scene 5 (1:35–1:55)
**Target raw length:** ~25–30s (cut to ~18–20s)

1. Open the **State Machine Designer**, new state machine for `Employee`; state attribute `state`, initial state `INVITED`.
2. Add states in order: `INVITED` → `DOCUMENTS_PENDING` → `ACTIVE` (mark `ACTIVE` final).
3. Connect transitions with triggers:
   - `INVITED` → `DOCUMENTS_PENDING`, trigger `sendContract`
   - `DOCUMENTS_PENDING` → `ACTIVE`, trigger `activate`, guard `ruleGuard` with param `context: Employee`
4. Save.

**Try by hand first:**
- Fire `activate` on an employee whose `contractSigned` is unchecked. It should be rejected, naming Rule 3.
- Tick the box and fire `activate` again. It should go through.
- The guard evaluates *every* enabled `Employee` rule, so the employee must also satisfy Rules 1–2. Rule 2 is a WARNING, so it doesn't block.

**Capture tip:** This is the most visual/diagram-like spot. It's a good candidate for a slightly longer, satisfying "watch it connect" moment if the tool animates the connector draw. Linger a beat on the guard of the `activate` transition: that is where Spot #2's rule comes back.

---

## Spot #5 — Bring It Together in a Workflow
**Script scene:** Scene 6 (1:55–2:25)
**Target raw length:** ~35–40s (cut to ~25–30s)

1. Open the **Workflow Designer**, new workflow named "Onboarding."
2. Add Role 1: `HR Coordinator`. Add Role 2: `New Hire`.
3. Add artifacts: the `Employee` entity and the Employment Contract document.
4. Add tasks, assigning each to a role and wiring it to an artifact from earlier spots:
   - `HR Coordinator`: "Create Employee record" (output: `Employee`)
   - `HR Coordinator`: "Send contract" (input: `Employee`, output: Employment Contract; fires `sendContract` → `DOCUMENTS_PENDING`)
   - `New Hire`: "Review & sign contract" (input: Employment Contract; ticks `contractSigned`)
   - `New Hire`: "Set up workstation"
   - `New Hire`: "Complete safety training". Add a **timer boundary event** (e.g. 5 days) that routes to an `HR Coordinator` task, "Follow up overdue training". This is the due date and the overdue escalation, with no extra entity.
   - `HR Coordinator`: "Activate employee" (depends on all of the above; fires `activate` → the Rule 3 guard)
5. Save the workflow and show the full diagram zoomed out — this is the "everything connects" beat.

**Try by hand first:** check which task kinds in the current build can fire a state transition directly. If
none can, the HR Coordinator fires `sendContract` / `activate` from the Employee screen during the task, and the
task is then completed.

**Capture tip:** End on a wide/zoomed-out shot of the whole workflow diagram — this is the visual payoff of Scene 6's line "every artifact you just built, working together."

---

## Spot #6 — Compose the Application
**Script scene:** Scene 7 (2:25–2:50)
**Target raw length:** ~40–45s (cut to ~25–30s)

1. Open the **App Composer**.
2. Create a new app "Onboarding," and add the built artifacts: the `Employee` and `Department` entities, the 3 rules, the contract document, the `Employee` state machine, and the "Onboarding" workflow.
3. Click compose/build/deploy (whatever the actual action is called).
4. Cut to the **running app**:
   - HR Coordinator view: create a new Employee record. Pick "Engineering" from the department lookup and leave the start date in the future.
   - New Hire view: open the task dashboard, claim "Review & sign contract", read the contract, tick `contractSigned`, complete the task. Then complete "Set up workstation".
   - HR Coordinator view: "Activate employee" → state shows `ACTIVE`.
5. End on a clean shot of the task dashboard showing the completed tasks.

**Capture tip:** This is the emotional payoff of the whole video — give it the most polish. If possible, use realistic-looking sample data (a real name, a real-sounding department) rather than "Test Test."

---

## Recording checklist (all spots)
- [ ] Do one full dry run by hand (Spots #1 → #6) before recording anything; note the real menu labels and fix this list
- [ ] Use a consistent browser window size/zoom across all spots (for clean, uniform framing in the edit)
- [ ] Hide any dev/debug UI, notifications, or personal data before recording
- [ ] Record each spot as its own clip (don't record scenes back-to-back) — makes trimming/re-takes easier
- [ ] Leave 2–3 extra seconds of static screen at the start/end of each clip for clean cut points
- [ ] Use sample data consistent across all spots (same employee name, same department, same task titles) so the story reads as one continuous example
- [ ] Departments exist before Spot #6; the new hire's start date is in the future (Rule 1) and the email is on the company domain (Rule 2)
