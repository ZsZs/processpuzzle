# ProcessPuzzle Demo Video — Character Animation Tool Recommendation

## Requirement
A small, hand-drawn/watercolor-style 2D "explainer" character that appears in a small rectangle overlay during the screen-capture portions of the video, with minimal manual animation effort (AI-generated/automated).

## Recommended tool: HeyGen — Photo/Image-to-Video ("Avatar IV")
- Draw **one static, front-facing portrait** of the character in the existing watercolor/hand-drawn icon style — this is the only manual art step.
- Upload that single image; type or paste each scene's script line (or upload audio) and HeyGen generates a lip-synced, gesturing clip automatically.
- Handles stylized/cartoon characters (not just human photos) with automatically generated facial expressions, head tilts, and micro-expressions matched to the emotional tone of the script.
- Pricing: Creator plan ≈ $29/month.

**Avoid transparent-background export.** It's plan-gated and unreliable on HeyGen. Since the character sits in a small fixed rectangle (not freely composited), this isn't needed — generate against a plain solid background and crop to a rounded rectangle in the video editor instead.

## Budget alternative: D-ID
Same single-image → talking-video approach. Lite plan ≈ $5.90/month, Pro ≈ $29/month. Good for testing the workflow cheaply before committing to HeyGen.

## Workflow
1. Draw one clean, front-facing character portrait (existing icon style).
2. Upload to HeyGen (or D-ID); generate one short clip per script scene using the narrator lines as the script text (see `processpuzzle-onboarding-video-character-prompts.md` for the per-scene breakdown).
3. Export each clip as a plain-background MP4.
4. In editing, crop each clip into a small rounded rectangle and overlay it on the matching screen-capture clip per the shot list.
