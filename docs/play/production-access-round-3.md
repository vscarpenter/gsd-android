# Production access, round 3

Written 2026-10-13. Read this before the next "Apply for production". It replaces the "wait for Google's
answer" step in `tasks/todo.md` section A, which never recorded the answer.

## Where things stand

Google has declined production access twice. Both emails are identical and name the same two reasons.

| Date (CDT) | Event | Evidence |
|---|---|---|
| Aug 29 | 0.8.0 published to Closed testing, Alpha track | `submission-checklist.md` |
| Sep 6 | 0.9.0 published. Tester call sent to `gsd-android-testers@googlegroups.com`; recruiting on r/AndroidClosedTesting, which is reciprocal | Sent mail, Sep 6 |
| Sep 20, 8:08 PM | Applied for production (round 1) | `tasks/todo.md` A |
| Sep 21, 6:06 PM | Declined: "More testing required to access Google Play production" | Email to vscarpenter@gmail.com |
| Sep 21, 9:08 PM | 0.9.1 published to testers, three hours after the decline | `submission-checklist.md` |
| Sep 24 | #29, #30, and #31 merge 49 fix and feature commits to `main`. None reach testers | `git log 3c62197..main` |
| Oct 5 to 9 | Applied again (round 2). The date and the answers were not recorded | Earliest allowed date was Oct 5 |
| Oct 9, 4:45 PM | Declined again, same email | Email; Console "Reviewed Friday, 4:45 PM" |
| Oct 13 | Console: 12 testers opted in, 3 days continuous. Needs 14 days from the review date | Console screenshot |

The earliest date to apply again is **Friday Oct 23 or Saturday Oct 24**, when the Console's third
checklist item ticks and "Apply for production" enables. Don't apply before the button enables.

## What Google said

The decline email, verbatim:

> Possible reasons why your production access could not be granted include:
> Testers were not engaged with your app during your closed test.
> You didn't follow testing best practices, which may include gathering and acting on user feedback
> through updates to your app.
> Before applying again, test your app using closed testing for an additional 14 days with real testers.

Google's help page (answer 14151465) adds that the 14 days must be consecutive, that a tester who opts
out early doesn't count, and that the application asks how testers engaged, what feedback came in and how,
and what changed as a result. Its best-practice list: give testers instructions and the feedback you want,
encourage them to use as many features as possible, keep a record of feedback, and "continue running closed
tests while resolving user-reported issues and bugs." Google doesn't publish how it measures engagement.

## What Google probably saw

These are inferences from the evidence above. The Console's engagement data and the round 2 answers would
confirm or rule them out.

1. **One update per window, at the start.** Round 1 had 0.9.0 on Sep 6. Round 2 had 0.9.1 on its first
   evening and nothing for the next 18 days, while `main` took 75 commits. "Acting on feedback through
   updates" had almost no evidence behind it.
2. **Testers recruited to pass the gate.** The Sep 6 call asked testers to "install GSD ... and open it
   once" and to "use it as much or as little as you like." Some testers came through reciprocal swaps (one
   wrote on Sep 6 asking for a swap test of their own app). An opted-in tester who opens the app once looks
   like no engagement. "With real testers" in the email reads as pointed.
3. **A US-only track.** The Alpha track targets the United States only. On Sep 6 a tester in Europe
   (writing in French, UTC+2) replied "Application not available for my phone: Samsung S23." Country targeting applies to closed
   tracks (Google exempts only internal testing), so a tester outside the US counts as opted in and can't
   install. That tester adds to the count and subtracts from engagement.
4. **Unknown: the answers.** Neither application's answers were saved. This doc keeps the next set.

## The plan

