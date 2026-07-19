# Accessibility audit — Phase 7 (2026-07-19)

Method and findings for the §13.3 baseline. Automated checks ran on the API 36 emulator
(320×640 — the worst case for reflow); the with-ears TalkBack pass is an owner item below.

## 1. Unlabeled interactive elements — PASS
`uiautomator dump` of Matrix, Browse, and Settings, flagging `clickable="true"` nodes with empty
`text` and `content-desc`: **0 findings on all three surfaces.** Interactive elements carry either
visible text or a contentDescription (completion disc, overflow, sync chip, eye toggle, select
mode, tag/subtask/dependency removers all covered).

## 2. Font scale 2.0 reflow — PASS
`settings put system font_scale 2.0` on a 320dp-wide screen: labels wrap to multiple lines,
switches stay aligned, no clipped fixed-height text, bottom-nav labels wrap but remain legible.
All type uses `sp`; card titles lift their line caps at accessibility scales.

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
- Custom accessibility actions on cards (complete/edit) exist; snooze/delete equivalents are
  reachable via the long-press menu — confirm the menu opens under TalkBack double-tap-and-hold.
- Contrast: the §5.1 accent values are pre-verified AA by the spec; no runtime check needed.
