package com.meow.assistant;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
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
    private static final String[] SEND_KEYWORDS = {"发送", "送出", "提交", "send", "submit", "enter", "➤"};

    private CatConfig cachedConfig;
    private String userOriginal = "";
    private String lastSet = "";
    private boolean processing = false;
    private long lastWriteTime = 0;

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
            // 窗口切换：清空跟踪状态，重新加载配置
            this.processing = false;
            this.userOriginal = "";
            this.lastSet = "";
            this.lastWriteTime = 0L;
            this.cachedConfig = CatConfig.load(this);
            return;
        }
        if (type == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            AccessibilityNodeInfo src = e.getSource();
            if (src != null) {
                if (cfg.enableSendFallback && isSendButton(src)) {
                    Log.d(TAG, "点击发送按钮，兜底处理");
                    doProcess(true);
                }
                src.recycle();
            }
            return;
        }
        if (type == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
            if (CatConfig.MODE_REALTIME.equals(cfg.processingMode)) {
                doProcess(false);
                return;
            }
            // 标点触发模式：取当前输入框文本，句末为标点才处理
            String raw = readEditableTextFromEvent(e);
            if (raw == null || raw.trim().isEmpty()) {
                return;
            }
            if (isPunctuationEnding(raw.trim())) {
                Log.d(TAG, "标点触发: " + raw.trim());
                doProcess(false);
            }
        }
    }

    /** 从事件源（若为可编辑节点）读取文本；事件源不可用时回退到窗口树搜索 */
    private String readEditableTextFromEvent(AccessibilityEvent e) {
        AccessibilityNodeInfo src = e.getSource();
        if (src != null) {
            try {
                if (src.isEditable() && !src.isPassword()) {
                    CharSequence cs = src.getText();
                    if (cs != null && cs.length() > 0) {
                        return cs.toString();
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
            CharSequence cs = inp.getText();
            return cs == null ? null : cs.toString();
        } finally {
            inp.recycle();
        }
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
        AccessibilityNodeInfo inp = findEditableInAppWindows();
        if (inp == null) {
            this.processing = false;
            return;
        }
        CharSequence cs = inp.getText();
        if (cs == null || cs.length() == 0) {
            inp.recycle();
            this.processing = false;
            this.userOriginal = "";
            this.lastSet = "";
            return;
        }
        String raw = cs.toString().trim();
        if (raw.isEmpty()) {
            inp.recycle();
            this.processing = false;
            this.userOriginal = "";
            this.lastSet = "";
            return;
        }
        CatConfig cfg = this.cachedConfig;
        if (cfg == null) {
            cfg = CatConfig.load(this);
            this.cachedConfig = cfg;
        }
        long now = System.currentTimeMillis();
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

    /** 在非输入法、非覆盖层的应用窗口中查找可编辑输入框（通用版核心检索逻辑） */
    private AccessibilityNodeInfo findEditableInAppWindows() {
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
                        CatConfig cfg = cachedConfig != null ? cachedConfig : CatConfig.load(this);
                        if (cfg.shouldHandlePackage(wpkg)) {
                            AccessibilityNodeInfo found = findEditable(root);
                            if (found != null) {
                                return found;
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
                    CatConfig cfg = cachedConfig != null ? cachedConfig : CatConfig.load(this);
                    if (cfg.shouldHandlePackage(wpkg)) {
                        return findEditable(root);
                    }
                }
            } finally {
                root.recycle();
            }
        }
        return null;
    }

    /** 深度优先查找可编辑节点：isEditable 优先，类名兜底（EditText 系 / Compose / 输入型节点），跳过密码框 */
    private AccessibilityNodeInfo findEditable(AccessibilityNodeInfo n) {
        if (n == null) {
            return null;
        }
        if (isEditableNode(n)) {
            return AccessibilityNodeInfo.obtain(n);
        }
        for (int i = 0; i < n.getChildCount(); i++) {
            AccessibilityNodeInfo c = n.getChild(i);
            if (c != null) {
                AccessibilityNodeInfo r = findEditable(c);
                c.recycle();
                if (r != null) {
                    return r;
                }
            }
        }
        return null;
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
        i.flags = AccessibilityServiceInfo.FLAG_DEFAULT
                | AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                | AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        i.notificationTimeout = 50L;
        // 关键：不再设置 packageNames —— 监听所有应用，作用范围由配置动态决定
        setServiceInfo(i);
        this.cachedConfig = CatConfig.load(this);
    }
}
