# Diagnostics and bug reports

The assistant keeps a local log of what each step of a round did, so that a bug
report can show where things went wrong. It is meant for development and for
users who want to report a problem.

## What is recorded

One JSON line per event in app-private storage (`files/diagnostics/journal.jsonl`),
the newest 1000 lines kept. Schema `jev.diagnostics/1`; events are built in
`core/.../diagnostics/Diagnostics.kt`.

| Event | Fields |
|---|---|
| `round` | round id, why it started (`analyse`, `read_again`), scene, relationship, conflict |
| `read` | ok or why not (`not_conversation`, `unstable`, `unavailable`, `not_at_latest`, `chat_changed`), message count, new from them, unreadable rows, screens, whether the earlier messages came from the 15-minute cache (`cached`), why reading stopped, sender pattern (`uouo…`), ms |
| `analysis` | next step, detected and unsure behaviours with probabilities, cues, missing answers, friction distributions, tone, stance groups offered, ms, requests and cost so far; or skipped / failed with the reason |
| `goal` | stance id, own goal or not, goal version, length of the user's details, whether the details are checked, fine-tune switches, required items (template wording only; the item that quotes the user's details is left out), number of avoids |
| `candidates` | per reply: label, length, state, total, G and E with level probabilities, every check with its probability and outcome; draft and check ms; requests and cost |
| `error` | step, error kind, HTTP status; a detail sentence only when it is Jev's own wording (no HTTP status, or a 200 that could not be used). A provider's error text is never logged: it can quote the request |
| `chat_change` | how many new messages from them, if countable; whether another chat is on screen |
| `crash` | an unexpected exception in background work: its class and where in Jev's code it was thrown |
| `action` | identify (known chat or not), remember_scene, fill (filled, asked to replace, refused other chat, refused unreadable), copy (and whether it was a fallback), use anyway, read again, read further, retry, recheck, cancel, resume (kept, chat moved on, other chat), close |

Never recorded: message text, the contact's name, reply text, the goal the user
typed, the API key, the chat identity hash. `AssistantSessionTest.diagnosticsCarryNoChatOrReplyText`
checks this.

## How a user reports a problem

- One round: in the panel tap **Goal & analysis**, then **Report a problem**. The report
  names that round. Ticking **Include goal and replies** adds the goal line and
  the drafted replies (not the chat). The tick resets every round.
- Everything recent: Jev app › **Diagnostics** › **Share report**.

Both open the Android share sheet with a Markdown report (versions, settings,
recent events in a `jsonl` block). Paste it into a GitHub issue using the
"Bug report (WhatsApp assistant)" template. Nothing is uploaded unless the user
shares it. The log can be turned off or cleared in Settings.

## Reading a report

Group lines by `round`. The usual path is `round → read → analysis → goal →
candidates → action`. Typical questions:

- Wrong reading of the chat: look at `read.senders` and `read.latestTurn`.
- Odd stance options: `analysis.detected`, `unsure`, `next`.
- Odd replies: `goal.switches` (a fine-tune left on), `candidates.replies[].hard`
  (which check blocked or flagged a reply).
- Slow or costly: `ms`, `draftMs`, `checkMs`, `requests`, `cost`.
- Fill problems: `action` with `name=fill`.
