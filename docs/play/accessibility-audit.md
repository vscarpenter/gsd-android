# Accessibility audit — Phase 7 (2026-07-19)

Method and findings for the §13.3 baseline. Automated checks ran on the API 36 emulator
(320×640 — the worst case for reflow); the with-ears TalkBack pass is an owner item below.

> Corrected 2026-09-24. The 2026-09-20 polish review (`docs/play/2026-09-20-polish-review.md`,
> Accessibility section) found three claims here that the code did not support: text fields had no
> TalkBack name, cards had no custom actions, and card titles did not lift their line cap. Each is
> fixed in code now, and the notes below say so where they apply. None of it has been heard on a
> device yet; the TalkBack walkthrough below is still the owner's.

## 1. Unlabeled interactive elements — PASS
`uiautomator dump` of Matrix, Browse, and Settings, flagging `clickable="true"` nodes with empty
`text` and `content-desc`: **0 findings on all three surfaces.** Interactive elements carry either
visible text or a contentDescription (completion disc, overflow, sync chip, eye toggle, select
mode, tag/subtask/dependency removers all covered).

*Correction:* the check skipped text fields, which are not `clickable`. Every field was unnamed:
its hint sat beside it and was read as loose text. Each hint now sits inside its field, which reads
it as its name (spec §13.3), and a source test fails any field without a decoration box.

## 2. Font scale 2.0 reflow — PASS
`settings put system font_scale 2.0` on a 320dp-wide screen: labels wrap to multiple lines,
switches stay aligned, no clipped fixed-height text, bottom-nav labels wrap but remain legible.
All type uses `sp`; card titles lift their line caps at accessibility scales.

*Correction:* when this was written, titles still stopped at two lines at every scale (review
finding 35). They lift the cap from font scale 1.5 since 2026-09-24 (`cardTitleMaxLines`,
`TaskCardLayoutTest`).

## 3. Reduced motion — PASS (by construction + code review)
Every non-essential animation gates on `ValueAnimator.areAnimatorsEnabled()`: confetti is skipped
entirely, the running-timer dot goes static, onboarding cross-fades. Verified in code review;
exercised earlier in Phase 1 manual testing.

## 4. Touch targets — PASS (by construction)
48dp minimums enforced at build time on the small controls (completion disc, chip removers,
IconButtons are 48dp by default).

## Owner items (need ears and hands on a real device)
- TalkBack walkthrough: reading order on the task card (title → quadrant → due → status), editor
  section headings announced, snackbar Undo reachable (12 s window under TalkBack is wired).
- Custom accessibility actions on cards: Complete (or Uncomplete), Edit, Snooze 1 hour, and
  Delete, since 2026-09-24 (`CardActionsTest`). Before that there were none, whatever this line
  said. Confirm TalkBack lists them, and that the "⋯" button opens the full menu.
- Contrast: `ContrastTest` holds every text ink to 4.5:1 on paper, sunken, surface, and surface2 in
  both modes, and each quadrant's text ink to 4.5:1 on its wash (spec §5.1 to §5.3). The
  spec's colors were not all AA as text: `ink3` read 4.17:1 on paper, and Ochre 4.17:1 on its wash.
