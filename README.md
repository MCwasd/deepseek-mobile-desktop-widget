# DeepSeek Dashboard Widget 📊

> 实时查看 DeepSeek API 余额、Token 消耗、缓存命中率的 Android 桌面小组件
> Real-time DeepSeek API dashboard — balance, token usage, cache hits — right on your Android home screen

---

**English** · [中文](#中文)

---

## Features

| English | 中文 |
|---------|------|
| Real-time balance display (in CNY) | 实时显示账户余额（元） |
| Today's cost & monthly cost | 今日花费 & 本月花费 |
| Token consumption (input / output) | Token 消耗（输入 / 输出） |
| Cache hit tokens | 缓存命中 Token 数 |
| Click to refresh manually | 点击小组件一键刷新 |
| Auto-refresh every 30 minutes | 30 分钟自动定时刷新 |
| Dark theme widget | 暗色风格 UI |
| Secure API key storage (AES-256-GCM encrypted) | API Key 加密存储（AES-256-GCM） |
| Works with any launcher | 兼容所有 Android 启动器 |

## Screenshots

| Widget preview | Config screen |
|:---:|:---:|
| ![Widget](screenshots/widget.png) | ![Config](screenshots/config.png) |

## Quick Start

1. **Download** the latest APK from [Releases](https://github.com/MCwasd/deepseek-mobile-desktop-widget/releases) or [Actions](https://github.com/MCwasd/deepseek-mobile-desktop-widget/actions)
2. **Install** on your Android phone (Android 8.0+)
3. **Long press** home screen → **Widgets** → find **DeepSeek Dashboard**
4. **Enter** your DeepSeek API Key
5. **Done!** The widget will show your usage data

> 💡 Get your API Key at [platform.deepseek.com](https://platform.deepseek.com)

## APIs Used

- `GET https://api.deepseek.com/user/balance` — Balance inquiry
- `GET https://platform.deepseek.com/api/v0/users/get_user_summary` — Usage summary

Both authenticated via `Authorization: Bearer <API_KEY>`.

## Tech Stack

- **Language:** Kotlin
- **Min SDK:** 26 (Android 8.0)
- **Target SDK:** 34
- **Architecture:** AppWidgetProvider + WorkManager
- **HTTP:** OkHttp 4.12
- **Encryption:** AndroidX Security (EncryptedSharedPreferences)
- **Build:** Gradle 8.2 + AGP 8.2.0

## Build from Source

```bash
git clone https://github.com/MCwasd/deepseek-mobile-desktop-widget.git
cd deepseek-mobile-desktop-widget
./gradlew assembleRelease
# APK output: app/build/outputs/apk/release/
```

## Project Structure

```
deepseek-mobile-desktop-widget/
├── app/
│   ├── src/main/java/com/tiramisu/deepseekwidget/
│   │   ├── DeepSeekApiClient.kt     — HTTP API client
│   │   ├── DeepSeekData.kt          — Data models
│   │   ├── DeepSeekWidget.kt        — Widget provider
│   │   ├── DeepSeekWidgetConfig.kt  — Config activity
│   │   └── WidgetUpdateWorker.kt    — Background updater
│   └── src/main/res/
│       ├── layout/                  — Widget & config layouts
│       ├── values/                  — Strings, colors, themes
│       ├── drawable/                — Background & icons
│       └── xml/                     — Widget metadata
├── build.gradle.kts
└── README.md
```

## Privacy

- API Key is stored **locally** on your device (EncryptedSharedPreferences)
- All API calls go **directly** to DeepSeek's official servers
- **No** data is sent to any third party

## License

MIT

---

## 中文

### 功能

在手机桌面实时监控 DeepSeek API 的使用情况：

- 💰 **余额** — 显示总余额、赠送金额、充值金额
- 📊 **今日花费** — 当天 Token 消耗和费用
- 📈 **本月累计** — 月度用量汇总
- 🎯 **缓存命中** — 缓存命中的 Token 数量
- 🔄 **自动刷新** — 每 30 分钟更新一次
- 👆 **手动刷新** — 点击小组件立即刷新
- 🔒 **安全存储** — API Key 用 AES-256-GCM 加密存储

### 快速开始

1. 从 [Releases](https://github.com/MCwasd/deepseek-mobile-desktop-widget/releases) 下载最新的 APK
2. 安装到 Android 手机（需要 Android 8.0+）
3. 长按桌面空白处 → 添加小组件 → 选择 **DeepSeek 仪表盘**
4. 输入你的 DeepSeek API Key
5. 保存后就看到数据了！

> 💡 在 [platform.deepseek.com](https://platform.deepseek.com) 获取 API Key

### 技术栈

| 组件 | 选型 |
|------|------|
| 语言 | Kotlin |
| 最低支持 | Android 8.0 (API 26) |
| 目标版本 | Android 14 (API 34) |
| 小组件框架 | AppWidgetProvider |
| 定时刷新 | WorkManager |
| 网络请求 | OkHttp 4.12 |
| 数据解析 | Gson |
| 加密存储 | EncryptedSharedPreferences |
| 构建 | Gradle 8.2 + AGP 8.2.0 |

### 自行编译

```bash
git clone https://github.com/MCwasd/deepseek-mobile-desktop-widget.git
cd deepseek-mobile-desktop-widget
./gradlew assembleRelease
# APK 在 app/build/outputs/apk/release/
```

### 隐私说明

- API Key **仅存储在手机本地**，使用系统级加密
- API 请求 **直接发送** 到 DeepSeek 官方服务器
- **不收集** 任何个人信息
- **不自建** 后端或代理服务器

### 许可证

MIT
# TUN proxy test
