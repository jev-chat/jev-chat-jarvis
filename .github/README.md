<div align="center">

<img src="assets/images/logo.png" width="150" alt="Jev Chat Assistant" />

# Jev Chat Assistant

**A conversation copilot for your phone. Jev works alongside your chat apps, helps you understand what the other person means, and suggests what to say next. Tap to fill in a reply. You decide whether to send it.**

[![Stars](https://img.shields.io/github/stars/jev-chat/jev-chat-jarvis?style=flat-square&logo=github&label=Stars)](https://github.com/jev-chat/jev-chat-jarvis/stargazers)
[![Forks](https://img.shields.io/github/forks/jev-chat/jev-chat-jarvis?style=flat-square&logo=github&label=Forks)](https://github.com/jev-chat/jev-chat-jarvis/forks)
[![Global edition](https://img.shields.io/badge/Global%20edition-v0.1.0-25D366?style=flat-square)](../global/CHANGELOG.md)
[![Version](https://img.shields.io/badge/Chinese%20edition-v1.7-1f6feb?style=flat-square)](../cn/CHANGELOG.md)
[![Android](https://img.shields.io/badge/Android-11%2B-3DDC84?style=flat-square&logo=android&logoColor=white)](../global/README.md#quick-start)
[![License](https://img.shields.io/github/license/jev-chat/jev-chat-jarvis?style=flat-square)](../LICENSE)

[Website](https://chatjevs.com) · [Releases](https://github.com/jev-chat/jev-chat-jarvis/releases) · Changelogs: [global](../global/CHANGELOG.md) · [Chinese](../cn/CHANGELOG.md) · [macOS](https://github.com/jev-chat/jev-chat-mac) · [Windows](https://github.com/jev-chat/jev-chat-windows)

**English** · [简体中文](README.zh-CN.md) · [Tiếng Việt](README.vi.md)

</div>

## Choose Your Edition

| Global edition · Android | Chinese edition · Android | Windows | macOS |
| :---: | :---: | :---: | :---: |
| Release coming soon | [Download v1.7 APK](https://raw.githubusercontent.com/jev-chat/jev-chat-jarvis/main/cn/apk/jev-assistant-v1.7-release.apk) | [Get Jev for Windows](https://github.com/jev-chat/jev-chat-windows/releases) | [Get Jev for macOS](https://github.com/jev-chat/jev-chat-jarvis-mac/releases) |
| English UI · WhatsApp | Chinese UI · QQ / Feishu / X / WhatsApp | Reply helper beside your chat window | Message intent overlay |
| Android 11+ · [Guide](../global/README.md) | Android 11+ · ARM64 · [Guide](../cn/README.en.md) | Windows 10 1903+ / 11 | macOS 13+ · Apple Silicon |

Both Android editions live in this repository and can be installed side by side on the same phone:

- **Global edition** ([`global/`](../global/), Jev for WhatsApp): built for English-speaking users by [@smgonthebeat](https://github.com/smgonthebeat). It reads the chat on screen, works out what the other person wants, drafts two replies, checks and scores them, and fills the one you pick into the message box. Message reading, the assessment question bank and the reply corpus are designed around how people chat outside China. WhatsApp comes first, with more chat apps to follow.
- **Chinese edition** ([`cn/`](../cn/)): built for Chinese-speaking users and the chat apps popular in China. It also works on WhatsApp, with a Chinese interface.

If Jev is useful to you, a **Star** on this repository helps us keep it going. You don't need to star or follow anything to download or use it.

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

## Screenshots

<table align="center">
<tr>
<td align="center"><img src="../global/docs/images/states/02-decide.png" width="230" alt="Global edition: what they want, then your options" /><br/><sub>Global edition: what they want, then your options</sub></td>
<td align="center"><img src="../global/docs/images/states/04-results.png" width="230" alt="Global edition: two replies, scored out of 5" /><br/><sub>Global edition: two replies, scored out of 5</sub></td>
<td align="center"><img src="assets/images/overlay.png" width="230" alt="Chinese edition: risk level, intent and three ranked replies" /><br/><sub>Chinese edition: risk level, intent and three ranked replies</sub></td>
</tr>
</table>

## Why Jev?

- **It assesses the conversation before drafting a reply.** Most tools simply ask a model to write a response. Jev first uses an assessment model to identify the other person's intent, gauge the risk, and decide whether a reply can wait. That assessment guides its suggestions.
- **It leaves your chat apps alone.** No hooking, modified app packages, access to app APIs or accounts, or database reads. Jev uses Android's accessibility service to read the conversation currently visible on your screen.
- **You're always in control of sending.** Jev only fills in the text field. It never sends messages automatically or interacts with transfers, red packets, or payment collection.

> **Use at your own risk:** Using Jev inside third-party apps such as QQ, Feishu, X, or WhatsApp may not comply with those apps' terms of service, and your account could be restricted or banned. Decide for yourself whether to use it.

## Community and Feedback

For the global edition, report problems with the [WhatsApp bug template](https://github.com/jev-chat/jev-chat-jarvis/issues/new?template=whatsapp-assistant-bug.md).

**Please get in touch by messaging the WeChat official account.** Use it for partnerships, sponsorships, feedback, trouble joining a group, or expired QR codes. Messages sent elsewhere may be missed.

<p align="center"><img src="assets/images/mp-qr.png" width="180" alt="WeChat official account QR code" /></p>

We'd love to hear what you actually need. Which chat app would you most like to use Jev with? What should it pick up on, how should it alert you, and what should it never touch? Send your thoughts to the official account.

<details>
<summary>Show community group QR codes. These groups are full or their QR codes have expired; message the official account for a current code.</summary>

<table align="center"><tr>
  <td align="center"><img src="assets/images/group-1.png" width="80" alt="Group 1" /><br/><sub>Group 1</sub></td>
  <td align="center"><img src="assets/images/group-2.png" width="80" alt="Group 2" /><br/><sub>Group 2</sub></td>
  <td align="center"><img src="assets/images/group-3.png" width="80" alt="Group 3" /><br/><sub>Group 3</sub></td>
  <td align="center"><img src="assets/images/group-4.png" width="80" alt="Group 4" /><br/><sub>Group 4</sub></td>
  <td align="center"><img src="assets/images/group-5.png" width="80" alt="Group 5" /><br/><sub>Group 5</sub></td>
  <td align="center"><img src="assets/images/group-6.png" width="80" alt="Group 6" /><br/><sub>Group 6</sub></td>
  <td align="center"><img src="assets/images/group-7.png" width="80" alt="Group 7" /><br/><sub>Group 7</sub></td>
  <td align="center"><img src="assets/images/group-8.png" width="80" alt="Group 8" /><br/><sub>Group 8</sub></td>
  <td align="center"><img src="assets/images/group-9.png" width="80" alt="Group 9" /><br/><sub>Group 9</sub></td>
</tr></table>

</details>

## Related Projects

Also part of the [jev-chat](https://github.com/jev-chat) organization:

- [Jev Chat Assistant for macOS](https://github.com/jev-chat/jev-chat-jarvis-mac): A read-only overlay that reads the screen, uses a small local model to assess intent and risk, and generates suggested replies based on conversation guidance.
- [Jev Chat Assistant for Windows](https://github.com/jev-chat/jev-chat-windows): A reply assistant that sits beside your chat window. Uses window screenshots and local offline OCR, with three suggested replies you can insert with a click. Sending is always manual.

See the privacy policies of the [global edition](../global/PRIVACY.md) and the [Chinese edition](../cn/PRIVACY.md) for details on what is read, where it is sent and stored, and how to delete it.

## Friend Links

<table>
<tr>
<td width="150" align="center"><a href="https://github.com/lanyijianke"><img src="assets/images/friends/lanyijianke.jpg" width="100" alt="Lanyi Jianke" /></a><br/><b>Lanyi Jianke</b></td>
<td>Senior AI expert and author; Volcengine Navigator KOL, Alibaba Cloud Agent Maker, and core WaytoAGI creator. Deeply experienced in software development, system architecture, and project management. Author of bestselling AI books including <i>Efficient Office Work with Doubao</i> and <i>Efficient Office Work with Kimi</i>; winner of JD Books' 2025 Super New Book award and 2025 CMPress Creation Star. He has contributed to AI standards and national-level reports, and has provided enterprise AI consulting and implementation for dozens of Fortune Global 100 companies.<br/><br/>GitHub: <a href="https://github.com/lanyijianke">@lanyijianke</a> · Email: <a href="mailto:lanyijianke@outlook.com">lanyijianke@outlook.com</a></td>
</tr>
</table>

## Copyright and License

Copyright © 2026 Finderchangchang and the jev-chat contributors. The code is available under the [MIT License](../LICENSE). See also [NOTICE](../NOTICE). Contributors are listed in [CONTRIBUTORS](CONTRIBUTORS.md).

- **Commercial use is allowed:** Individuals and companies may use, modify, redistribute, or integrate the code into their own products without payment or prior permission.
- **Attribution is required:** Keep LICENSE and NOTICE when distributing or using the project commercially, and credit the source in your product's About page, documentation, or release page. Suggested wording: `Based on Jev Chat Assistant (https://github.com/jev-chat/jev-chat-jarvis)`.
- Do not use the names "Jev Chat Assistant" ("Jev 聊天助手") or "jev-chat", or the domain chatjevs.com, to imply that your product was made or endorsed by the original authors.

**Disclaimer:** This project only processes conversations on your own device that you are authorized to view. Follow the terms of service of QQ, X, Feishu, WhatsApp, and any other apps you use, as well as applicable local laws and regulations. The authors accept no responsibility for the consequences of its use.
