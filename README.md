# 喵喵助手 (MeowMeowAssistant)

> [!IMPORTANT]
> ## ⚠️ 衍生项目声明 · 本仓库仅为上游的「功能扩张」
>
> **本仓库（喵喵助手 / MeowMeowAssistant）是 [QQMiaoAssistant](https://github.com/QiCaiJie114514/QQMiaoAssistant)
> （QQ文本改写助手）的功能扩张（二次开发衍生）项目，不是独立原创软件。**
>
> - **上游仓库**：https://github.com/QiCaiJie114514/QQMiaoAssistant
> - **上游作者联系方式**：QQ **1670534177**（上游仓库内公开的联系方式，用于署名/下架等协商）
> - **本仓库做了什么**：仅在保持上游核心改写引擎一致的前提下，把「只能在 QQ 使用」扩张为
>   「可在任意聊天软件使用」——即删除了上游写死的 QQ 包名/View ID 限制，改为通用可编辑节点
>   检索与可配置的作用范围（详见下方「与原版的差异」）。
> - **授权**：AGPL-3.0（与上游一致，见 [LICENSE](LICENSE) 与 [NOTICE](NOTICE)）
> - **承诺**：若上游作者认为本仓库侵权，请通过上方联系方式或本仓库 Issues 联系，
>   将立即下架、署名或按要求整改。

---

> 逆向自 [QQMiaoAssistant](https://github.com/QiCaiJie114514/QQMiaoAssistant)（QQ 文本改写助手）的**功能扩张通用版**：
> 同样的文本改写效果（替换规则 → 断句追加 → 随机颜文字），但**不再受限于 QQ**——
> 可在任意聊天软件（微信、Telegram、WhatsApp、钉钉、Discord、飞书、Slack、LINE 等）的输入框内生效。

## 功能简介（全面）

喵喵助手是一款基于 **Android 无障碍服务（Accessibility Service）** 的文本改写工具：

- **文本替换规则**：任意"原词=替换词"，每行一条、按顺序应用（支持 `=`、全角 `＝`、`→`）
- **断句追加**：按 `，,。！!？?` 分句，句末追加自定义文本（默认"喵"）
- **句末随机颜文字**：内置 50+ 猫咪颜文字，可自定义
- **两种处理模式**：标点触发（推荐）/ 实时处理
- **高级设置（v1.1）**：仅处理聚焦输入框（防止误识别提示词/占位文字）+ 流式输入稳定防抖（语音输入时等待输入成型再改写，默认 800ms）
- **作用范围（功能扩张核心）**：目标应用白名单（一键填入 QQ/微信/Telegram/WhatsApp/钉钉/Discord/飞书/Slack/LINE/Signal 包名）+ 排除黑名单，留空 = 所有应用
- **发送按钮兜底**：点击聊天软件的发送按钮时做最后一次处理（实时模式下补上句末颜文字）
- **隐私与防误改**：自动跳过输入法(IME)窗口与包名、桌面启动器、系统设置；**密码框永不处理**
- 纯原生 Android API，**零第三方依赖**，仅 0.4MB

## 与原版的差异（功能扩张点）

| 原版（仅 QQ 可用） | 喵喵助手（任意聊天软件） |
| --- | --- |
| 无障碍服务 `packageNames` 写死 `com.tencent.mobileqq` / `com.tencent.mobileqqi` | 不限定包名，监听所有应用；作用范围由应用内配置动态决定 |
| 输入框查找依赖硬编码 View ID `com.tencent.mobileqq:id/input` | 通用"可编辑节点"检索（`isEditable` / EditText 类名），不依赖任何 View ID |
| 发送按钮兜底依赖 QQ 专属 View ID `com.tencent.mobileqq:id/send_btn` | 通用发送关键词识别（发送/送出/send/submit…），可开关 |
| 无作用范围配置 | 新增目标应用白名单（留空 = 所有应用）与排除黑名单 |
| 无键盘保护 | 自动跳过输入法(IME)窗口与包名，绝不改写键盘候选区 |
| 无隐私保护 | 自动跳过密码框（`isPassword`） |
| 替换规则写死/半写死 | 全部配置界面可自定义（继承上游重构成果） |

## 技术栈

- Android 原生 API（无障碍服务 AccessibilityService + SharedPreferences）
- Java 8 / Gradle 8.8 / AGP 8.4.2
- minSdk 23 / compileSdk 34

## 构建

```bash
./gradlew assembleDebug
```

产物：`app/build/outputs/apk/debug/app-debug.apk`（已构建好的现成 APK 见 `dist/喵喵助手-v1.0-debug.apk`）

> 提示：若 Gradle 发行版下载缓慢（国内网络），可先用浏览器/下载工具从
> https://mirrors.cloud.tencent.com/gradle/gradle-8.8-bin.zip 下载
> `gradle-8.8-bin.zip` 放入 `%USERPROFILE%\.gradle\wrapper\dists\gradle-8.8-bin\` 下对应哈希目录，
> 或直接修改 `gradle/wrapper/gradle-wrapper.properties` 的 distributionUrl 为镜像地址。

## 使用

1. 安装 APK（开启"允许未知来源"）
2. 打开应用 → 前往开启无障碍服务「喵喵助手（任意聊天软件文本改写）」
3. 配置作用范围：留空目标应用 = 所有应用；或点击预置按钮只选要改写的聊天软件
4. 配置替换规则 / 断句 / 颜文字 → 保存设置
5. 在任意聊天软件输入框中打字，触发规则自动改写

> 提示：默认会排除输入法、桌面启动器、系统设置等，防止误改写；密码框永不处理。

## 逆向说明

详见 [REVERSE.md](REVERSE.md)：上游限制来源、逐文件改造对照、通用化设计要点。

## 目录结构

```
MeowMeowAssistant/
├── app/src/main/java/com/meow/assistant/
│   ├── MeowAccessibilityService.java   # 通用无障碍服务（功能扩张核心）
│   ├── TextProcessor.java              # 文本改写引擎（与上游一致）
│   ├── CatConfig.java                  # 配置模型（含作用范围白名单/黑名单）
│   └── MainActivity.java               # 控制面板
├── tools/                              # 引擎行为验证工具（Java 测试 + Python 演示）
├── dist/                               # 已构建的 APK
├── REVERSE.md                          # 逆向工程说明
├── NOTICE                              # AGPL-3.0 修改声明
└── LICENSE                             # AGPL-3.0（与上游一致）
```

## 免责声明

1. 本项目是 [QQMiaoAssistant](https://github.com/QiCaiJie114514/QQMiaoAssistant)（AGPL-3.0）的
   **功能扩张（二次开发衍生）项目**，核心改写引擎与上游一致，本仓库不主张对上游代码的独立著作权。
2. 上游仓库本身亦声明其由未知作者的 APK 逆向重建，原作者信息请以上游仓库为准。
3. 本项目仅供技术学习与交流，请勿用于任何违反法律法规或平台规则的行为。
4. 若涉及上游或原著作权人权益，请通过上游公开联系方式（QQ：1670534177）或本仓库 Issues 联系，
   我们将第一时间配合处理（署名、下架或整改）。

## 致谢

感谢 [QQMiaoAssistant](https://github.com/QiCaiJie114514/QQMiaoAssistant) 作者提供的开源基础。
