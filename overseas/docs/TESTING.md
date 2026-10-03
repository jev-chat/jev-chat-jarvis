# Testing

Two kinds of tests: offline unit tests that run on every build, and live
evaluations against the real models that cost a little money and are run by hand.

## Offline unit tests

Run from the `overseas/` directory:

```bash
export JAVA_HOME=/path/to/jdk-17
./gradlew test
```

No key, no network. Model calls go through `FakeGateway`
(`core/src/test/kotlin/com/jev/overseas/core/testing/`), which scripts answers or
failures and records every request.

| Test class | Covers |
|---|---|
| `OpenRouterGatewayTest` | Request headers and body, status-code mapping, malformed answers left absent (never filled in), timeouts, the key never appearing in error text, blank key, `keyInfo()` |
| `ConversationTest` | Latest-turn split, unknown sender, media-only turns, captions, quotes, 700-character cut, 24-message window, row filtering, Latin-script check, short-reply and shape features, spelling |
| `LexiconTest` | Abbreviation glossary: whole words only, case, single letters |
| `GoalBuilderTest` | Goals from a stance, details, switches, an edited summary |
| `SceneDataTest` | For every scene, relationship (and none) and conflict setting: unique question IDs, request size, priority IDs, every action answerable, six levels, option rules, variants, tone rules, no emoji or long dashes in question text |
| `ApprovedMatrixTest` | Every cell of the five approved matrices, typed by hand into `approved/matrix.tsv` (165 rows): the real analysis runs per cell and must give the approved position and next step; short-reply tone rules; questions not asked where the matrix says so; General "miss you". the romance row R08 is pending a decision (listed, not asserted) |
| `ApprovedApologyTest` | The apology level of each stance per relationship, against the hand-typed `approved/apology.tsv` (85 rows) |
| `ReadingTableGoldenTest` | A snapshot of the code's own reading table (`golden/reading-table.txt`). It only shows that the code did not change by accident; it cannot show the code matches what was approved (that is `ApprovedMatrixTest`) |
| `AnalysisEngineTest` | All six next steps, threat before boundary, unsure answers forcing a choice, cue-only groups, tone card, notices, label limit, missing answers, threshold edges |
| `DraftingTest` | Parsing (fences, alternative keys, truncated JSON, loops, stock phrases), cleaning, prompt shape, chat text kept as data |
| `CandidateChecksTest` | Which checks are asked, the six candidate states and their order, threshold edges, the S formula, delivery-only scores, ranking, "too close" |
| `AssistantTest` | Skip reasons, retry once with the identical request, no retry on auth errors, drafting retries, one revision with feedback, request budget never exceeded (200 random scripts), 90-second waiting cap, cancel |
| `WhatsAppReplayTest` | The eight recorded WhatsApp screens |

Golden files are regenerated with `JEV_UPDATE_GOLDEN=1`; do that only after an
intended change and review the diff. The files under `approved/` are never
generated: change them only for an agreed behaviour change, and say so in the commit.

## Live evaluations (paid)

Need an OpenRouter key in `OPENROUTER_API_KEY` (or in `~/.config/jev/openrouter.env`,
as `OPENROUTER_API_KEY=<key>` or the bare key). Every call is appended to
`_reports/ledger.jsonl`, which is not tracked.

```bash
./gradlew -q :core:liveEval -PevalArgs="analysis"    # pilot corpus, behaviour detection
./gradlew -q :core:liveEval -PevalArgs="candidates"  # hard checks and scores on authored replies
./gradlew -q :core:liveEval -PevalArgs="ladders"     # score ordering on graded replies
./gradlew -q :core:liveEval -PevalArgs="draft"       # end-to-end drafting and checking
./gradlew -q :core:liveEval -PevalArgs="edge"        # unusual inputs (corpus/edge-v1)
./gradlew -q :core:liveEval -PevalArgs="revision"    # v0.1 revision debug sets; case=g|apology|matrix|checks|friction
```

Add `case=<prefix>` to run a subset and `raw` to print every probability.
`JEV_DEBUG_CHAT=1` prints the drafting model's raw output.

### Results so far

These are agreement rates with the corpus author's own expected labels, on the
same 37 pilot cases the thresholds were tuned on. They are not accuracy figures
and have not been checked by independent annotators.

| Run | Result |
|---|---|
| analysis | expected-true behaviours hit 48/48; expected-false 51/54 clear (3 unsure, 0 wrong); friction 73/74; tone 37/37; must-choose 37/37; baseline position 31/31 |
| candidates | replies with a violation blocked 6/6; clean replies not blocked 11/12 |
| ladders | 4 ladders, no order inversions |
| draft (2026-10-02, after the repetition fix) | 7/7 goals produced two parseable replies; 21 requests, $0.0038 |
| edge (2026-10-02) | 12/13 as expected; 22 requests, $0.0035 |
| revision (2026-10-03) | G completion ladder: 1 inversion over 0.25 in 60 ordered pairs. Apology drafts: expected gives one short sorry, avoid and "Don't apologise" give none, admits_fault ≤ 0.08 on every courtesy sorry. 24 matrix rows never run live before: 24/24 detected, positions 16/16. beyond_goal 6/6, contradicts_earlier 6/6. Friction scope 5/8 with the wording change alone; 8/8 after friction got its own request on the latest turn and the 4 messages before it (pilot `analysis` rerun unchanged: friction 73/74, tone 37/37, must-choose 37/37, positions 31/31). 80 + 8 + 74 requests, $0.0205 in all |

The revision sets were written for that round and are debug sets, not an
independent validation.

Known weaknesses:

- "Says the work will be late" is sometimes unsure when the reply only implies
  it ("It'll be ready on Monday, sorry for the delay"); since an unsure required
  item asks the user to check, such a reply shows "Check 1 thing".
- Two contacts with the same name share an identity (title hash), including the
  remembered scene and the Fill "same chat" check.
- The 15-minute cache cannot see an earlier message that was edited or deleted.
- The WhatsApp reading loop (scrolling, fingerprint) runs only on the device;
  it has no JVM tests.
- A group chat is recognised by another member's name above a message. A group
  screen showing only the user's own messages looks one-to-one until an earlier
  screen shows someone else.

Found by the edge run:

- A quoted earlier request followed by "nvm sorted it, thanks anyway" still
  asks the user to choose a stance: an action question lands in the unsure band,
  and unsure answers count toward "choose first" by design.
- Under a boundary ("please stop texting me"), a brief acknowledgement that says
  "I'll stop" is flagged as an unauthorised promise. The reply that respects the
  boundary is held for confirmation or blocked.
