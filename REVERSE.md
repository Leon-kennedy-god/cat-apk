# 逆向工程说明（REVERSE.md）

> 本仓库为上游 [QQMiaoAssistant](https://github.com/QiCaiJie114514/QQMiaoAssistant) 的
> **功能扩张（二次开发衍生）项目**，衍生关系与合规声明见 [README.md](README.md) 顶部与 [NOTICE](NOTICE)。

本文档记录对 [QQMiaoAssistant](https://github.com/QiCaiJie114514/QQMiaoAssistant) 的逆向分析过程，
以及"喵喵助手"通用化改造的逐文件对照。

## 一、原版工作原理

原版是一个基于 **Android 无障碍服务（AccessibilityService）** 的文本改写工具，流程如下：

```
用户在 QQ 输入框打字
   │  (系统产生无障碍事件)
   ▼
QQAccessibilityService.onAccessibilityEvent()
   │  事件类型：
   │  ├─ TYPE_WINDOW_STATE_CHANGED → 重置跟踪状态、重载配置
   │  ├─ TYPE_VIEW_CLICKED（QQ 发送按钮）→ 兜底处理
   │  └─ TYPE_VIEW_TEXT_CHANGED → 标点触发 / 实时处理
   ▼
查找输入框：findNodeById(com.tencent.mobileqq:id/input) → 找不到再 findEditable()
   ▼
读取文本 raw ──► 剥离已附加内容（颜文字/连续符号串）恢复用户原文 userOriginal
   ▼
TextProcessor.process(userOriginal)：
   1. 按顺序应用替换规则（我→本喵、你→主人…）
   2. 按标点分句、句末追加"喵"
   3. 句末附加随机猫咪颜文字
   ▼
通过 ACTION_SET_TEXT 写回输入框，并把光标移到末尾
   ▼
防反馈环：600ms 内且文本等于上次写入值 → 跳过（"写入回显跳过"）
```

## 二、原版"只能在 QQ 使用"的限制来源（逆向定位）

1. **包名过滤**（两处）：
   - `res/xml/accessibility_service_config.xml`：`android:packageNames="com.tencent.mobileqq,com.tencent.mobileqqi"`
   - `onServiceConnected()`：`i.packageNames = new String[]{PKG_QQ, PKG_QQI}`
   → 其他聊天软件的事件根本不会送达服务。

2. **硬编码 View ID**：
   - `ID_INPUT = "com.tencent.mobileqq:id/input"`：查找输入框优先按此 ID
   - `ID_SEND = "com.tencent.mobileqq:id/send_btn"`：发送按钮兜底只认这个 ID
   → 即便去掉包名过滤，其他软件的输入框 ID 不同，同样找不到。

3. **兜底逻辑缺陷**：`findEditable()`（任意可编辑节点）虽然存在，但只在 QQ 专属 ID
   找不到时才会启用；且未处理输入法(IME)窗口抢占问题（键盘弹出时活动窗口可能是输入法）。

## 三、喵喵助手的通用化改造（逐文件对照）

| 原版文件 | 喵喵助手对应文件 | 改造内容 |
| --- | --- | --- |
| `QQAccessibilityService.java` | `MeowAccessibilityService.java` | 见下方 3.1 |
| `TextProcessor.java` | `TextProcessor.java` | 改写引擎原样移植，行为一致 |
| `CatConfig.java` | `CatConfig.java` | 保留全部原字段，新增作用范围字段（见 3.2） |
| `MainActivity.java` | `MainActivity.java` | 保留全部原设置项，新增"作用范围"区块与聊天软件预置按钮 |
| `AndroidManifest.xml` | `AndroidManifest.xml` | 应用名改"喵喵助手"、包名改 `com.meow.assistant`、移除无关权限 |
| `accessibility_service_config.xml` | `accessibility_service_config.xml` | **删除 `packageNames`**，监听所有应用 |

### 3.1 无障碍服务（核心）

- **去掉包名写死**：`onServiceConnected()` 不再设置 `packageNames`；事件处理前调用
  `CatConfig.shouldHandlePackage(pkg)` 动态过滤：
  - 目标应用白名单非空 → 只处理白名单内的包名；
  - 白名单为空（所有应用）→ 跳过默认排除列表（输入法/桌面/系统设置等 19 个包名）+ 用户排除列表；
  - 自身包名 `com.meow.assistant` **永远排除**（防止改写配置界面自己的输入框）。
- **通用输入框检索** `findEditableInAppWindows()`：
  - 遍历 `getWindows()` 所有窗口，**跳过 `TYPE_INPUT_METHOD`（输入法）与
    `TYPE_ACCESSIBILITY_OVERLAY`（无障碍覆盖层）窗口**——解决键盘弹出时活动窗口被 IME
    抢占的问题（原版没有处理，通用版必须处理）；
  - 每窗口 DFS 查找 `isEditable()` 节点；类名兜底匹配 `EditText` / `TextInput` / `TextField`
    （覆盖 WebView 输入框、Compose/Flutter 输入节点等）；
  - **跳过密码框**（`isPassword()`），隐私保护。
- **发送按钮通用识别** `isSendButton()`：可点击 + 非输入框 + 类名像按钮 +
  文本/内容描述含关键词（发送/送出/提交/send/submit/enter/➤），替代原版写死的
  `com.tencent.mobileqq:id/send_btn`；可通过"发送按钮兜底"开关关闭。
- **保留原版全部核心机制**：增量跟踪 `userOriginal`（前缀增量/剥离重建）、
  600ms 写入回显跳过（防反馈环）、标点触发/实时两种模式、实时模式打字中不加颜文字
  （发送时补上）、`stripAll` 逆向剥离逻辑。

### 3.2 配置模型新增字段

```
targetPackages    目标应用白名单（String[]，空 = 所有应用）
excludePackages   用户排除黑名单（String[]，与内置默认排除列表合并生效）
enableSendFallback 发送按钮兜底开关（默认开）
```

## 四、通用化设计要点（为什么这样改是安全的）

1. **防反馈环**：写回文本会再次触发 `TYPE_VIEW_TEXT_CHANGED`，600ms 回显跳过保证不循环；
2. **防误改写**：输入法候选区、桌面搜索框、系统设置搜索框默认全部排除；密码框硬跳过；
3. **防自伤**：服务不处理本应用包名，配置界面输入规则/颜文字时不会被改写；
4. **按窗口而非按根节点检索**：键盘弹出时活动窗口是 IME，按窗口遍历并跳过 IME 窗口，
   才能稳定拿到聊天应用的输入框；
5. **白名单优先**：只想改某几个聊天软件时，用预置按钮一键填入包名，最稳妥。

## 五、构建与验证

```bash
./gradlew assembleDebug            # 产出 app-debug.apk
```

行为验证：`TextProcessor` 为纯 Java 无 Android 依赖，核心算法可通过单元测试/命令行
独立验证（见项目 tools/ 目录的引擎测试）。

## 六、已知边界

- 无障碍服务要求应用在"无障碍"设置中开启（Android 系统限制，与原版相同）；
- 个别加密/私有化输入控件（如部分游戏内置聊天）不暴露可编辑节点，无法改写；
- 微信等部分应用对无障碍写回有限流/兼容性问题时，建议配合"标点触发"模式使用。
