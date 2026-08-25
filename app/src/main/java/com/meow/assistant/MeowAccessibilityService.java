package com.meow.assistant;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * 喵喵助手无障碍服务 —— 由原版 QQAccessibilityService 逆向重构的"通用版"。
 *
 * 原版只能在 QQ 使用，限制来源有二：
 *  1. accessibility_service_config.xml 与 onServiceConnected 中写死了包名
 *     com.tencent.mobileqq / com.tencent.mobileqqi；
 *  2. 查找输入框时优先使用硬编码 View ID com.tencent.mobileqq:id/input。
 *
 * 本版本：
 *  - 不再过滤包名，接收所有应用事件，改由 CatConfig.shouldHandlePackage() 动态决定作用范围
 *    （白名单：仅处理指定聊天软件；黑名单：跳过输入法/桌面/系统界面等）；
 *  - 输入框检索改为通用"可编辑节点"查找（isEditable / EditText 类名），不依赖任何 View ID；
 *  - 遍历窗口时跳过输入法(IME)窗口，避免误改键盘候选区；
 *  - 自动跳过密码框（isPassword），保护隐私；
 *  - 保留原版的：增量跟踪 userOriginal、写回回显跳过（防反馈环）、标点触发/实时两种模式、
 *    发送按钮兜底（由 QQ 专属 ID 改为通用发送关键词识别）。
 *
 * 改写效果（替换规则 → 断句追加 → 随机颜文字）由 TextProcessor 提供，与原版一致。
 */
public class MeowAccessibilityService extends AccessibilityService {
    private static final String TAG = "MeowSvc";

    /** 发送按钮关键词：节点文本或内容描述命中任一即视为"发送"（可开关，默认开） */
    private static final String[] SEND_KEYWORDS = {"发送", "送出", "提交", "发表", "发布", "回复", "评论", "send", "submit", "enter", "post", "reply", "comment", "ok", "done", "➤"};

    /** 高信号提示词子串：命中即大概率是应用官方提示词（如"说点什么吧"），不依赖任何时序 */
    private static final String[] PLACEHOLDER_PATTERNS = {
            "说点什么", "说两句", "说点啥", "输入消息", "写评论", "添加评论",
            "发个友善的", "善语结善缘", "请输入", "评论一下", "留下你的",
            "说说你的", "留言", "吐槽一下", "回复一下", "讲两句", "想说什么"
    };

