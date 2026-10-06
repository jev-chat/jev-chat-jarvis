<div align="center">

<img src="assets/images/logo.png" width="140" alt="Jev Chat Assistant" />

# Jev Chat Assistant

**Read them first, then reply.**

Jev reads the chat on your screen, works out what the other person means and how to respond, and drafts replies for you to pick from. It fills your choice into the message box. Whether to send is up to you.

[![Stars](https://img.shields.io/github/stars/jev-chat/jev-chat-jarvis?style=flat-square&logo=github&label=Stars)](https://github.com/jev-chat/jev-chat-jarvis/stargazers)
[![Global edition](https://img.shields.io/badge/Global%20edition-v0.1.0-25D366?style=flat-square)](../global/CHANGELOG.md)
[![Chinese edition](https://img.shields.io/badge/Chinese%20edition-v1.7-1f6feb?style=flat-square)](../cn/CHANGELOG.md)
[![License](https://img.shields.io/github/license/jev-chat/jev-chat-jarvis?style=flat-square)](../LICENSE)

[Website](https://chatjevs.com) · [Download](#download) · [Releases](https://github.com/jev-chat/jev-chat-jarvis/releases)

**English** · [简体中文](README.zh-CN.md) · [Tiếng Việt](README.vi.md)

</div>

## How It Works

1. **Read.** Jev reads the conversation shown on your screen through Android's accessibility service. It does not modify the chat app, log in to it or read its database.
2. **Judge first.** Before any reply is written, a judgment model works out what the other person really wants, how risky the moment is and the best way to respond.
3. **Draft, check, rank.** Jev drafts replies, checks them against that judgment and ranks them. Tap one to fill it into the message box. You press send.

<table align="center">
<tr>
<td align="center"><img src="../global/docs/images/states/02-decide.png" width="230" alt="Global edition: what they want, then your options" /><br/><sub>Global edition: what they want, then your options</sub></td>
<td align="center"><img src="../global/docs/images/states/04-results.png" width="230" alt="Global edition: two replies, scored out of 5" /><br/><sub>Global edition: two replies, scored out of 5</sub></td>
<td align="center"><img src="assets/images/overlay.png" width="230" alt="Chinese edition: risk level, intent and three ranked replies" /><br/><sub>Chinese edition: risk level, intent and three ranked replies</sub></td>
</tr>
</table>

## Download

| | Jev for WhatsApp (global edition) | Jev Chat Assistant (Chinese edition) |
| :--- | :---: | :---: |
| Best for | Chats in English | Chats in Chinese |
| Chat apps | WhatsApp | QQ, Feishu, X, WhatsApp |
| App language | English | Chinese |
| Download | [**v0.1.0 APK**](https://github.com/jev-chat/jev-chat-jarvis/releases/download/global-v0.1.0/jev-whatsapp-v0.1.0-release.apk) | [**v1.7 APK**](https://raw.githubusercontent.com/jev-chat/jev-chat-jarvis/main/cn/apk/jev-assistant-v1.7-release.apk) |
| Docs | [Guide](../global/README.md) · [Changelog](../global/CHANGELOG.md) | [Guide](../cn/README.en.md) · [Changelog](../cn/CHANGELOG.md) |

Both editions need Android 11 or later and can be installed side by side. The global edition is built by [@smgonthebeat](https://github.com/smgonthebeat). Also available: [Jev for Windows](https://github.com/jev-chat/jev-chat-windows) · [Jev for macOS](https://github.com/jev-chat/jev-chat-jarvis-mac).

## Sponsors

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

## Privacy and Risk

- Chat text, plus any background you turn on, goes only to the model provider you configure, with your own key. We run no server and never receive your chats.
- The Chinese edition reads some apps with on-device OCR. Screenshots never leave the phone.
- Privacy policies: [global edition](../global/PRIVACY.md) · [Chinese edition](../cn/PRIVACY.md)

> **Use at your own risk:** Using Jev inside third-party apps such as QQ, Feishu, X, or WhatsApp may not comply with those apps' terms of service, and your account could be restricted or banned. Decide for yourself whether to use it.

## Community and Feedback

- Problems with the global edition: [report them with the WhatsApp bug template](https://github.com/jev-chat/jev-chat-jarvis/issues/new?template=whatsapp-assistant-bug.md).
- Anything else: [open an issue](https://github.com/jev-chat/jev-chat-jarvis/issues).
- If Jev helps you, a **Star** on this repository helps us keep going.

**Please get in touch by messaging the WeChat official account.** Use it for partnerships, sponsorships or feedback. Messages sent elsewhere may be missed.

<p align="center"><img src="assets/images/mp-qr.png" width="180" alt="WeChat official account QR code" /></p>

We'd love to hear what you actually need. Which chat app would you most like to use Jev with? What should it pick up on, how should it alert you, and what should it never touch? Send your thoughts to the official account.

## Copyright and License

Copyright © 2026 Finderchangchang and the jev-chat contributors. The code is available under the [MIT License](../LICENSE). See also [NOTICE](../NOTICE). Contributors are listed in [CONTRIBUTORS](CONTRIBUTORS.md).

- **Commercial use is allowed:** Individuals and companies may use, modify, redistribute, or integrate the code into their own products without payment or prior permission.
- **Attribution is required:** Keep LICENSE and NOTICE when distributing or using the project commercially, and credit the source in your product's About page, documentation, or release page. Suggested wording: `Based on Jev Chat Assistant (https://github.com/jev-chat/jev-chat-jarvis)`.
- Do not use the names "Jev Chat Assistant" ("Jev 聊天助手") or "jev-chat", or the domain chatjevs.com, to imply that your product was made or endorsed by the original authors.

**Disclaimer:** This project only processes conversations on your own device that you are authorized to view. Follow the terms of service of QQ, X, Feishu, WhatsApp, and any other apps you use, as well as applicable local laws and regulations. The authors accept no responsibility for the consequences of its use.
