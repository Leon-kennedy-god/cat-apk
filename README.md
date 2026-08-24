# 喵喵助手 (MeowMeowAssistant)

> 逆向自 [QQMiaoAssistant](https://github.com/QiCaiJie114514/QQMiaoAssistant)（QQ 文本改写助手）的**通用版**：
> 同样的文本改写效果（替换规则 → 断句追加 → 随机颜文字），但**不再受限于 QQ**——
> 可在任意聊天软件（微信、Telegram、WhatsApp、钉钉、Discord、飞书、Slack、LINE 等）的输入框内生效。

## 与原版的差异（逆向改造点）

| 原版（仅 QQ 可用） | 喵喵助手（任意聊天软件） |
| --- | --- |
| 无障碍服务 `packageNames` 写死 `com.tencent.mobileqq` / `com.tencent.mobileqqi` | 不限定包名，监听所有应用；作用范围由应用内配置动态决定 |
| 输入框查找依赖硬编码 View ID `com.tencent.mobileqq:id/input` | 通用"可编辑节点"检索（`isEditable` / EditText 类名），不依赖任何 View ID |
| 发送按钮兜底依赖 QQ 专属 View ID `com.tencent.mobileqq:id/send_btn` | 通用发送关键词识别（发送/送出/send/submit…），可开关 |
| 无作用范围配置 | 新增目标应用白名单（留空 = 所有应用）与排除黑名单 |
| 无键盘保护 | 自动跳过输入法(IME)窗口与包名，绝不改写键盘候选区 |
| 无隐私保护 | 自动跳过密码框（`isPassword`） |
| 替换规则写死/半写死 | 全部配置界面可自定义（继承原版重构成果） |

## 功能

- **文本替换规则**：任意"原词=替换词"，每行一条、按顺序应用（支持 `=`、全角 `＝`、`→`）
- **断句追加**：按 `，,。！!？?` 分句，句末追加自定义文本（默认"喵"）
- **句末随机颜文字**：内置 50+ 猫咪颜文字，可自定义
- **两种处理模式**：标点触发（推荐）/ 实时处理
- **作用范围**：目标应用白名单（一键填入 QQ/微信/Telegram/WhatsApp/钉钉/Discord/飞书/Slack/LINE/Signal 包名）+ 排除黑名单
- **发送按钮兜底**：点击发送时做最后一次处理（实时模式下补上句末颜文字）
- 基于 Android 无障碍服务，纯原生 API，无第三方依赖

## 技术栈

- Android 原生 API（无障碍服务 AccessibilityService + SharedPreferences）
- Java 8 / Gradle 8.8 / AGP 8.4.2
- minSdk 23 / compileSdk 34

## 构建

```bash
./gradlew assembleDebug
```

产物：`app/build/outputs/apk/debug/app-debug.apk`

## 使用

1. 安装 APK（开启"允许未知来源"）
2. 打开应用 → 前往开启无障碍服务「喵喵助手（任意聊天软件文本改写）」
3. 配置作用范围：留空目标应用 = 所有应用；或点击预置按钮只选要改写的聊天软件
4. 配置替换规则 / 断句 / 颜文字 → 保存设置
5. 在任意聊天软件输入框中打字，触发规则自动改写

> 提示：默认会排除输入法、桌面启动器、系统设置等，防止误改写；密码框永不处理。

## 逆向说明

详见 [REVERSE.md](REVERSE.md)：原版限制来源、逐文件改造对照、通用化设计要点。

## 免责声明

本项目基于 [QQMiaoAssistant](https://github.com/QiCaiJie114514/QQMiaoAssistant)（AGPL-3.0）逆向重构，
仅供技术学习与交流。若涉及原著作权人权益，请联系原作者协商处理。
