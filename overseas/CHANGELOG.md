# Changelog: Jev for WhatsApp

## 0.1.0 (2026-10-03)

First public release. Android 11+, WhatsApp for Android (tested on 2.26.38.73),
one-to-one chats in English.

- Reads the open WhatsApp chat when you tap the bubble: up to 24 recent messages
  over several screens, with "Read further back" for more. Group chats are refused.
- Five scenes (Work, Romance, Friends, Family, General) with relationship types,
  remembered per chat under a salted hash.
- Analysis with the Jev model: behaviours, friction, tone and the next step from
  five hand-approved scene matrices.
- Stances with required items, avoids, commitments and an apology level by scene,
  relationship and stance; your own details become required items; fine-tune
  switches "Don't apologise", "No new promises", "Don't explain why".
- Two drafted replies, each checked (new commitments, going beyond the goal,
  unsupported facts, crossing a boundary, admitting fault, contradicting an earlier
  date or amount) and scored out of 5 from goal completion and delivery. Only a
  reply that passes every check can be "Top pick".
- A file sent on its own is answered from the messages around it.
- Fill writes the chosen reply into WhatsApp's message box after checking that the
  same chat is open. Jev never sends.
- Signed release APK in `apk/` (Android 11+, any CPU).
- README in English and Chinese, with the panel states rendered by the app.
- OpenRouter key stored encrypted with an Android Keystore key; no backups; a local
  diagnostic log without chat text, shareable as a bug report.