    private CatConfig cachedConfig;
    private String userOriginal = "";
    private String lastSet = "";
    private boolean processing = false;
    private long lastWriteTime = 0;
    /** 上一次收到文本变化事件的时间（用于语音/候选词等流式输入防抖） */
    private long lastTextChangeTime = 0;
    /** 最近一次观察到"输入框文本变空"的时间与包名（用于提示词防护） */
    private long lastEmptyObservedTime = 0;
    private String lastEmptyPkg = "";
    /** 最近一次点击发送按钮的时间与包名（用于提示词防护） */
    private long sendResetUntil = 0;
    private String sendResetPkg = "";
    /** 最近一次被拦截的提示词文本与包名（10 秒内精确匹配兜底） */
    private String lastPlaceholderText = "";
    private String lastPlaceholderPkg = "";
    private long lastPlaceholderTime = 0;
    /** 最近一次找到的输入框所属包名（doProcess 提示词防护用） */
    private String lastInputPkg = "";

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        String pkg = e.getPackageName() != null ? e.getPackageName().toString() : "";
        if (pkg.isEmpty() || pkg.equals(getPackageName())) {
            return; // 永不处理自身界面（防止改写配置输入框）
        }
        CatConfig cfg = cachedConfig;
        if (cfg == null) {
            cfg = CatConfig.load(this);
            cachedConfig = cfg;
        }
        if (!cfg.shouldHandlePackage(pkg)) {
            return;
        }
        int type = e.getEventType();
        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            // 窗口切换：清空跟踪状态，重新加载配置；防抖时钟从窗口变化时开始计时，
            // 使面板刚弹出时的占位文本事件（提示词）落入防抖窗口而被跳过
            this.processing = false;
            this.userOriginal = "";
            this.lastSet = "";
            this.lastWriteTime = 0L;
            this.lastTextChangeTime = System.currentTimeMillis();
            this.cachedConfig = CatConfig.load(this);
            return;
        }
        if (type == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            AccessibilityNodeInfo src = e.getSource();
            if (src != null) {
                if (cfg.enableSendFallback && isSendButton(src)) {
                    Log.d(TAG, "点击发送按钮，兜底处理");
                    this.sendResetUntil = System.currentTimeMillis();
                    this.sendResetPkg = pkg;
                    doProcess(true);
                }
                src.recycle();
            }
            return;
        }
        if (type == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
            // ===== 官方提示词防护（事件级） =====
            // 抖音等应用把提示词作为节点文本写入，且清空/发送后输入框仍是聚焦状态。
            // 特征：提示词是"文本刚变空/刚发送/刚点击"之后的短文本，或命中提示词模式库。
            // 注意：不能要求"变化前为空"——很多应用把 删除→提示词 合并成一步替换事件。
            CharSequence afterCs = (e.getText() != null && e.getText().size() > 0) ? e.getText().get(0) : null;
            boolean afterEmpty = afterCs == null || afterCs.length() == 0;
            long nowMs = System.currentTimeMillis();
            if (afterEmpty) {
                noteEmpty(pkg, nowMs);
            } else if (isPlaceholderLike(afterCs.toString(), pkg, nowMs, true)) {
                Log.d(TAG, "事件级拦截疑似提示词: " + afterCs);
                rememberPlaceholder(afterCs.toString(), pkg, nowMs);
                return;
            }
            if (CatConfig.MODE_REALTIME.equals(cfg.processingMode)) {
                // 流式输入防抖（仅实时模式）：语音输入/输入法候选词会以极快频率连续触发
                // 文本变化，若两次变化间隔小于 stableDelayMs，视为"输入尚未成型"，跳过，
                // 等待输入停止（静默 stableDelayMs）后再改写，避免破坏未成型的语音识别结果。
                // 标点模式不需要防抖：流式中间结果极少以标点结尾，且防抖会吞掉最后的触发事件。
                long now = System.currentTimeMillis();
                long gap = now - this.lastTextChangeTime;
                this.lastTextChangeTime = now;
                if (cfg.stableDelayMs > 0 && gap < cfg.stableDelayMs) {
                    Log.d(TAG, "流式输入防抖跳过 (gap=" + gap + "ms)");
                    return;
                }
                doProcess(false);
                return;
            }
            // 标点触发模式：取当前输入框文本，句末为标点才处理
            String raw = readEditableTextFromEvent(e, cfg, pkg);
            if (raw == null || raw.trim().isEmpty()) {
                return;
            }
            if (isPunctuationEnding(raw.trim())) {
                Log.d(TAG, "标点触发: " + raw.trim());
                doProcess(false);
            }
        }
    }

    /**
     * 从事件源（若为可编辑节点）读取文本；事件源不可用时回退到窗口树搜索。
     * 已集成防护：仅接受"可处理"节点（见 isUsableForInput），并排除提示词文本。
     */
    private String readEditableTextFromEvent(AccessibilityEvent e, CatConfig cfg, String pkg) {
        long now = System.currentTimeMillis();
        AccessibilityNodeInfo src = e.getSource();
        if (src != null) {
            try {
                if (isUsableForInput(src, cfg)) {
                    CharSequence cs = src.getText();
                    if (cs != null && cs.length() > 0) {
                        String text = cs.toString();
                        if (!isPlaceholderText(src, text) && !isPlaceholderLike(text, pkg, now, true)) {
                            return text;
                        }
                    }
                }
            } finally {
                src.recycle();
            }
        }
        AccessibilityNodeInfo inp = findEditableInAppWindows();
        if (inp == null) {
            return null;
        }
        try {
            if (!isUsableForInput(inp, cfg)) {
                return null;
            }
            CharSequence cs = inp.getText();
            if (cs == null || cs.length() == 0) {
                noteEmpty(pkg, now);
                return null;
            }
            String text = cs.toString();
            if (isPlaceholderText(inp, text) || isPlaceholderLike(text, pkg, now, true)) {
                rememberPlaceholder(text, pkg, now);
                return null;
            }
            return text;
        } finally {
            inp.recycle();
        }
    }

    /** 记录"输入框文本为空"的观察（事件/读取/处理任意路径），用于提示词时序判定 */
    private void noteEmpty(String pkg, long now) {
        this.lastEmptyObservedTime = now;
        this.lastEmptyPkg = pkg;
    }

    /** 记录被拦截的提示词文本，10 秒内对完全相同文本做精确匹配兜底 */
    private void rememberPlaceholder(String text, String pkg, long now) {
        this.lastPlaceholderText = text;
        this.lastPlaceholderPkg = pkg;
        this.lastPlaceholderTime = now;
    }

    /**
     * 官方提示词综合判定（事件/读取路径）：短文本（2~40 字）且满足任一条件：
     *  - 同一应用"输入框刚变空/刚点击发送"3 秒内出现（时序信号）；
     *  - 10 秒内曾被拦截过的完全相同文本（精确记忆兜底）；
     *  - 命中高信号提示词模式库（说点什么/写评论/善语结善缘…）。
     */
    private boolean isPlaceholderLike(String text, String pkg, long now, boolean allowTiming) {
        if (text == null) {
            return false;
        }
        if (allowTiming) {
            boolean recentEmpty = pkg.equals(this.lastEmptyPkg) && (now - this.lastEmptyObservedTime) < 3000;
            boolean recentSend = pkg.equals(this.sendResetPkg) && now < this.sendResetUntil + 3000;
            if (recentEmpty || recentSend) {
                return true;
            }
        }
        return isPlaceholderMemory(text, pkg, now) || isPlaceholderPattern(text);
    }

    /** 10 秒内曾被拦截过的完全相同文本（精确记忆兜底） */
    private boolean isPlaceholderMemory(String text, String pkg, long now) {
        if (text == null) {
            return false;
        }
        String t = text.trim();
        int len = t.length();
        if (len < 2 || len > 40) {
            return false;
        }
        return pkg.equals(this.lastPlaceholderPkg)
                && now - this.lastPlaceholderTime < 10000
                && t.equals(this.lastPlaceholderText);
    }

    /** 高信号提示词模式：短文本且命中模式库 */
    private boolean isPlaceholderPattern(String text) {
        if (text == null) {
            return false;
        }
        String t = text.trim();
        int len = t.length();
        if (len < 2 || len > 40) {
            return false; // 提示词都是短文本；过长的文本一定是真实内容
        }
        for (String p : PLACEHOLDER_PATTERNS) {
            if (t.contains(p)) {
                return true;
            }
        }
        return false;
    }

    /** 节点是否可用于输入处理：非空、非密码框、且（按配置）处于聚焦状态 */
    private boolean isUsableForInput(AccessibilityNodeInfo n, CatConfig cfg) {
        try {
            if (n == null || n.isPassword()) {
                return false;
            }
            if (cfg != null && cfg.onlyProcessFocused && !n.isFocused()) {
                return false; // 未聚焦的输入框：里面的文字多半是提示词/占位内容
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 提示词检测：节点当前文本与其 hint（提示词）一致时，视为占位文本，不处理 */
    private boolean isPlaceholderText(AccessibilityNodeInfo n, String text) {
        if (n == null || text == null || text.trim().isEmpty()) {
            return false;
        }
        if (Build.VERSION.SDK_INT >= 26) {
            try {
                CharSequence hint = n.getHintText();
                if (hint != null && hint.length() > 0 && text.trim().equals(hint.toString().trim())) {
                    Log.d(TAG, "提示词占位文本，跳过: " + text);
                    return true;
                }
            } catch (Exception e) {
                // 忽略 hint 读取异常
            }
        }
        return false;
    }

    private boolean isPunctuationEnding(String s) {
        if (s == null || s.isEmpty()) {
            return false;
        }
        char last = s.charAt(s.length() - 1);
        return last == 12290 || last == 65281 || last == '!' || last == 65311 || last == '?' || last == ' ';
    }

    private void doProcess(boolean isSendClick) {
        if (this.processing) {
            return;
        }
        this.processing = true;
        CatConfig cfg = this.cachedConfig;
        if (cfg == null) {
            cfg = CatConfig.load(this);
            this.cachedConfig = cfg;
        }
        AccessibilityNodeInfo inp = findEditableInAppWindows();
        if (inp == null || !isUsableForInput(inp, cfg)) {
            if (inp != null) {
                inp.recycle();
            }
            this.processing = false;
            return;
        }
        String inputPkg = this.lastInputPkg;
        CharSequence cs = inp.getText();
        long now = System.currentTimeMillis();
        if (cs == null || cs.length() == 0) {
            inp.recycle();
            this.processing = false;
            this.userOriginal = "";
            this.lastSet = "";
            noteEmpty(inputPkg, now);
            return;
        }
        String raw = cs.toString().trim();
        if (raw.isEmpty() || isPlaceholderText(inp, raw)) {
            inp.recycle();
            this.processing = false;
            this.userOriginal = "";
            this.lastSet = "";
            noteEmpty(inputPkg, now);
            return;
        }
        // 处理级提示词兜底（严格模式）：只认"记忆匹配"（事件级已拦截过的相同文本），
        // 或"模式命中 + 输入框刚被清空"（此时才可能是提示词）——
        // 避免发送兜底把用户刚打的真实短消息误判为提示词
        boolean placeholderHit = isPlaceholderMemory(raw, inputPkg, now)
                || (isPlaceholderPattern(raw)
                    && inputPkg.equals(this.lastEmptyPkg)
                    && (now - this.lastEmptyObservedTime) < 3000);
        if (placeholderHit) {
            Log.d(TAG, "处理级拦截疑似提示词: " + raw);
            rememberPlaceholder(raw, inputPkg, now);
            inp.recycle();
            this.processing = false;
            this.userOriginal = "";
            this.lastSet = "";
            return;
        }
        long j = this.lastWriteTime;
        if (j > 0 && now - j < 600 && raw.equals(this.lastSet)) {
            Log.d(TAG, "写入回显跳过");
            this.lastWriteTime = 0L;
            inp.recycle();
            this.processing = false;
            return;
        }
        boolean isRealtime = CatConfig.MODE_REALTIME.equals(cfg.processingMode);
        if (!isRealtime && this.lastSet.isEmpty()) {
            this.userOriginal = stripAll(raw, cfg);
            Log.d(TAG, "标点首次剥离: " + this.userOriginal);
        } else if (this.lastSet.isEmpty() || !raw.startsWith(this.lastSet)) {
            this.userOriginal = stripAll(raw, cfg);
            Log.d(TAG, "剥离重建: " + this.userOriginal);
        } else {
            String added = raw.substring(this.lastSet.length());
            this.userOriginal += added;
            Log.d(TAG, "前缀增量: +" + added + "  userOriginal=" + this.userOriginal);
        }
        if (this.userOriginal.isEmpty()) {
            Log.d(TAG, "原文为空，跳过");
            inp.recycle();
            this.processing = false;
            return;
        }
        CatConfig effectiveCfg = cfg;
        if (isRealtime && cfg.enableRandomEmoticon && !isSendClick) {
            effectiveCfg = cloneConfigWithoutEmoticon(cfg);
        }
        String target = TextProcessor.process(this.userOriginal, effectiveCfg);
        if (!target.equals(raw)) {
            Log.d(TAG, "写入: raw=" + raw + "  userOriginal=" + this.userOriginal + "  target=" + target);
            boolean ok = setText(inp, target);
            if (ok) {
                this.lastSet = target;
                this.lastWriteTime = System.currentTimeMillis();
            }
        } else {
            this.lastSet = target;
        }
        inp.recycle();
        this.processing = false;
    }

    private CatConfig cloneConfigWithoutEmoticon(CatConfig src) {
        CatConfig c = new CatConfig();
        c.enableAppend = src.enableAppend;
        c.appendText = src.appendText;
        c.enableRandomEmoticon = false;
        c.processingMode = src.processingMode;
        c.customEmoticons = src.customEmoticons;
        c.rules = src.rules;
        return c;
    }

    /** 从已改写文本中剥离出用户原始输入（原版逻辑）：移除颜文字 + 连续符号串 */
    private String stripAll(String text, CatConfig cfg) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String result = text;
        String[] emotes = cfg.getActiveEmoticons();
        if (emotes.length == 0) {
            emotes = CatConfig.BUILTIN_EMOTICONS;
        }
        Arrays.sort(emotes, new Comparator<String>() {
            @Override
            public int compare(String a, String b) {
                return b.length() - a.length();
            }
        });
        for (String em : emotes) {
            if (em == null || em.isEmpty()) {
                continue;
            }
            int idx;
            while ((idx = result.indexOf(em)) >= 0) {
                int st;
                if (idx <= 0 || result.charAt(idx - 1) != ' ') {
                    st = idx;
                } else {
                    st = idx - 1;
                }
                result = result.substring(0, st) + result.substring(idx + em.length());
            }
        }
        return result.replaceAll("\\s*[\\p{S}\\p{So}\\p{Sm}\\p{Sk}\\p{P}]{3,}\\s*", " ").trim();
    }

    /** 在非输入法、非覆盖层的应用窗口中查找可编辑输入框（通用版核心检索逻辑）。
     *  whenFocusRequired=true 时只接受聚焦输入框（防止误取提示词/后台输入框）。 */
    private AccessibilityNodeInfo findEditableInAppWindows() {
        CatConfig cfg = cachedConfig != null ? cachedConfig : CatConfig.load(this);
        List<AccessibilityWindowInfo> windows = getWindows();
        if (windows != null) {
            for (AccessibilityWindowInfo w : windows) {
                if (w == null) {
                    continue;
                }
                int wt = w.getType();
                if (wt == AccessibilityWindowInfo.TYPE_INPUT_METHOD
                        || wt == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY) {
                    continue; // 跳过输入法与无障碍覆盖层
                }
                AccessibilityNodeInfo root = w.getRoot();
                if (root == null) {
                    continue;
                }
                try {
                    String wpkg = root.getPackageName() != null ? root.getPackageName().toString() : "";
                    if (!wpkg.isEmpty() && !wpkg.equals(getPackageName())) {
                        if (cfg.shouldHandlePackage(wpkg)) {
                            AccessibilityNodeInfo found = findEditable(root, cfg.onlyProcessFocused);
                            if (found != null && isUsableForInput(found, cfg)) {
                                this.lastInputPkg = wpkg;
                                return found;
                            }
                            if (found != null) {
                                found.recycle();
                            }
                        }
                    }
                } finally {
                    root.recycle();
                }
            }
        }
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root != null) {
            try {
                // 兜底同样受作用范围约束：活动窗口可能是输入法，绝不允许误改
                String wpkg = root.getPackageName() != null ? root.getPackageName().toString() : "";
                if (!wpkg.isEmpty() && !wpkg.equals(getPackageName())) {
                    if (cfg.shouldHandlePackage(wpkg)) {
                        AccessibilityNodeInfo found = findEditable(root, cfg.onlyProcessFocused);
                        if (found != null && isUsableForInput(found, cfg)) {
                            this.lastInputPkg = wpkg;
                            return found;
                        }
                        if (found != null) {
                            found.recycle();
                        }
                    }
                }
            } finally {
                root.recycle();
            }
        }
        return null;
    }

    /** 深度优先查找可编辑节点：isEditable 优先，类名兜底（EditText 系 / Compose / 输入型节点），跳过密码框。
     *  focusedOnly=true 时只匹配处于聚焦状态的节点。 */
    private AccessibilityNodeInfo findEditable(AccessibilityNodeInfo n, boolean focusedOnly) {
        if (n == null) {
            return null;
        }
        if (isEditableNode(n)) {
            if (!focusedOnly || isFocusedSafe(n)) {
                return AccessibilityNodeInfo.obtain(n);
            }
        }
        for (int i = 0; i < n.getChildCount(); i++) {
            AccessibilityNodeInfo c = n.getChild(i);
            if (c != null) {
                AccessibilityNodeInfo r = findEditable(c, focusedOnly);
                c.recycle();
                if (r != null) {
                    return r;
                }
            }
        }
        return null;
    }

    private boolean isFocusedSafe(AccessibilityNodeInfo n) {
        try {
            return n.isFocused();
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isEditableNode(AccessibilityNodeInfo n) {
        try {
            if (n.isPassword()) {
                return false; // 绝不改写密码框
            }
            if (n.isEditable()) {
                return true;
            }
            CharSequence cls = n.getClassName();
            if (cls == null) {
                return false;
            }
            String c = cls.toString();
            return c.contains("EditText") || c.contains("TextInput") || c.contains("TextField");
        } catch (Exception e) {
            return false;
        }
    }

    /** 通用发送按钮识别：可点击、非输入框、类名像按钮、文本/描述含发送关键词 */
    private boolean isSendButton(AccessibilityNodeInfo n) {
        try {
            if (n == null || n.isEditable() || !n.isClickable()) {
                return false;
            }
            CharSequence cls = n.getClassName();
            String c = cls != null ? cls.toString() : "";
            boolean btnLike = c.contains("Button") || c.contains("Image") || c.contains("TextView");
            if (!btnLike) {
                return false;
            }
            CharSequence t = n.getText();
            CharSequence d = n.getContentDescription();
            String s = ((t != null ? t.toString() : "") + " " + (d != null ? d.toString() : "")).toLowerCase();
            if (s.trim().isEmpty()) {
                return false;
            }
            for (String kw : SEND_KEYWORDS) {
                if (s.contains(kw)) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /** 通过无障碍 ACTION_SET_TEXT 写回文本，并把光标移到末尾 */
    private boolean setText(AccessibilityNodeInfo n, String t) {
        if (n == null) {
            return false;
        }
        try {
            Bundle b = new Bundle();
            b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, t);
            boolean ok = n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b);
            if (ok) {
                Bundle a = new Bundle();
                a.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, t.length());
                a.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, t.length());
                n.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, a);
            }
            return ok;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void onInterrupt() {
        this.processing = false;
    }

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        AccessibilityServiceInfo i = new AccessibilityServiceInfo();
        i.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                | AccessibilityEvent.TYPE_VIEW_CLICKED
                | AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED;
        i.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        // FLAG_DEFAULT 无公开常量（值 1）：与原版 flags=81 等价
        i.flags = 0x00000001
                | AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                | AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        i.notificationTimeout = 50L;
        // 关键：不再设置 packageNames —— 监听所有应用，作用范围由配置动态决定
        setServiceInfo(i);
        this.cachedConfig = CatConfig.load(this);
    }
}
