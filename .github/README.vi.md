<div align="center">

<img src="assets/images/logo.png" width="140" alt="Jev Chat Assistant" />

# Jev Chat Assistant

**Đọc hiểu người kia trước, rồi mới trả lời.**

Jev đọc cuộc trò chuyện trên màn hình, hiểu người kia muốn gì và nên đáp thế nào, rồi soạn sẵn vài câu trả lời để bạn chọn. Câu bạn chọn được điền vào ô nhập; gửi hay không là do bạn. Có trên Android, Windows, macOS và iOS.

[English](README.md) · [简体中文](README.zh-CN.md) · **Tiếng Việt**

</div>

## ❤️Nhà tài trợ

> [Muốn xuất hiện ở đây?](#nhóm-trao-đổi--thu-thập-yêu-cầu)

<details open>
<summary>Nhấn để thu gọn</summary>

<table>
<tr>
<td width="240" align="center"><a href="https://open.bocha.cn"><img src="assets/images/sponsors/bocha.png" alt="博查" width="200"></a></td>
<td>Cảm ơn <b>博查</b> đã tài trợ cho dự án này! 博查 là một công cụ tìm kiếm dành cho AI, giúp ứng dụng AI của bạn kết nối với tri thức thế giới và tiếp cận kết quả tìm kiếm sạch, chính xác, chất lượng cao. Cung cấp Web Search API, Bocha Jev API cùng nhiều dịch vụ tìm kiếm trực tuyến và dịch vụ mô hình khác. <a href="https://open.bocha.cn">open.bocha.cn</a></td>
</tr>
<tr>
<td width="240" align="center"><a href="https://faka.rainlanguage.top"><img src="assets/images/sponsors/xiaoyou.png" alt="小优店铺" width="200"></a></td>
<td>Cảm ơn <b>小优店铺</b> đã tài trợ cho dự án này! 小优店铺 là một cửa hàng cung cấp sản phẩm số và dịch vụ tài khoản, cung cấp cho người dùng dự án một kênh mua sắm. <a href="https://faka.rainlanguage.top">Truy cập tại đây</a>.</td>
</tr>
<tr>
<td width="240" align="center"><a href="https://agent.ai-tools.cn"><img src="assets/images/sponsors/vytal.jpg" alt="速创猫 Vytal" width="200"></a></td>
<td>Cảm ơn <b>速创猫 Vytal</b> đã tài trợ cho dự án này! 速创猫 Vytal là nền tảng quy trình làm việc video chuyên nghiệp bằng AI, cung cấp quy trình video có thể tái sử dụng hàng loạt, giúp giảm rào cản sản xuất nội dung và phục vụ nhà sáng tạo nội dung, cơ sở đào tạo cùng các nhóm vừa và nhỏ. <a href="https://agent.ai-tools.cn">Truy cập tại đây</a>.</td>
</tr>
</table>

</details>

<div align="center">

[![Stars](https://img.shields.io/github/stars/jev-chat/jev-chat-jarvis?style=flat-square&logo=github&label=Stars)](https://github.com/jev-chat/jev-chat-jarvis/stargazers)
[![Bản quốc tế](https://img.shields.io/badge/Bản%20quốc%20tế-v0.1.0-25D366?style=flat-square)](../global/CHANGELOG.md)
[![Bản tiếng Trung](https://img.shields.io/badge/Bản%20tiếng%20Trung-v1.7-1f6feb?style=flat-square)](../cn/CHANGELOG.md)
[![License](https://img.shields.io/github/license/jev-chat/jev-chat-jarvis?style=flat-square)](../LICENSE)

[Trang web](https://chatjevs.com) · [Tải về](#tải-về) · [Releases](https://github.com/jev-chat/jev-chat-jarvis/releases)

</div>

## jev-chat là gì

jev-chat là công cụ hỗ trợ ra quyết định khi trò chuyện. Cốt lõi là mô hình đánh giá Jev: trước khi bạn trả lời, nó xác định người kia thật sự muốn gì, cuộc trò chuyện rủi ro đến đâu và bạn nên đáp thế nào, rồi mới soạn câu trả lời. Mọi ứng dụng jev-chat chỉ chuẩn bị câu trả lời; gửi hay không là do bạn.

| Nền tảng | Sản phẩm | Cách dùng | Trạng thái |
| :--- | :--- | :--- | :--- |
| Android | [Jev for WhatsApp](../global/README.md) (bản quốc tế) | Cửa sổ nổi cho các cuộc trò chuyện WhatsApp bằng tiếng Anh | Bản xem trước v0.1.0 |
| Android | [Jev Chat Assistant](../cn/README.vi.md) (bản tiếng Trung) | Cửa sổ nổi cho QQ, Feishu, X, WhatsApp | v1.7 |
| Windows | [Jev cho Windows](https://github.com/jev-chat/jev-chat-windows) | Đặt cạnh cửa sổ chat, đọc tin nhắn bằng ảnh chụp cửa sổ và OCR trên máy | Đã phát hành |
| macOS | [Jev cho macOS](https://github.com/jev-chat/jev-chat-jarvis-mac) | Cửa sổ nổi đọc cuộc trò chuyện trên màn hình, đánh giá bằng mô hình chạy trên máy | Đã phát hành (chip Apple) |
| iOS | [Bàn phím Jev](https://github.com/jev-chat/jev-chat-jarvis-ios) | Bàn phím tùy chỉnh: sao chép tin nhắn để xem ý định, rủi ro và câu trả lời ngay trên bàn phím | Chỉ có mã nguồn, cần tự build |

Hai phiên bản Android nằm trong kho mã này; các nền tảng khác có kho mã riêng.

## Cách hoạt động

1. **Đọc.** Jev đọc cuộc trò chuyện trên màn hình: qua dịch vụ trợ năng trên Android, qua cửa sổ chat trên máy tính (ảnh chụp kèm OCR trên máy, hoặc giao diện văn bản của hệ thống), và qua tin nhắn bạn sao chép trên iOS. Không sửa ứng dụng chat và không đăng nhập vào đó.
2. **Đánh giá trước.** Trước khi viết câu trả lời, mô hình đánh giá xác định người kia thật sự muốn gì, tình huống rủi ro đến đâu và nên đáp thế nào.
3. **Soạn, kiểm tra, xếp hạng.** Jev soạn vài câu trả lời, kiểm tra theo kết quả đánh giá rồi xếp hạng. Chạm vào một câu để điền vào ô nhập. Bạn tự nhấn gửi.

<table align="center">
<tr>
<td align="center"><img src="../global/docs/images/states/02-decide.png" width="230" alt="Bản quốc tế: người kia muốn gì và các lựa chọn của bạn" /><br/><sub>Bản quốc tế: người kia muốn gì và các lựa chọn của bạn</sub></td>
<td align="center"><img src="../global/docs/images/states/04-results.png" width="230" alt="Bản quốc tế: hai câu trả lời, chấm trên thang 5" /><br/><sub>Bản quốc tế: hai câu trả lời, chấm trên thang 5</sub></td>
<td align="center"><img src="assets/images/overlay.png" width="230" alt="Bản tiếng Trung: mức độ rủi ro, ý định và ba câu trả lời đã xếp hạng" /><br/><sub>Bản tiếng Trung: mức độ rủi ro, ý định và ba câu trả lời đã xếp hạng</sub></td>
</tr>
</table>

## Tải về

| Nền tảng | Tải về | Yêu cầu | Tài liệu |
| :--- | :--- | :--- | :--- |
| Android · bản quốc tế | [**APK v0.1.0**](https://github.com/jev-chat/jev-chat-jarvis/releases/download/global-v0.1.0/jev-whatsapp-v0.1.0-release.apk) | Android 11+ | [Hướng dẫn (tiếng Anh)](../global/README.md) · [Nhật ký thay đổi](../global/CHANGELOG.md) |
| Android · bản tiếng Trung | [**APK v1.7**](https://raw.githubusercontent.com/jev-chat/jev-chat-jarvis/main/cn/apk/jev-assistant-v1.7-release.apk) | Android 11+ · ARM64 | [Hướng dẫn](../cn/README.vi.md) · [Nhật ký thay đổi](../cn/CHANGELOG.md) |
| Windows | [Trang phát hành](https://github.com/jev-chat/jev-chat-windows/releases) | Windows 10 1903+ / 11 | [Dự án](https://github.com/jev-chat/jev-chat-windows) |
| macOS | [Trang phát hành](https://github.com/jev-chat/jev-chat-jarvis-mac/releases) | macOS 13+ · chip Apple | [Dự án](https://github.com/jev-chat/jev-chat-jarvis-mac) |
| iOS | Tự build từ mã nguồn | Xcode | [Dự án](https://github.com/jev-chat/jev-chat-jarvis-ios) |

Hai phiên bản Android có thể cài cùng lúc trên một điện thoại. Bản quốc tế do [@smgonthebeat](https://github.com/smgonthebeat) phát triển.

## Quyền riêng tư và rủi ro

- Android: nội dung trò chuyện và thông tin nền bạn bật chỉ được gửi tới nhà cung cấp mô hình do bạn cấu hình, bằng khóa của chính bạn. Chúng tôi không vận hành máy chủ nào và không nhận được cuộc trò chuyện của bạn. Ảnh chụp dùng cho OCR không rời khỏi điện thoại.
- Windows, macOS và iOS: xem README của từng dự án để biết cách xử lý dữ liệu.
- Chính sách quyền riêng tư (Android): [bản quốc tế](../global/PRIVACY.md) · [bản tiếng Trung](../cn/PRIVACY.md)

> **Rủi ro khi sử dụng:** Dùng trợ lý trong các ứng dụng bên thứ ba như QQ, 飞书, X hoặc WhatsApp có thể không phù hợp với thỏa thuận người dùng của ứng dụng đó, tài khoản có thể bị hạn chế hoặc khóa. Hãy tự cân nhắc trước khi sử dụng.

## Nhóm trao đổi / Thu thập yêu cầu

- Lỗi của bản quốc tế: [báo lỗi bằng mẫu WhatsApp](https://github.com/jev-chat/jev-chat-jarvis/issues/new?template=whatsapp-assistant-bug.md).
- Các vấn đề khác: [tạo issue](https://github.com/jev-chat/jev-chat-jarvis/issues).
- Nếu Jev hữu ích với bạn, hãy nhấn **Star** cho kho mã này để ủng hộ việc duy trì.

**Nếu cần liên hệ, hãy nhắn tin riêng qua tài khoản công khai chính thức trên WeChat.** Hợp tác, tài trợ hay phản hồi đều gửi qua đây; các kênh khác không bảo đảm nhận được.

<p align="center"><img src="assets/images/mp-qr.png" width="180" alt="Mã QR tài khoản công khai chính thức trên WeChat" /></p>

Muốn biết nhu cầu thật: bạn muốn trợ lý này nhất trong ứng dụng chat nào? Bạn muốn nó phân tích điều gì, hiển thị thế nào, và điều tuyệt đối nào không được chạm tới? Hãy nhắn tin riêng qua tài khoản công khai chính thức trên WeChat.

## Bản quyền và giấy phép

Copyright © 2026 Finderchangchang và những người đóng góp cho jev-chat. Mã nguồn được phát hành theo giấy phép mã nguồn mở [MIT](../LICENSE), xem thêm [NOTICE](../NOTICE). Danh sách người đóng góp: [CONTRIBUTORS](CONTRIBUTORS.md).

- **Có thể sử dụng thương mại**: cả cá nhân và công ty đều có thể sử dụng, chỉnh sửa, phân phối lại hoặc tích hợp vào sản phẩm của mình mà không cần trả phí hoặc xin phép trước.
- **Bắt buộc ghi nguồn**: khi phân phối hoặc sử dụng thương mại, phải giữ lại LICENSE và NOTICE, đồng thời ghi nguồn trong trang “Giới thiệu”, tài liệu hoặc trang phát hành của sản phẩm. Cách ghi được khuyến nghị: `Dựa trên Jev 聊天助手（https://github.com/jev-chat/jev-chat-jarvis） để phát triển tiếp`.
- Không dùng tên “Jev 聊天助手”, “jev-chat” hoặc tên miền chatjevs.com để gợi ý rằng sản phẩm do tác giả gốc phát hành hoặc chứng thực.

**Quyền riêng tư và miễn trừ trách nhiệm**: khi kích hoạt phân tích, nội dung trò chuyện và thông tin nền đang bật sẽ được gửi tới nhà cung cấp mô hình bên thứ ba do bạn tự cấu hình; ảnh chụp màn hình chỉ được OCR trên máy. Vui lòng đọc [chính sách quyền riêng tư](../cn/PRIVACY.md) cùng chính sách của nhà cung cấp đã chọn và tuân thủ điều khoản của QQ, X, 飞书, WhatsApp và các phần mềm khác cùng pháp luật và quy định địa phương. Tác giả không chịu trách nhiệm về hành vi xử lý dữ liệu hoặc hậu quả sử dụng của nhà cung cấp bên thứ ba.
