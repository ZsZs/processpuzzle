# ProcessPuzzle Demo Video — Assembly Plan
### Putting script + screen captures + character clips together into the final video

This is the last step: once you have (a) the 6 screen-capture recordings from the shot list, and (b) the 8 character clips from the prompts doc, here's how to cut them into the finished ~3-minute video.

## 1. Tooling
Any basic video editor works — DaVinci Resolve (free, most capable), CapCut (free, simplest), or Premiere/Final Cut if you already have them. Nothing in this plan needs advanced features beyond: multi-track timeline, picture-in-picture (PiP) positioning, rounded-rectangle/mask crop, and text/title cards.

## 2. Timeline structure (per scene)
Every scene follows the same 2-track pattern:
- **Track 1 (background, full frame):** the screen-capture clip for that scene (or the title/end card for Scenes 1 and 8)
- **Track 2 (foreground, small rectangle, e.g. bottom-right corner):** the matching character clip, cropped to a rounded rectangle, roughly 20–25% of frame width
- **Audio:** the character clip's own narration audio carries the voiceover for the whole scene — mute the screen-capture recording's audio (if any) so there's no clash

## 3. Scene-by-scene cut sheet

| Scene | Background (Track 1) | Character clip (Track 2) | Approx. length |
|---|---|---|---|
| 1 — Intro | Title card: "ProcessPuzzle: Onboarding, from idea to app" | Clip 1 | 0:00–0:20 |
| 2 — Model the Data | Spot #1 screen capture (Entity Designer) | Clip 2 | 0:20–0:50 |
| 3 — Business Rules | Spot #2 screen capture (Rule Designer) | Clip 3 | 0:50–1:15 |
| 4 — Document | Spot #3 screen capture (Document Designer) | Clip 4 | 1:15–1:35 |
| 5 — States | Spot #4 screen capture (State Machine Designer) | Clip 5 | 1:35–1:55 |
| 6 — Workflow | Spot #5 screen capture (Workflow Designer) | Clip 6 | 1:55–2:25 |
| 7 — Compose the App | Spot #6 screen capture (App Composer + running app) | Clip 7 | 2:25–2:50 |
| 8 — Close | End card: logo, tagline, call to action | Clip 8 | 2:50–3:00 |

Since each character clip's narration is what drives pacing, **cut the screen-capture footage to match the character clip's length**, not the other way around — trim the screen recordings (which you recorded a bit longer, per the shot list) down to fit under each line.

## 4. Assembly steps
1. Import all 6 screen-capture clips and 8 character clips into the editor.
2. Lay the 8 character clips on Track 2 first, in order, back-to-back — this sets the master timing/pacing for the whole video (their total runtime = ~3:00).
3. For each character clip, drop the matching background (screen capture or title/end card) onto Track 1 underneath it, trimmed to the same length.
4. Crop/mask each character clip into a small rounded rectangle, positioned consistently (same corner, same size) across every scene, so it doesn't jump around.
5. Mute any audio on Track 1; keep the character clips' audio as the only voiceover track.
6. Add simple crossfades (a few frames) between scenes so cuts don't feel abrupt.
7. Add an optional light background music track under everything, ducked low under the narration.
8. Export at 1080p, then upload wherever the processpuzzle-biz site will host it (per the biz-site content plan, Part 1 — the video accompanies the condensed "what ProcessPuzzle is" intro).

## 5. Deliverable checklist
- [ ] `processpuzzle-onboarding-video-script.md` — narrative script
- [ ] `processpuzzle-onboarding-video-shotlist.md` — screen-capture shot list
- [ ] `processpuzzle-onboarding-video-character-tool.md` — character tool recommendation
- [ ] `processpuzzle-onboarding-video-character-prompts.md` — per-scene character clip scripts
- [ ] `processpuzzle-onboarding-video-assembly-plan.md` — this document
- [ ] 6 screen-capture recordings (per shot list)
- [ ] 8 character clips (per character prompts doc)
- [ ] Final exported video (1080p)
