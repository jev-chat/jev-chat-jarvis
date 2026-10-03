# Privacy: Jev for WhatsApp

This covers the Android app in this directory (`com.jev.overseas`). The Chinese app
in the repository root has its own [privacy policy](../PRIVACY.md).

The project has no server and collects nothing. The app talks to one service,
OpenRouter, with the API key you provide.

## What Jev reads

- Only WhatsApp (`com.whatsapp`), and only after you tap Jev's bubble.
- Only the chat that is open on screen. Jev scrolls that chat back to collect
  about 24 recent messages, or about twice as many if you tap "Read further back".
- The text of messages, who sent each one (you or them), and whether a row is a
  photo, document, voice note or other media. It does not open media.
- No screenshots, no other apps, no contact list, no WhatsApp database.

While WhatsApp is not on screen, the accessibility service only receives window
change events to show or hide the bubble.

## What leaves the phone

Each round sends HTTPS requests to `openrouter.ai`, which forwards them to the model
providers you configured (by default TypeSafe for the Jev model and DeepSeek for
drafting):

| Request | Contents |
|---|---|
| Analysis | The messages Jev read, the scene and relationship you chose |
| Drafting | The same messages, the analysis, your goal and any detail you typed |
| Checking | The messages, your goal, and each drafted reply |
| Key check | Only the key, to show your remaining credit |

These include the other person's messages. OpenRouter and the model providers
handle the data under their own policies:
[OpenRouter privacy](https://openrouter.ai/privacy). You can choose other models in
Settings › Models.

Nothing is sent anywhere else. There is no analytics, crash reporting or telemetry.
The only Android permission is `INTERNET`.

## What stays on the phone

In the app's private storage, excluded from cloud backup and device transfer:

- Your OpenRouter key, encrypted with an AES-GCM key held in the Android Keystore.
  The full key is never shown again after you save it; Settings shows "sk-or-…"
  and the last four characters.
- Your settings (models, spelling, bubble position, "Analyse when opened").
- For each chat you set up: the scene and relationship, stored under a SHA-256 hash
  of the chat with a random per-install salt. Not the contact's name.
- A diagnostic log of the latest 1000 events: ids, counts, scores, timings and error
  kinds. Never message text, names, replies, the goal you typed, the key or the
  chat hash. See [docs/DIAGNOSTICS.md](docs/DIAGNOSTICS.md). You can turn it off or
  clear it in Settings.

The chat text and the drafted replies are kept in memory for the current round
only (at most 15 minutes) and are not written to storage.

## What you control

- Jev reads nothing until you tap the bubble.
- "Forget remembered chats" deletes all remembered scenes.
- A bug report is created only when you tap "Report a problem" or "Share report",
  and goes only where you share it. It contains the diagnostic log; the goal and
  replies are added only if you tick "Include goal and replies".
- Removing the key, turning off the accessibility service or uninstalling the app
  stops everything. Uninstalling deletes all stored data.

## Sending

Jev never sends a message. "Fill" writes the chosen reply into WhatsApp's message
box after checking that the same chat is still open; you decide whether to send it.
