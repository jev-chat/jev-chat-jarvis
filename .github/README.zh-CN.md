<div align="center">

<img src="assets/images/logo.png" width="140" alt="Jev 聊天助手" />

# Jev 聊天助手

**回消息之前，先看懂对方。**

Jev 读取你屏幕上的聊天，判断对方的真实意图和该怎么回，起草几条回复让你挑，再把你选的那条填进输入框。发不发，由你决定。

[![Stars](https://img.shields.io/github/stars/jev-chat/jev-chat-jarvis?style=flat-square&logo=github&label=Stars)](https://github.com/jev-chat/jev-chat-jarvis/stargazers)
[![海外版](https://img.shields.io/badge/海外版-v0.1.0-25D366?style=flat-square)](../global/CHANGELOG.md)
[![国内版](https://img.shields.io/badge/国内版-v1.7-1f6feb?style=flat-square)](../cn/CHANGELOG.md)
[![License](https://img.shields.io/github/license/jev-chat/jev-chat-jarvis?style=flat-square)](../LICENSE)

[官网](https://chatjevs.com) · [下载](#下载) · [安装与设置指南](https://chatjevs.com/guides/android-setup.html) · [候选回复使用建议](https://chatjevs.com/guides/review-ai-replies.html) · [Releases](https://github.com/jev-chat/jev-chat-jarvis/releases)

[English](README.md) · **简体中文** · [Tiếng Việt](README.vi.md)

</div>

## 它怎么工作

1. **读取**：通过安卓无障碍服务读取屏幕上正在显示的对话。不改聊天软件，不登录它的账号，不读它的数据库。
2. **先判断**：写回复之前，判断模型先看清对方真正想要什么、这句话风险有多大、怎么回最合适。
3. **起草、检查、排序**：按判断结果起草几条回复，检查后排好序。点一下填进输入框，发送键由你自己按。

<table align="center">
<tr>
<td align="center"><img src="../global/docs/images/states/02-decide.png" width="230" alt="海外版：对方要什么、你想怎么回" /><br/><sub>海外版：对方要什么、你想怎么回</sub></td>
<td align="center"><img src="../global/docs/images/states/04-results.png" width="230" alt="海外版：两条回复，按 5 分制打分" /><br/><sub>海外版：两条回复，按 5 分制打分</sub></td>
<td align="center"><img src="assets/images/overlay.png" width="230" alt="国内版：危险等级、对方意图、排好序的 3 条候选" /><br/><sub>国内版：危险等级、对方意图、排好序的 3 条候选</sub></td>
</tr>
</table>

## 下载

| | Jev for WhatsApp（海外版） | Jev 聊天助手（国内版） |
| :--- | :---: | :---: |
| 适合 | 用英文聊天 | 用中文聊天 |
| 支持的 App | WhatsApp | QQ、飞书、X、WhatsApp |
| 界面语言 | 英文 | 中文 |
| 下载 | [**v0.1.0 APK**](https://github.com/jev-chat/jev-chat-jarvis/releases/download/global-v0.1.0/jev-whatsapp-v0.1.0-release.apk) | [**v1.7 APK**](https://raw.githubusercontent.com/jev-chat/jev-chat-jarvis/main/cn/apk/jev-assistant-v1.7-release.apk) |
| 文档 | [说明](../global/README.zh-CN.md) · [更新日志](../global/CHANGELOG.md) | [使用说明](../cn/README.md) · [更新日志](../cn/CHANGELOG.md) |

两个版本都需要 Android 11 及以上，可以装在同一台手机上。海外版由 [@smgonthebeat](https://github.com/smgonthebeat) 开发。另有 [Windows 版](https://github.com/jev-chat/jev-chat-windows) · [macOS 版](https://github.com/jev-chat/jev-chat-jarvis-mac)。

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

## 隐私与风险

- 聊天文字和你开启的背景信息，只发给你自己配置的模型服务商，用的是你自己的密钥。作者不运营服务器，收不到你的聊天。
- 国内版有些 App 靠手机本地 OCR 识别，截图不出手机。
- 隐私政策：[海外版](../global/PRIVACY.md) · [国内版](../cn/PRIVACY.md)

> **使用风险**：在 QQ、飞书、X、WhatsApp 等第三方 App 里使用本助手，可能不符合该 App 的用户协议，账号有被限制或封禁的风险，请自行判断是否使用。

## 交流群 / 需求收集

- 海外版的问题：用 [WhatsApp 问题模板](https://github.com/jev-chat/jev-chat-jarvis/issues/new?template=whatsapp-assistant-bug.md) 提 issue。
- 其他问题：[提 issue](https://github.com/jev-chat/jev-chat-jarvis/issues)，或者公众号私信。
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

## 版权与许可

Copyright © 2026 Finderchangchang 与 jev-chat 贡献者。代码以 [MIT](../LICENSE) 协议开源，另见 [NOTICE](../NOTICE)。 贡献者名单见 [CONTRIBUTORS](CONTRIBUTORS.md)。

- **可以商用**：个人和公司都可以使用、修改、再分发，或集成进自己的产品，不需要付费或事先授权。
- **必须注明出处**：分发或商用时保留 LICENSE 与 NOTICE，并在产品「关于」页、说明文档或发布页写明来源。推荐写法：`基于 Jev 聊天助手（https://github.com/jev-chat/jev-chat-jarvis）二次开发`。
- 不要用「Jev 聊天助手」「jev-chat」名称或 chatjevs.com 域名暗示由原作者出品或背书。

**隐私与免责声明**：触发分析时，聊天文字和启用的背景信息会发送到你自行配置的第三方模型服务商；截图仅在本机 OCR。请阅读[隐私政策](../cn/PRIVACY.md)以及所选服务商的政策，并遵守 QQ、X、飞书、WhatsApp 等软件的用户协议与当地法律法规；因违反第三方 App 用户协议导致的账号限制等后果由使用者自行承担。作者不对第三方服务商的数据处理行为或使用后果负责。
