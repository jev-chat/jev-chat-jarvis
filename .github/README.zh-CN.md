<div align="center">

<img src="assets/images/logo.png" width="120" alt="jev-chat" />

# jev-chat

### 聊天辅助决策工具

**回消息之前，先看懂对方。Jev 先判断对方真正的意思、这句话的风险和该怎么回，再起草几条回复，点一下就能填进输入框。发不发，永远由你决定。**

[![Stars](https://img.shields.io/github/stars/jev-chat/jev-chat-jarvis?style=flat-square&logo=github&label=Stars)](https://github.com/jev-chat/jev-chat-jarvis/stargazers)
[![Platform](https://img.shields.io/badge/platform-Android%20%7C%20Windows%20%7C%20macOS%20%7C%20iOS-lightgrey?style=flat-square)](#下载与安装)
[![License](https://img.shields.io/github/license/jev-chat/jev-chat-jarvis?style=flat-square)](../LICENSE)

### 🌐 官网：**[chatjevs.com](https://chatjevs.com)**

[English](README.md) | 简体中文 | [Tiếng Việt](README.vi.md) | [Releases](https://github.com/jev-chat/jev-chat-jarvis/releases)

**[下载](#下载与安装) · [产品一览](#产品一览) · [它怎么工作](#它怎么工作) · [隐私](#隐私与风险) · [交流群](#交流群--需求收集) · [安装指南](https://chatjevs.com/guides/android-setup.html)**

</div>

## ❤️赞助商

> [想出现在这里？](#交流群--需求收集)

<details open>
<summary>点击折叠</summary>

<table>
<tr>
<td width="240" align="center"><a href="https://open.bocha.cn"><img src="assets/images/sponsors/bocha.png" alt="博查" width="200"></a></td>
<td>感谢 <b>博查</b> 赞助了本项目！博查是一个给 AI 用的搜索引擎，让你的 AI 应用连接世界知识，获得干净、准确、高质量的搜索结果。提供 Web Search API、Bocha Jev API 等多种联网搜索和模型服务。<a href="https://open.bocha.cn">open.bocha.cn</a></td>
</tr>
<tr>
<td width="240" align="center"><a href="https://faka.rainlanguage.top"><img src="assets/images/sponsors/xiaoyou.png" alt="小优店铺" width="200"></a></td>
<td>感谢 <b>小优店铺</b> 赞助了本项目！小优店铺是一家数字商品与账号服务店铺，为本项目的用户提供选购渠道。<a href="https://faka.rainlanguage.top">点此前往</a>。</td>
</tr>
<tr>
<td width="240" align="center"><a href="https://agent.ai-tools.cn"><img src="assets/images/sponsors/vytal.jpg" alt="速创猫 Vytal" width="200"></a></td>
<td>感谢 <b>速创猫 Vytal</b> 赞助了本项目！速创猫 Vytal 是专业的 AI 视频工作流平台，提供可批量复用的视频工作流，降低内容制作门槛，服务内容创作者、培训机构及中小团队。<a href="https://agent.ai-tools.cn">点此前往</a>。</td>
</tr>
</table>

</details>

## 为什么用 jev-chat？

收到一条消息，难的往往不是打字，而是看懂对方：是生气了、在试探你，还是随口聊聊？大多数 AI 工具跳过这一步，直接帮你写回复。

**jev-chat** 先判断。Jev 判断模型读完对话，先看清对方的真实意图、这句话的风险、怎么回最合适，然后才起草回复，把你选的那条填进输入框。

- **先判断，再写字** — 意图、风险、对方需要什么、最佳做法，都在起草之前给出
- **回复经过检查和排序** — 每条候选都按判断结果检查并排序，最合适的排在最前
- **在你聊天的地方用** — Android 上支持 WhatsApp、QQ、飞书、X；电脑端挂在聊天窗口旁；iOS 用键盘，任何 App 都能用
- **发送键在你手里** — 只把回复填进输入框，从不自动发送
- **用你自己的密钥** — 请求只发给你配置的模型服务商，作者不运营服务器
- **开源** — MIT 协议，Android、Windows、macOS、iOS 共 5 个客户端

## 产品一览

| 端 | 产品 | 怎么用 | 状态 |
| :--- | :--- | :--- | :--- |
| Android | [Jev for WhatsApp](../global/README.zh-CN.md)（海外版） | 悬浮窗，用于 WhatsApp 英文聊天 | 预览版 v0.1.0 |
| Android | [Jev 聊天助手](../cn/README.md)（国内版） | 悬浮窗，用于 QQ、飞书、X、WhatsApp | v1.7 |
| Windows | [Jev 聊天助手 Windows 版](https://github.com/jev-chat/jev-chat-windows) | 挂在聊天窗口旁，靠窗口截图和本地文字识别读消息 | 已发布 |
| macOS | [Jev 聊天助手 macOS 版](https://github.com/jev-chat/jev-chat-jarvis-mac) | 悬浮窗，读屏幕上的聊天，用本地模型判断 | 已发布（Apple 芯片） |
| iOS | [Jev 键盘](https://github.com/jev-chat/jev-chat-jarvis-ios) | 自定义键盘：复制对方的消息，键盘上直接出意图、风险和候选回复 | 只有源码，需自行编译 |

两个 Android 版本在这个仓库里，其他端各有自己的仓库。

## 截图

| 海外版 · 分析 | 海外版 · 打分后的回复 | 国内版 · 悬浮窗 |
| :---: | :---: | :---: |
| <img src="../global/docs/images/states/02-decide.png" width="230" alt="海外版 · 分析" /> | <img src="../global/docs/images/states/04-results.png" width="230" alt="海外版 · 打分后的回复" /> | <img src="assets/images/overlay.png" width="230" alt="国内版 · 悬浮窗" /> |

## 下载与安装

### 系统要求

- **Android**：Android 11 及以上（国内版需要 ARM64 手机）
- **Windows**：Windows 10 1903 及以上，或 Windows 11
- **macOS**：macOS 13 及以上，仅支持 Apple 芯片
- **iOS**：需要用 Xcode 自行编译

### Android 用户

| | 海外版 | 国内版 |
| :--- | :--- | :--- |
| 适合 | 在 WhatsApp 上用英文聊天 | 在 QQ、飞书、X、WhatsApp 上用中文聊天 |
| 下载 | [jev-whatsapp-v0.1.0-release.apk](https://github.com/jev-chat/jev-chat-jarvis/releases/download/global-v0.1.0/jev-whatsapp-v0.1.0-release.apk) | [jev-assistant-v1.7-release.apk](https://raw.githubusercontent.com/jev-chat/jev-chat-jarvis/main/cn/apk/jev-assistant-v1.7-release.apk) |
| 说明 | [海外版说明](../global/README.zh-CN.md) | [国内版使用说明](../cn/README.md) |

两个版本可以装在同一台手机上。装好后打开 App，按引导开启无障碍服务和悬浮窗权限。海外版由 [@smgonthebeat](https://github.com/smgonthebeat) 开发。

### Windows 用户

到[发布页](https://github.com/jev-chat/jev-chat-windows/releases)下载最新版，安装说明见[项目说明](https://github.com/jev-chat/jev-chat-windows)。

### macOS 用户

到[发布页](https://github.com/jev-chat/jev-chat-jarvis-mac/releases)下载最新版，仅支持 Apple 芯片，说明见[项目说明](https://github.com/jev-chat/jev-chat-jarvis-mac)。

### iOS 用户

暂时没有 App Store 版本，需要用 Xcode 从[源码](https://github.com/jev-chat/jev-chat-jarvis-ios)自行编译键盘。

## 它怎么工作

1. **读取**：读取屏幕上正在显示的对话。Android 用无障碍服务，电脑端读聊天窗口（截图加本地文字识别，或系统文本接口），iOS 读你复制的消息。不改聊天软件，也不登录它的账号。
2. **先判断**：写回复之前，判断模型先看清对方真正想要什么、这句话风险有多大、怎么回最合适。
3. **起草、检查、排序**：按判断结果起草几条回复，检查后排好序。点一下填进输入框，发送键由你自己按。

## 隐私与风险

- Android：聊天文字和你开启的背景信息，只发给你自己配置的模型服务商，用的是你自己的密钥。作者不运营服务器，收不到你的聊天。用来识别文字的截图不出手机。
- Windows、macOS、iOS 怎么处理数据，见各自的项目说明。
- 隐私政策（Android）：[海外版](../global/PRIVACY.md) · [国内版](../cn/PRIVACY.md)

> **使用风险**：在 QQ、飞书、X、WhatsApp 等第三方 App 里使用本助手，可能不符合该 App 的用户协议，账号有被限制或封禁的风险，请自行判断是否使用。

## 交流群 / 需求收集

- 海外版的问题：用 [WhatsApp 问题模板](https://github.com/jev-chat/jev-chat-jarvis/issues/new?template=whatsapp-assistant-bug.md) 提 issue。
- 其他问题：[提 issue](https://github.com/jev-chat/jev-chat-jarvis/issues)，或者公众号私信。
- 欢迎提 PR。做新功能之前，请先开 issue 讨论一下。
- 如果项目对你有帮助，欢迎点右上角的 **Star**，支持后续维护。获取和使用无需先加星或关注。

**扫码关注公众号可查看项目更新；需要联系时请公众号私信。** 合作、赞助、反馈、进群失败、二维码过期，都走公众号私信，其它渠道不一定看得到。

<p align="center"><img src="assets/images/mp-qr.png" width="180" alt="公众号二维码" /></p>

想听真实需求：你在哪个聊天 App 上最想要这个副驾？希望它判断什么、怎么提示、什么绝对不能碰？公众号私信直接说。

<details>
<summary>点击展开交流群二维码（都已满或已过期，进群请公众号私信要新码）</summary>

<table align="center"><tr>
  <td align="center"><img src="assets/images/group-1.png" width="80" alt="1 群" /><br/><sub>1 群</sub></td>
  <td align="center"><img src="assets/images/group-2.png" width="80" alt="2 群" /><br/><sub>2 群</sub></td>
  <td align="center"><img src="assets/images/group-3.png" width="80" alt="3 群" /><br/><sub>3 群</sub></td>
  <td align="center"><img src="assets/images/group-4.png" width="80" alt="4 群" /><br/><sub>4 群</sub></td>
  <td align="center"><img src="assets/images/group-5.png" width="80" alt="5 群" /><br/><sub>5 群</sub></td>
  <td align="center"><img src="assets/images/group-6.png" width="80" alt="6 群" /><br/><sub>6 群</sub></td>
  <td align="center"><img src="assets/images/group-7.png" width="80" alt="7 群" /><br/><sub>7 群</sub></td>
  <td align="center"><img src="assets/images/group-8.png" width="80" alt="8 群" /><br/><sub>8 群</sub></td>
  <td align="center"><img src="assets/images/group-9.png" width="80" alt="9 群" /><br/><sub>9 群</sub></td>
</tr></table>

</details>

## 友情链接

<table>
<tr>
<td width="150" align="center"><a href="https://github.com/lanyijianke"><img src="assets/images/friends/lanyijianke.jpg" width="100" alt="蓝衣剑客" /></a><br/><b>蓝衣剑客</b></td>
<td>资深 AI 专家、作家，火山引擎领航 KOL、阿里云 Agent 创客、WaytoAGI 核心创作者。深耕软件开发、系统架构与项目管理，著有《豆包高效办公》《Kimi 高效办公》等畅销 AI 书籍，获京东图书 2025 年度超级新书、2025 机工创作之星；曾参与多项 AI 领域标准及国家级报告起草，为数十家世界百强企业提供企业级 AI 咨询与实施。<br/><br/>GitHub：<a href="https://github.com/lanyijianke">@lanyijianke</a> · 微信：lanyijianke1992</td>
</tr>
</table>

## Star 历史

[![Star History Chart](https://api.star-history.com/svg?repos=jev-chat/jev-chat-jarvis&type=Date)](https://www.star-history.com/#jev-chat/jev-chat-jarvis&Date)

## 版权与许可

Copyright © 2026 Finderchangchang 与 jev-chat 贡献者。代码以 [MIT](../LICENSE) 协议开源，另见 [NOTICE](../NOTICE)。 贡献者名单见 [CONTRIBUTORS](CONTRIBUTORS.md)。

- **可以商用**：个人和公司都可以使用、修改、再分发，或集成进自己的产品，不需要付费或事先授权。
- **必须注明出处**：分发或商用时保留 LICENSE 与 NOTICE，并在产品「关于」页、说明文档或发布页写明来源。推荐写法：`基于 Jev 聊天助手（https://github.com/jev-chat/jev-chat-jarvis）二次开发`。
- 不要用「Jev 聊天助手」「jev-chat」名称或 chatjevs.com 域名暗示由原作者出品或背书。

**隐私与免责声明**：触发分析时，聊天文字和启用的背景信息会发送到你自行配置的第三方模型服务商；截图仅在本机 OCR。请阅读[隐私政策](../cn/PRIVACY.md)以及所选服务商的政策，并遵守 QQ、X、飞书、WhatsApp 等软件的用户协议与当地法律法规；因违反第三方 App 用户协议导致的账号限制等后果由使用者自行承担。作者不对第三方服务商的数据处理行为或使用后果负责。
