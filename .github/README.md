<div align="center">

<img src="assets/images/logo.png" width="120" alt="jev-chat" />

# jev-chat

### The chat decision assistant

**Read them first, then reply. Before you answer, Jev works out what the other person really means, how risky the moment is and how to respond, then drafts replies you can fill in with one tap. Whether to send is always up to you.**

[![Stars](https://img.shields.io/github/stars/jev-chat/jev-chat-jarvis?style=flat-square&logo=github&label=Stars)](https://github.com/jev-chat/jev-chat-jarvis/stargazers)
[![Platform](https://img.shields.io/badge/platform-Android%20%7C%20Windows%20%7C%20macOS%20%7C%20iOS-lightgrey?style=flat-square)](#download--installation)
[![Global edition](https://img.shields.io/badge/Global%20edition-v0.1.0-25D366?style=flat-square)](../global/CHANGELOG.md)
[![Chinese edition](https://img.shields.io/badge/Chinese%20edition-v1.7-1f6feb?style=flat-square)](../cn/CHANGELOG.md)
[![License](https://img.shields.io/github/license/jev-chat/jev-chat-jarvis?style=flat-square)](../LICENSE)

### 🌐 Official website: **[chatjevs.com](https://chatjevs.com)**

English | [简体中文](README.zh-CN.md) | [Tiếng Việt](README.vi.md) | [Releases](https://github.com/jev-chat/jev-chat-jarvis/releases)

**[Download](#download--installation) · [Products](#products) · [How It Works](#how-it-works) · [Privacy](#privacy-and-risk) · [Community](#community-and-feedback)**

</div>

## ❤️Sponsors

> [Interested in sponsoring the project?](#community-and-feedback)

<details open>
<summary>Show or hide sponsors</summary>

<table>
<tr>
<td width="240" align="center"><a href="https://open.bocha.cn"><img src="assets/images/sponsors/bocha.png" alt="Bocha" width="200"></a></td>
<td>Thanks to <b>Bocha</b> for sponsoring this project! Bocha is a search engine for AI, giving your applications access to information from across the web with clean, accurate, high-quality results. Its services include the Web Search API, Bocha Jev API, and other search and model APIs. <a href="https://open.bocha.cn">open.bocha.cn</a></td>
</tr>
<tr>
<td width="240" align="center"><a href="https://faka.rainlanguage.top"><img src="assets/images/sponsors/xiaoyou.png" alt="Xiaoyou Store" width="200"></a></td>
<td>Thanks to <b>Xiaoyou Store</b> for sponsoring this project! Xiaoyou Store sells digital products and account services, with a selection available to users of this project. <a href="https://faka.rainlanguage.top">Visit the store</a>.</td>
</tr>
<tr>
<td width="240" align="center"><a href="https://agent.ai-tools.cn"><img src="assets/images/sponsors/vytal.jpg" alt="Vytal" width="200"></a></td>
<td>Thanks to <b>Vytal</b> for sponsoring this project! Vytal is an AI video workflow platform with reusable workflows for batch production, making video creation more accessible to content creators, training providers, and small teams. <a href="https://agent.ai-tools.cn">Visit Vytal</a>.</td>
</tr>
</table>

</details>

## Why jev-chat?

When a message lands, the hard part is not typing. It is working out what the other person really means: are they upset, testing you, or just chatting? Most AI tools skip that step and go straight to writing a reply.

**jev-chat** judges first. Its Jev judgment model reads the conversation and works out the other person's intent, the risk in the moment and the best way to respond. Only then does it draft replies, and it fills the one you pick into the message box.

- **Judge before writing** — intent, risk, what they need and the best action come before any draft
- **Checked, ranked replies** — every draft is checked against that judgment and ranked, so the best fit comes first
- **Works where you chat** — WhatsApp, QQ, Feishu and X on Android; chat windows on Windows and macOS; any app through the iOS keyboard
- **You press send** — jev-chat only fills the input box and never sends on its own
- **Your keys, no server of ours** — requests go only to the model provider you configure, with your own key
- **Open source** — MIT licensed, five apps across Android, Windows, macOS and iOS

## Products

| Platform | Product | How you use it | Status |
| :--- | :--- | :--- | :--- |
| Android | [Jev for WhatsApp](../global/README.md) (global edition) | Overlay on WhatsApp chats in English | Preview v0.1.0 |
| Android | [Jev Chat Assistant](../cn/README.en.md) (Chinese edition) | Overlay on QQ, Feishu, X and WhatsApp | v1.7 |
| Windows | [Jev for Windows](https://github.com/jev-chat/jev-chat-windows) | Sits beside your chat window and reads it with screenshots and on-device OCR | Released |
| macOS | [Jev for macOS](https://github.com/jev-chat/jev-chat-jarvis-mac) | Overlay that reads the chat on screen and judges with a local model | Released (Apple Silicon) |
| iOS | [Jev Keyboard](https://github.com/jev-chat/jev-chat-jarvis-ios) | A custom keyboard: copy a message and see intent, risk and replies on the keyboard | Source only, build it yourself |

Both Android editions live in this repository. The other platforms have their own repositories.

## Screenshots

| Global edition · analysis | Global edition · scored replies | Chinese edition · overlay |
| :---: | :---: | :---: |
| <img src="../global/docs/images/states/02-decide.png" width="230" alt="Global edition · analysis" /> | <img src="../global/docs/images/states/04-results.png" width="230" alt="Global edition · scored replies" /> | <img src="assets/images/overlay.png" width="230" alt="Chinese edition · overlay" /> |

## Download & Installation

### System Requirements

- **Android**: Android 11 or later (the Chinese edition needs an ARM64 phone)
- **Windows**: Windows 10 1903 or later, or Windows 11
- **macOS**: macOS 13 or later on Apple Silicon
- **iOS**: build it yourself with Xcode

### Android Users

| | Global edition | Chinese edition |
| :--- | :--- | :--- |
| For | Chats in English on WhatsApp | Chats in Chinese on QQ, Feishu, X and WhatsApp |
| Download | [jev-whatsapp-v0.1.0-release.apk](https://github.com/jev-chat/jev-chat-jarvis/releases/download/global-v0.1.0/jev-whatsapp-v0.1.0-release.apk) | [jev-assistant-v1.7-release.apk](https://raw.githubusercontent.com/jev-chat/jev-chat-jarvis/main/cn/apk/jev-assistant-v1.7-release.apk) |
| Guide | [Global edition README](../global/README.md) | [Chinese edition README](../cn/README.en.md) |

Both can be installed on the same phone. After installing, open the app and follow its guide to turn on the accessibility service and the overlay permission. The global edition is built by [@smgonthebeat](https://github.com/smgonthebeat).

### Windows Users

Download the latest version from the [Releases](https://github.com/jev-chat/jev-chat-windows/releases) page. See the [project README](https://github.com/jev-chat/jev-chat-windows) for setup.

### macOS Users

Download the latest version from the [Releases](https://github.com/jev-chat/jev-chat-jarvis-mac/releases) page. Apple Silicon only. See the [project README](https://github.com/jev-chat/jev-chat-jarvis-mac) for setup.

### iOS Users

There is no App Store build yet. Build the keyboard from [source](https://github.com/jev-chat/jev-chat-jarvis-ios) with Xcode.

## How It Works

1. **Read.** Jev reads the conversation on your screen: through the accessibility service on Android, the chat window on desktop (screenshots with on-device OCR, or system text APIs), and the message you copy on iOS. It does not modify the chat app or log in to it.
2. **Judge first.** Before any reply is written, a judgment model works out what the other person really wants, how risky the moment is and the best way to respond.
3. **Draft, check, rank.** Jev drafts replies, checks them against that judgment and ranks them. Tap one to fill it into the message box. You press send.

## Privacy and Risk

- Android: chat text, plus any background you turn on, goes only to the model provider you configure, with your own key. We run no server and never receive your chats. Screenshots used for OCR never leave the phone.
- Windows, macOS and iOS: see each project's README for how it handles data.
- Privacy policies (Android): [global edition](../global/PRIVACY.md) · [Chinese edition](../cn/PRIVACY.md)

> **Use at your own risk:** Using Jev inside third-party apps such as QQ, Feishu, X, or WhatsApp may not comply with those apps' terms of service, and your account could be restricted or banned. Decide for yourself whether to use it.

## Community and Feedback

- Problems with the global edition: [report them with the WhatsApp bug template](https://github.com/jev-chat/jev-chat-jarvis/issues/new?template=whatsapp-assistant-bug.md).
- Anything else: [open an issue](https://github.com/jev-chat/jev-chat-jarvis/issues).
- Pull requests are welcome. For a new feature, open an issue first so we can talk it through.
- If Jev helps you, a **Star** on this repository helps us keep going.

**Please get in touch by messaging the WeChat official account.** Use it for partnerships, sponsorships or feedback. Messages sent elsewhere may be missed.

<p align="center"><img src="assets/images/mp-qr.png" width="180" alt="WeChat official account QR code" /></p>

We'd love to hear what you actually need. Which chat app would you most like to use Jev with? What should it pick up on, how should it alert you, and what should it never touch? Send your thoughts to the official account.

## Star History

[![Star History Chart](https://api.star-history.com/svg?repos=jev-chat/jev-chat-jarvis&type=Date)](https://www.star-history.com/#jev-chat/jev-chat-jarvis&Date)

## Copyright and License

Copyright © 2026 Finderchangchang and the jev-chat contributors. The code is available under the [MIT License](../LICENSE). See also [NOTICE](../NOTICE). Contributors are listed in [CONTRIBUTORS](CONTRIBUTORS.md).

- **Commercial use is allowed:** Individuals and companies may use, modify, redistribute, or integrate the code into their own products without payment or prior permission.
- **Attribution is required:** Keep LICENSE and NOTICE when distributing or using the project commercially, and credit the source in your product's About page, documentation, or release page. Suggested wording: `Based on Jev Chat Assistant (https://github.com/jev-chat/jev-chat-jarvis)`.
- Do not use the names "Jev Chat Assistant" ("Jev 聊天助手") or "jev-chat", or the domain chatjevs.com, to imply that your product was made or endorsed by the original authors.

**Disclaimer:** This project only processes conversations on your own device that you are authorized to view. Follow the terms of service of QQ, X, Feishu, WhatsApp, and any other apps you use, as well as applicable local laws and regulations. The authors accept no responsibility for the consequences of its use.
