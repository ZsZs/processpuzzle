# ProcessPuzzle Demo Video — Script
### Story: "Onboarding" — From Idea to Working App
**Target length:** ~3 minutes
**Format:** Narrator voiceover + small animated "explainer" character (bottom-corner rectangle) over ProcessPuzzle screen captures

---

## SCENE 1 — The Idea (0:00–0:20)
**Narrator (VO):**
"Every company has to onboard new people. It sounds simple — but behind it there's data to track, rules to enforce, documents to sign, and people who need to know what to do next. What if you could go from that idea... to a working application, today?"

**Character:** Waves, appears in corner, gives a friendly nod toward the screen.

**Screen:** Clean title card — "ProcessPuzzle: Onboarding, from idea to app." No live screen yet.

---

## SCENE 2 — Model the Data (0:20–0:50)
**Narrator (VO):**
"We start with the data. In ProcessPuzzle, you define entities — the things your business cares about. Here, an Employee, and an Onboarding Task."

**Screen capture spot #1:** Entity designer — create `Employee` entity, add fields (name, email, start date, department, manager). Quick cut to creating `OnboardingTask` entity (title, description, due date, status).

**Character:** Points at the field list as it grows, gives a thumbs-up when the entity is saved.

**Narrator (VO):**
"No code. Just describe what you need, and ProcessPuzzle builds the data model for you."

---

## SCENE 3 — Add the Business Rules (0:50–1:15)
**Narrator (VO):**
"Now the logic. What happens automatically? ProcessPuzzle lets you express that as rules, right next to your data."

**Screen capture spot #2:** Rule designer — show three rules being defined:
1. Auto-set task due dates relative to the employee's start date
2. Block the employee from going "Active" until mandatory tasks are done
3. Notify the manager if a task is overdue

**Character:** Small lightbulb icon appears next to the character as each rule is added.

---

## SCENE 4 — Attach the Document (1:15–1:35)
**Narrator (VO):**
"Onboarding always needs paperwork. Attach a document template — here, the employment contract — and ProcessPuzzle handles generating and tracking it as part of the process."

**Screen capture spot #3:** Document designer — attach/upload the contract template, link it to the Employee entity.

**Character:** Mimes flipping through a small document icon.

---

## SCENE 5 — Define the States (1:35–1:55)
**Narrator (VO):**
"Every employee moves through stages — invited, pending paperwork, active. ProcessPuzzle lets you draw that directly as a state machine."

**Screen capture spot #4:** State machine designer — build `Employee` states: Invited → Documents Pending → Active. Quick glimpse of the simpler `OnboardingTask` states: Open → In Progress → Done.

**Character:** Traces the arrows between states with a finger.

---

## SCENE 6 — Bring It Together in a Workflow (1:55–2:25)
**Narrator (VO):**
"Now we connect everything into a workflow with real people in real roles. An HR Coordinator creates the record and manages the paperwork. The New Hire reviews the contract and completes their own tasks."

**Screen capture spot #5:** Workflow designer — show the two roles (HR Coordinator, New Hire), each step wired to the entities, rules, document, and state transitions defined earlier.

**Character:** Two small role icons appear beside the character, then join together with a connecting line.

**Narrator (VO):**
"One workflow. Two roles. Every artifact you just built, working together."

---

## SCENE 7 — Compose the Application (2:25–2:50)
**Narrator (VO):**
"Last step: compose it into an application. ProcessPuzzle assembles your entities, rules, documents, states, and workflow into a working app — ready to use."

**Screen capture spot #6:** App composer — assemble the Onboarding app from the pieces just built, then show the finished app running: HR Coordinator creating a new employee, New Hire signing the contract and checking off a task.

**Character:** Claps, gives a big thumbs-up.

---

## SCENE 8 — Close (2:50–3:00)
**Narrator (VO):**
"From idea to application — no code, just your business, modeled the way it actually works. That's ProcessPuzzle."

**Screen:** End card — logo, tagline, call to action (e.g. "Try it yourself" / sign-up link).

**Character:** Waves goodbye.

---

## Notes for Task 3 (Shot List)
Each "Screen capture spot" above (#1–#6) will be broken out into a detailed shot list: exact ProcessPuzzle screens, click sequence, and recommended recording length, so they can be captured independently and cut to the script timing.