| When | Step | Who |
|---|---|---|
| Tue Oct 13 | `release/0.10.0` cut at `4813ff5` (versionCode 11) | Done |
| Wed Oct 14 | Run the gates, build the signed bundle, upload to Alpha with the notes below | Owner, on the Mac |
| Wed Oct 14 | Alpha → Countries/regions: add every country a tester lives in (or all of them; only opted-in testers can install) | Owner, Console |
| Wed Oct 14 | Send the tester email below. Recruit at least 6 more real testers so churn can't reset the clock | Owner |
| Oct 14 to 19 | Week 1 checklist. Log every report the day it arrives | Owner |
| Mon Oct 19 | Day-7 check-in email: what's broken, what's confusing | Owner |
| By Oct 19 | Deploy gsd.vinny.dev so the B-01 fix is live before testers sync with the web (see 0.10.0 below) | Owner |
| Tue Oct 20 | Ship 0.10.1 (versionCode 12) with fixes for tester reports only. Name them in the release notes | Owner |
| Oct 20 to 23 | Week 2 checklist. Release smoke on the Pixel from the Play-installed 0.10.1 | Owner |
| Fri Oct 23 | Day-14 check-in. Fill the answers below from the log and the Console | Owner |
| Oct 23 or 24 | Apply, once the button enables. Paste the submitted answers into this doc | Owner |

Rules for the window:

- Never let the count drop below 12. Adding testers never resets the clock; losing one below 12 does.
  Aim for 18 or more opted in.
- Don't change the tester lists in a way that could opt anyone out.
- Keep the closed test running after you apply, until Production publishes.
- Every fix in 0.10.2 gets a row in the feedback log with the report that caused it.

## Who to recruit

People who would use a task manager for two weeks without being asked twice. In rough order of value:

1. **Existing GSD users with Android phones.** They can sync with the web or iPhone app, which shows
   Google that usage matches production. The landing page says Android is "In the works" (`PlatformsSection.tsx`,
   `FaqSection.tsx`); a "Join the Android beta" link there is the cheapest channel. The gsd.vinny.dev usage data says how many visitors
   are on Android.
2. **Newsletter and LinkedIn readers** who have asked about Android.
3. **Friends, family, and former colleagues** with Android phones, if they'll do the checklist.

Avoid adding more reciprocal-swap testers. They hold the count but likely hurt the engagement signal.

Recruiting blurb, for a post or a direct message:

> I'm looking for a few Android users to test GSD for two weeks before it launches on Google Play. GSD is
> my Eisenhower matrix task manager: it sorts what's urgent from what's important, works offline with no
> account, and syncs with the web and iPhone apps if you want it to. If you have an Android phone and a
> to-do list, join the tester group at [group link], then tap "Become a tester" at
> https://play.google.com/apps/testing/dev.vinny.gsd. It takes a few minutes a day, and I read every note.

## Tester email (send with 0.10.0)

**To:** `gsd-android-testers@googlegroups.com` and `gsd-android-testers-needed@googlegroups.com`
**Subject:** GSD 0.10.0 is out, plus a two-week checklist

