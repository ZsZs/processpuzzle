# ProcessPuzzle Tutorials: Goal, Strategy, Technique

## Goal

Give ProcessPuzzle users clear, always-current tutorials for the complex parts of the platform (starters, releases, migrations, the designers) in a short time. Tutorials must stay accurate as the UI changes, without you re-recording and rewriting them by hand.

## Strategy

- **Teach by task, not by feature.** Write 5-7 short tutorials phrased as outcomes ("Set up inventory in 10 minutes", "Change a field without losing data", "Publish and roll back safely"). Concept pages like EAV/JSONB stay in a separate "how it works" section.
- **Let the product teach itself.** Ship an in-app guided tour with each Business Starter, defined as metadata like the rest of the platform, so it stays in sync with the starter.
- **Automate production.** Make each tutorial a script that generates its own video, subtitles and narration, so an update is a re-run rather than a rewrite.
- **Use AI to draft, you to verify.** An LLM drafts the text from the starter YAML, the migration operation catalog and the scenario scripts. You check the steps and their order.
- **Add cheap safety nets.** Contextual help panels on the complex screens, and a "first week" checklist on the dashboard that links to the tutorials in order.
- **Sequence by traffic.** Start with the highest-traffic flows (starter import first), because the pipeline built for the first one is reused for the rest.

## Technique

### Guided tours

- A YAML tour definition per starter, with steps that have a route, a `data-tour` target, i18n keys for text, and an `advanceOn` rule.
- `advanceOn: event` makes users perform the real action instead of clicking Next.
- Tours link into the dashboard checklist and chain into the next tour.

```yaml
tour:
  id: inventory-first-steps
  version: 1
  starter: inventory
  audience: [ADMIN, DESIGNER]
  startWhen: { event: starter.imported, params: { starter: inventory } }
  steps:
    - id: create-record
      route: /apps/inventory/products
      target: "[data-tour='add-record-button']"
      titleKey: tours.inventory.create.title
      bodyKey: tours.inventory.create.body
      advanceOn: { type: event, name: entity.created, entity: product }
  completion:
    markChecklistItem: first-week.import-starter
    nextTour: inventory-change-a-field
```

### Video pipeline

- One scenario file per designer (`<name>.tutorial.ts`) holds an ordered list of steps, each with a narration sentence and an optional Playwright action.
- The recorder runs the actions in a recorded browser context, with login done in a separate unrecorded one.
- Each step lasts as long as the longer of its action and its narration, estimated at about 145 words per minute (`wordsPerSecond` is configurable).
- A visible orange cursor is injected, since Playwright videos don't show one.
- One run outputs:
  - `<name>.webm`: the video
  - `<name>.srt`: subtitles
  - `<name>.script.md`: timestamped script (time, step, narration)
  - `<name>.narration.txt`: plain text for voice-over or text-to-speech
- The same `data-tour` selectors serve the in-app tours, the E2E tests and the videos, so a UI change breaks a test instead of silently breaking a tutorial.

```bash
BASE_URL=https://staging... TUTORIAL_USER=... TUTORIAL_PASSWORD=... \
  npx tsx record-all.ts [scenario-name] [--no-video]
```

### Workflow per tutorial

1. Add `data-tour` attributes on the screens in the flow.
2. Write the scenario with narration.
3. Run the recorder, then add the voice-over to the video.
4. Convert to MP4, store the master in MinIO, and upload to YouTube if public.
5. Update the manifest entry, then link it from the biz site and inside the app.
6. Re-run after UI changes (new version in MinIO, new manifest entry).

## Video storage

**Recommendation: scenarios in Git, master videos in MinIO, YouTube for viewing.**

| Option | Verdict | Why |
|--------|---------|-----|
| Git | No, for videos | Binary files bloat the repo permanently, even after deletion, and every re-run adds another copy. Git LFS reduces clone size but is still the wrong tool. Git is the right home for the scenario files, which are the real source. Videos are build output that can be regenerated. |
| MinIO | Yes, as master storage | Under your control and cheap on the netcup box. Enable bucket versioning so each re-run keeps the old copy and can be rolled back. Suits private tutorials, such as ones showing customer-specific data. Weakness: you serve the files yourself, plain MP4 has no adaptive streaming, and bandwidth comes out of your own server. |
| YouTube | Yes, for viewing | Free, adaptive streaming, captions (upload the `.srt` the recorder produces), mobile support, and no load on your server. Limits: a video file cannot be replaced under the same URL, so each re-run means a new upload and a new link. It is an external dependency, and embedding raises a GDPR question; use the `youtube-nocookie.com` embed domain to reduce that. Use "unlisted" for tutorials that should stay out of search results. |

### How to combine them

1. The recorder writes its output to MinIO, in a path like `tutorials/<name>/<version>/`.
2. For public tutorials, upload to YouTube from there (manually at first, via the API later).
3. Keep a small manifest in Git that maps each tutorial id to its video URL. The biz site and the in-app tours read this manifest, so swapping a video after a re-run is a one-line change, not a code change.

```yaml
# tutorials/manifest.yaml
tutorials:
  entity-designer:
    title: "Entity Designer: define your own business objects"
    version: 3
    visibility: public            # public | private
    youtube: https://youtu.be/XXXXXXXXXXX
    master: s3://tutorials/entity-designer/3/entity-designer.mp4
    subtitles: s3://tutorials/entity-designer/3/entity-designer.srt
  release-rollback:
    title: "Publish your changes safely (and roll back)"
    version: 1
    visibility: private           # served from MinIO only
    master: s3://tutorials/release-rollback/1/release-rollback.mp4
```

### Format

Convert the Playwright `.webm` to H.264 MP4 with ffmpeg before publishing. It plays everywhere and the files are usually smaller.

```bash
ffmpeg -i entity-designer.webm -c:v libx264 -crf 23 -preset veryfast \
  -pix_fmt yuv420p -movflags +faststart entity-designer.mp4
```

If you want only one option to start with, use YouTube for the public tutorials and add MinIO once you have videos that must stay private. The manifest keeps you free to move later.

## First three tutorials

| # | Tutorial | Outcome |
|---|----------|---------|
| 1 | Set up inventory management in 10 minutes | A working inventory app with one product record created |
| 2 | Change a field on a live entity without losing data | A saved migration set with the change, existing records intact |
| 3 | Publish your changes safely (and roll back) | A release created, applied, and rolled back once on purpose |

## Watch-outs

- **Trim the lead-in.** Video time zero starts slightly before the first step, so SRT timing can be off by a fraction of a second.
- **Reset demo data between runs.** A scenario that creates an entity will hit a duplicate on the next run. Use a fresh demo org per run, or have the scenario clean up.
- **Use a dedicated demo account** for the login credentials.
- **Selector failures stop the run** with an error naming the step ID, so a UI change fails loudly instead of recording a wrong video.
