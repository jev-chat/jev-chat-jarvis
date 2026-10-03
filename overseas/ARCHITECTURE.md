# Architecture: Jev for WhatsApp

One round, from tap to filled message box:

```
tap bubble
  → read the chat on screen        assistant/service/WhatsApp.kt, a11y/, core/whatsapp, core/stitch
  → who is this? (first time only) core/scene, assistant/Settings (remembered per chat)
  → analyse                        core/engine/Analysis.kt   (2 Jev requests)
  → pick a stance, add details     core/engine/Goal.kt
  → draft two replies              core/engine/Drafting.kt   (1 chat request, retried once)
  → check and score each reply     core/engine/CandidateCheck.kt (Jev requests)
  → Fill                           core/session/Fill.kt, assistant/service
  (the user sends)
```

`core/session/AssistantSession.kt` is the state machine that runs this and produces
the `PanelState` the panel draws. Everything under `core/` is plain Kotlin with no
Android types, so the whole flow is unit-tested on the JVM with a scripted model
gateway.

## Modules

| Module | Kind | Depends on | Role |
|---|---|---|---|
| `core` | Kotlin/JVM library | nothing (no third-party libraries) | Engine, scenes, session, WhatsApp adapter, stitching, model gateway, diagnostics |
| `a11y` | Android library | `core` | Collects accessibility nodes into `ScreenDump`s; measures whether rows are still moving |
| `assistant` | Android app | `core`, `a11y` | Accessibility service, bubble and panel (plain Views), reader loop, Fill, settings, key vault, diagnostic log |
| `probe` | Android app | `a11y` | Development recorder for WhatsApp screens; no `INTERNET` permission |

## Reading the chat

- `a11y/NodeCollector` turns the active window into a `ScreenDump` (view ids,
  bounds, text, content descriptions).
- `core/whatsapp/WhatsAppAdapter` classifies the page (chat, chat list, group) and
  turns rows into messages: sender by which side the bubble hugs, text, quoted text, media kind
  (photo, document, voice note, video, sticker, deleted), date dividers and system
  notices. It relies on WhatsApp's resource ids; the recorded screens in
  `fixtures/screendumps/whatsapp-2.26.38.73/` pin what it expects.
- `assistant/service/WhatsApp.kt` scrolls the list upward screen by screen, waits
  for rows to settle (`a11y/RowStability`), and stops at 24 messages, the start of
  the chat, 5 screens or 8 seconds. Then it scrolls back to the bottom.
- `core/stitch/Stitcher` joins overlapping screens into one ordered list without
  duplicates. A fingerprint of the last messages detects that the chat changed
  while reading.
- A group is recognised by member names above messages and refused.

## Conversation and scene

`core/engine/Conversation` splits the read messages into the latest turn (their
messages since your last one) and the earlier context, and derives features: short
replies, message shape, spelling (US/UK), abbreviations, whether the latest turn is
only a file.

The user picks a scene (Work, Romance, Friends, Family, General) and a relationship
type, plus "in a dispute" where it applies. Each scene in `core/scene/` defines:

- **Behaviours**: yes/no questions Jev answers about the latest turn (for example
  "asks for a deadline", "sets a boundary", "mentions escalating").
- **The matrix**: for each behaviour and relationship, the position of the
  exchange (`ABOVE`, `AT`, `BELOW`, `RARE`, `CUE`, `DEPENDS`) and the next step.
  These five matrices were approved by hand and are pinned cell by cell in
  `core/src/test/resources/approved/matrix.tsv`.
- **Stance groups**: the options offered for what was detected, each with required
  items, things to avoid, commitments and an apology level per relationship
  (pinned in `approved/apology.tsv`).

## Analysis

`AnalysisEngine` sends two Jev requests in parallel to OpenRouter's decisions
endpoint (`typesafe/jev-1.13` by default): one with the behaviour, tone and
must-choose questions over the conversation, and one with only the friction
questions over the latest turn and the four messages before it. Answers are
probabilities. Thresholds turn them into detected, unsure or absent; unsure answers
that matter make the user choose rather than guess. The result is a next step,
stance groups, cues and notices for the panel.

## Goal

`GoalBuilder` turns the chosen stance into a `Goal`: a summary, required items
("must include"), avoids, commitments, the apology level, fine-tune switches and the
user's own details. A detail the user typed becomes a required item of its own, so
a reply that leaves it out cannot rank first.

## Drafting

`Drafting` asks the drafting model (`deepseek/deepseek-chat-v3.1` by default,
OpenRouter chat completions) for two replies as JSON. The prompt carries the
conversation as data, the relationship, the situation, the goal and spelling.
Output is parsed defensively (fences, alternative keys, truncated JSON, loops) and
cleaned.

## Checks and score

`CandidateChecks` asks Jev about each reply:

- **Hard checks** that block a reply: a new commitment you did not ask for
  (`new_commitment`), committing other people (`commits_others`), deciding more than
  the goal covers (`beyond_goal`, `beyond_clarification`), taking the opposite
  stance, crossing a boundary they set, stating facts nobody gave
  (`unsupported_fact`), and the goal's own avoids.
- **Confirm-only checks** that ask you to look before using it: admitting fault,
  contradicting a date, time or amount you gave earlier, an unstated declaration.
- **G, goal completion**, on six levels from "Opposite or absent" to "Complete".
  A missing required item caps G at 3.
- **E, delivery**: tone and fit for the relationship.

The score is `S = 0.7 G + 0.3 E`, shown out of 5 with one decimal. Each reply gets a
state, ranked in this order: eligible, awaiting confirmation, needs an edit,
unscored, failed, needs a rewrite. "Top pick" goes only to an eligible reply whose
judgement was not split and that is more than 0.2 ahead of the next.

## Limits per round

`RunBudget` caps a round at 17 model requests and 90 seconds of waiting. A failed
request is retried once with the identical body; authentication errors are not
retried. The user can cancel at any time.

## Fill

`core/session/Fill.kt` decides whether Fill may write: the same chat must still be
open (by a hash of the title), and an existing draft in the box is replaced only
after asking. The service then sets the text of WhatsApp's input field. There is
no code path that clicks the send button or triggers the IME enter action.

## Diagnostics

`core/diagnostics/Diagnostics` builds one JSON line per step (read, analysis, goal,
candidates, errors, actions) with counts, probabilities, scores and timings, never
text. `assistant/DiagnosticLog` keeps the latest 1000 lines in private storage and
builds the shareable report. See [docs/DIAGNOSTICS.md](docs/DIAGNOSTICS.md).