> Hi everyone,
>
> Thank you for sticking with GSD. Google reviewed the closed test and wants two more weeks of real use
> before it opens the app to everyone, so I need something more specific from you than "open it once."
>
> Version 0.10.0 is rolling out now, and it's the biggest update of the test. You can set a due time
> instead of only a date, reminders ask for notification permission when they need it, and swiping left
> on a task reveals Snooze and Delete. TalkBack support, contrast, and tablet layouts all got a lot of
> work too. If it hasn't arrived, open GSD's page in the Play Store and tap Update.
>
> Here's the ask. It takes a few minutes a day, and each item tests something real.
>
> This week (Oct 14 to 19):
> 1. Add five real tasks from the capture bar. Try the shorthand: `!!` for Do First, `*` for important,
>    `!` for urgent, and `#work` for a tag.
> 2. Give one task a due date, a time, and a reminder. When it fires, tap Snooze or Complete on the
>    notification.
> 3. Add the Today's Focus widget to your home screen, and long-press the GSD icon to try a shortcut.
> 4. Share a web page from Chrome to GSD.
> 5. Complete a few tasks, then look at the Dashboard.
>
> Next week (Oct 20 to 23):
> 6. Make a task repeat, add subtasks, or make one task wait on another.
> 7. Delete a task and undo it, then restore another one from Trash.
> 8. Optional: sign in under Settings, then edit a task at gsd.vinny.dev on a computer and watch it show
>    up on your phone. Use the same sign-in you use on the web.
> 9. Switch to dark mode or a larger font size and tell me what looks off.
>
> To tell me what you found, use Settings, then Feedback, in the app (it's anonymous), or reply to this
> email if you'd like an answer. I'll ship fixes for what you report around October 20.
>
> One request matters more than it sounds: please stay opted in and keep GSD installed through
> October 24. If the count drops below 12, Google's two-week clock starts over.
>
> Thank you. Every bug you find now is one a stranger won't find later.
>
> Vinny

## 0.10.0 release

- Branch `release/0.10.0`, bump commit `4813ff5`, versionCode 11, versionName 0.10.0. It carries #29, #30,
  #31, and #32: 52 fix and feature commits since 0.9.1.
- Gates, on the Mac: `./gradlew :core:model:test testDebugUnitTest`, then `connectedAndroidTest` on
  `gsd-test`, then `:app:bundleRelease`. Check the bundle reads `versionCode=11 versionName=0.10.0`.
- Upload the same way as 0.9.1: Test and release, Closed testing, Alpha, Create new release. Managed
  publishing is off, so Save submits.
- Due times are an open parity decision (backlog D-1). Shipping 0.10.0 to testers takes option (b) for the
  closed test: Android sets a time, and web and Apple must keep it. A code read on 2026-10-13 says Apple's
  editor opens with the stored `dueDate` and writes it back unchanged unless the user picks a new date
  (`gsd-iosapp` `TaskEditorView.swift:72, 555`). The web keeps a stored time only after B-01 (PR #599)
  deploys, so deploy gsd.vinny.dev before week 2 asks testers to sync with it.
- Release notes for the `<en-US>` box (459 of 500 characters):

> Set a due time, not only a date, or mark a task all day. GSD asks for notification permission the first
> time a reminder needs it, and Settings can allow exact alarms. New welcome tour, Field Guide, and About
> page. Swipe left on a task for Snooze and Delete. The editor keeps Save at the top and asks before
> discarding edits. Shared links open over the app you shared from. Better TalkBack support and text
> contrast, tablet and landscape fixes, and safer sync.

## 0.10.2 release

Submitted to Alpha on 2026-11-02 as versionCode 13, versionName 0.10.2, and published the same day at 11:52 AM CST. The 0.10.1 build never reached the Console, so testers moved from 0.10.0 to this build. App code matches 0.10.1, and the notes in the 0.10.1 section describe it. The answers below say 0.10.2 (updated 2026-11-06), and the shipped builds are 0.8.0, 0.9.0, 0.9.1, 0.10.0, and 0.10.2.

## 0.10.1 release

Prepared 2026-10-31 in sprint 1. Versions: versionCode 12, versionName 0.10.1. Never uploaded.

- **It isn't the build this doc planned.** The plan above says 0.10.1 ships Oct 20 with tester-reported fixes only. It's shipping later, and it carries parity and sync-safety work instead. Nothing in it came from a tester report that this repo records, because the feedback log below is empty. Don't present these changes as tester feedback.
- **First sync.** Pull contract 3: the first sync after the update pulls everything once, to bring `saved_at` to rows 0.10.0 already holds.
- **Release notes for the `<en-US>` box (439 of 500 characters):**

> Saved keeps reading and reference outside the matrix, with short notes, and syncs with the web and Apple
> apps. Tasks due soon can show as escalating, with a Mark urgent action and a setting for the window. All-day
> tasks remind at 9 AM. Pull down on the Matrix to sync. Sync history shows totals, and the Dashboard shows
> completion by tag. Safer sync: fixes protect Saved items, new tasks, and edits, including when a device
> clock is wrong.

### Round 3 answers, checked 2026-10-31

The draft answers below are only as true as these items. S-AND checked what the repo and sibling repos can show. It can't read the Console, the tester list, or the feedback collection.

| Claim in the draft | Finding |
|---|---|
| "We shipped [five] builds" | Five: 0.8.0, 0.9.0, 0.9.1, 0.10.0, and 0.10.2, published Nov 2 |
| "0.10.0 (Oct 14)" | Submitted Oct 15 at 8:00 AM CDT (`submission-checklist.md`). Corrected below |
| "0.10.2 (Nov 2): [tester-reported fixes]" | False as written. Corrected below: parity and sync work, published Nov 2 |
| "[N] JVM unit tests and [N] instrumented tests pass" | See `submission-checklist.md`, 0.10.2. Those ran on the debug build, while the draft says "on the release build". Reword or rerun |
| "We ran a release smoke test on a Pixel" | **Not done.** Open since Aug 29 (`tasks/todo.md`, section A). Do it before this sentence is sent |
| "Each 0.10.2 fix points to a report", and "every fix in 0.10.2 gets a row" | **Can't be true for this build.** Log the tester reports you have, and describe the rest as internal work |
| Testers, engagement, vitals, pre-launch report, day-14 status | **Owner only.** Not visible from here |

## Feedback log

Feedback arrives in four places. Check each daily.

- **In-app Feedback.** PocketBase at api.vinny.io, `feedback` collection. Android rows carry
  `app_version` like `android-0.10.0.11`, so filter on `app_version ~ "android-"`. Rows are anonymous.
- **Replies** to the tester email.
- **gsdapp@vinny.dev.**
- **Android vitals** for crashes and ANRs on 0.10.0 and 0.10.2.

| Date | Source | Build | Report | Fixed in |
|---|---|---|---|---|
| | | | | |

## Draft answers for the application

Every sentence must be true on the day you apply. Fill each bracket from the log and the Console, and
delete any sentence you can't back up. Changes that came from the internal 2026-09-20 polish review are
labeled as such; don't present them as tester feedback.

**How easy was it to recruit testers?** Pick the honest option. Context, if the form asks: our first rounds
drew on a tester-exchange community, and those testers rarely used the app. For this round we recruited
[N] people who [use GSD on another platform / manage work and personal to-dos].

**Describe tester engagement.** [N] testers stayed opted in from [date] to [date]. Each received a
two-week checklist that covered every feature: quick capture with the shorthand, due dates and reminders
with notification actions, the home-screen widget and shortcuts, sharing links into the app, recurring
tasks, subtasks and dependencies, Trash and undo, and optional sync with our web app. [N] testers told us
they finished week one and [N] finished week two. [N] turned on sync and used the Android app alongside
gsd.vinny.dev or the iPhone app. Usage matched what we expect in production: [one concrete pattern from
the feedback].

**Summarize feedback and how you collected it.** Testers reported through an anonymous Feedback form inside
the app, replies to our tester mailing list, and email to gsdapp@vinny.dev. We logged each report with its
date and the build that fixed it. We received [N] reports: [N] bugs, [N] usability notes, and [N] feature
requests. The main themes were [theme] and [theme]. We fixed [list, only what testers reported] in 0.10.2 and kept [list] for after
launch.

**Target audience.** Adults who juggle work and personal to-dos and want a quick answer to "what should I
work on next?", including people who already use GSD on the web, iPhone, iPad, or Mac.

**Value to users.** GSD sorts tasks by urgency and importance, the Eisenhower matrix, so the next task is
obvious. It works fully offline with no account, no ads, no analytics, and no tracking. Optional sync keeps
the same tasks on Android, the web app, and the Apple apps.

**Expected installs in year one.** [Pick a range you can defend from gsd.vinny.dev traffic and the Apple
app's numbers.]

**Changes made during testing.** We shipped [five] builds to testers:
- 0.8.0 (Aug 29): the first closed-test build.
- 0.9.0 (Sep 6): completion rings and deadline badges on the Dashboard, search across Browse, and sync
  hardening so a partial server listing can never delete local tasks.
- 0.9.1 (Sep 21): fixes from our own review of every screen. Swipe actions and the task menu work on every
  list, a snoozed reminder fires again, tapping a reminder opens its task, and the editor no longer undoes a
  change that synced in while it was open.
- 0.10.0 (Oct 15): our review found every due date landed at midnight because the editor had no time
  picker, so we added date and time pickers. Also a notification-permission request at the moment it's
  needed, an exact-alarm setting, a rebuilt welcome tour, TalkBack labels and AA contrast, and tablet and
  landscape layouts. [Name any item a tester reported.]
- 0.10.2 (Nov 2): from our own parity and sync review, not tester reports: Saved items in sync with
  the web and Apple apps, escalation of tasks due soon, all-day reminders at 9 AM, pull to refresh, and sync
  fixes that protect Saved items, new tasks, and edits. [Add any tester-reported fix from the log, named as one.]

**How you decided the app is ready.** [N] JVM unit tests and [N] instrumented tests pass on the release
build. We ran a release smoke test on a Pixel from the Play-installed build: sign-in, sync with the web
app, a reminder firing, the widget, and deep links. Android vitals show [no crashes / a crash-free rate of
N%] across the test. The pre-launch report [found no issues / found X, fixed in Y]. TalkBack labels, 48dp
touch targets, and 4.5:1 text contrast are covered, and an automated test holds the contrast. The privacy
policy is linked in the app and the listing, accounts can be deleted in the app and on the web, and the
last [N] days of testing brought no new crash or data-loss report.

## Before you click Apply

- [ ] The Console shows 14 continuous days and the button is enabled.
- [ ] At least 12 testers opted in, ideally 15 or more.
- [ ] 0.10.0 and 0.10.2 both published inside the window.
- [ ] The feedback log has dated entries, and each 0.10.2 fix points to a report.
- [ ] Android vitals: no open crash cluster on 0.10.x.
- [ ] Pre-launch report for 0.10.2 reviewed.
- [ ] Release smoke on the Pixel done (open since Aug 29): sync, a reminder, the widget, a deep link, one
      real sign-in.
- [ ] The privacy policy update is merged and deployed (`gsdtaskmanager.com` `fix/privacy-android-feedback`).
- [ ] The Console's Data safety form matches `data-safety.md`, including the feedback row.
- [ ] Answers saved here before you submit.

## Fix before Production, separate from the testing gate

The privacy page at gsdtaskmanager.com/privacy (`gsdtaskmanager.com/app/privacy/page.tsx`) still names only
the web, iPhone, iPad, and Mac. It never mentions Android, the anonymous feedback form, or the no-cloud-
backup rule. It also says your data "never leaves your device unless you turn on the optional sync" and
that without sync "there is no server in the picture," which leaves out feedback: a feedback message
reaches the server with sync off. This isn't
why Google asked for more testing, but the policy must describe what the app does before the listing goes
public. Three changes cover it. **Done 2026-10-14** on `gsdtaskmanager.com` branch
`fix/privacy-android-feedback` (`099eaf9`), which also corrects the synced-data bullet: turning off sync
never cleared the server, and Delete account does. Merge and deploy it before you apply.

1. In "Data stored on your device" and "Optional sync," add Android next to iPhone, iPad, and Mac.
2. Add a "Feedback" section: what the form sends (the message, a thumbs up or down, a category, roadmap
   votes, the app version, a random submission id, and the device's clock time), that it carries no
   account, email, or device id, that it's sent only when you tap Send, and that the server deletes it
   after 180 days.
3. Add one line on backups: the Android app keeps tasks out of cloud backup, and a phone-to-phone transfer
   moves them without the sign-in token.

## After access is granted

- Promote 0.10.2, or ship 1.0.0 (versionCode 14 or later) to Alpha first and promote that. 1.0.0 should be a build
  testers have run.
- Landing page: replace "In the works" and add the Play badge (parity backlog R-4).
- Record the outcome in `tasks/todo.md` and the parity docs.
