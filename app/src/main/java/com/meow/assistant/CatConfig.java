package com.meow.assistant;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;

/**
 * 喵喵助手配置模型。
 * 在保留原版全部字段（替换规则/断句/颜文字/处理模式）的基础上，新增"作用范围"配置：
 * - targetPackages：目标应用白名单（空 = 所有应用）
 * - excludePackages：排除应用黑名单（输入法/桌面等，永远不处理）
 * - enableSendFallback：发送按钮兜底处理开关（原版对 QQ 发送按钮的兜底逻辑，泛化为关键词识别）
 */
public class CatConfig {
    public static final String[] BUILTIN_EMOTICONS = {"^⌯𖥦⌯^ ੭ ^", "⌯'ㅅ'⌯", "=^𖥦^=", "⌯•ㅅ•⌯", "ฅ•̀∀•́ฅ", "ฅ ̳͒•ˑ̫• ̳͒ฅ♡", "ฅ(̳•·̫•̳ฅ)♡", "ฅ^••^ฅ", "=^•ω•^=", "₍^ >ヮ<^₎", "/ᐠ - ˕ -マ Ⳋ", "ฅ^•ﻌ•^ฅ", "ฅ՞•ﻌ•՞ฅ", "(ฅ´ω`ฅ)", "ฅ(*`ω´*)ฅ", "ฅ꒰ ⸝˶• •˶⸝꒱ฅ", "₍˄·͈༝·͈˄*₎◞ ̑̑", "!!^⌯𖥦⌯^ ੭!!", "₍^⸝⸝> ·̫ <⸝⸝ ^₎", "ฅ^._.^ฅ", "₍🎀˄•͈༝•͈˄₎ฅ˒˒", "^•͈༝•^ฅ", "꒰ఎ(^ . ֑ .^)໒꒱", "ฅ●ω●ฅ", "₍⸍⸌·͈༝·͈⸍⸌₎◞", "(>^ω^<)", "ฅ^-﹃-^ฅ", "^ ̳ට ̫ ට ̳^", "୧₍˄·͈༝·͈˄₎୨", "^ ̳ᴗ  ̫ ᴗ ̳^", "˓˓ก(⸍⸌̣ʷ̣̫⸍̣⸌₎ค˒˒", "ヽ(ฅ≧へ≦)ฅ", "(`･ω･´)ฅ", "(=^･ᴥ･^=)", "(^ω^ฅ)", "ฅ(≧▽≦)ฅ", "ฅ(=´▽`=)ฅ", "ヾ((๑˘ㅂ˘๑)ฅ", "(ฅ◑ω◑ฅ)", "(๑•̀ω•́ฅ)", "(ฅ>ω<*ฅ)", "(=^.^=)", "(=´ᴥ`)", "(=ↀωↀ=)", "(=^-ω-^=)", "ฅ(*°ω°*ฅ)", "ヽ(=^･ω･^=)丿", "(^•ᴥ•^)", "( Φ ω Φ )", "(=^x^=)", "ฅ( ̳• ◡ • ̳)ฅ", "o( =•ω•= )m", "~o( =∩ω∩= )m", "≡ω≡"};

    /** 默认排除的应用包名：输入法（IME）、桌面启动器、系统界面 —— 这些应用里的输入框绝不改写 */
    public static final String[] DEFAULT_EXCLUDE_PACKAGES = {
            "com.android.inputmethod.latin",           // AOSP 键盘
            "com.google.android.inputmethod.latin",    // Gboard
            "com.sohu.inputmethod.sogou",              // 搜狗输入法
            "com.baidu.input",                         // 百度输入法
            "com.iflytek.inputmethod",                 // 讯飞输入法
            "com.tencent.qqpinyin",                    // QQ拼音输入法
            "com.qq.pinyin",                           // QQ拼音（旧版包名）
            "com.touchtype.swiftkey",                  // SwiftKey
            "com.aliyun.inputmethod",                  // 阿里输入法
            "com.android.systemui",                    // 系统 UI
            "com.android.settings",                    // 系统设置
            "com.android.launcher3",                   // AOSP 桌面
            "com.google.android.apps.nexuslauncher",   // Pixel 桌面
            "com.miui.home",                           // 小米桌面
            "com.sec.android.app.launcher",            // 三星桌面
            "com.huawei.android.launcher",             // 华为桌面
            "com.oppo.launcher",                       // OPPO 桌面
            "com.vivo.launcher",                       // vivo 桌面
            "com.meizu.flyme.launcher"                 // 魅族桌面
    };

    public static final String KEY_RULES = "rules";
    public static final String KEY_ENABLE_APPEND = "enable_append";
    public static final String KEY_APPEND_TEXT = "append_text";
    public static final String KEY_ENABLE_EMOTICON = "enable_emoticon";
    public static final String KEY_PROCESSING_MODE = "processing_mode";
    public static final String KEY_TARGET_PACKAGES = "target_packages";
    public static final String KEY_EXCLUDE_PACKAGES = "exclude_packages";
    public static final String KEY_ENABLE_SEND_FALLBACK = "enable_send_fallback";
    public static final String MODE_PUNCTUATION = "punctuation";
    public static final String MODE_REALTIME = "realtime";
    private static final String PREFS_NAME = "meow_config";

    public static class Rule {
        public final String from;
        public final String to;

        public Rule(String from, String to) {
            this.from = from;
            this.to = to;
        }

        @Override
        public String toString() {
            return from + "=" + to;
        }
    }

    public boolean enableAppend = true;
    public String appendText = "喵";
    public boolean enableRandomEmoticon = true;
    public String processingMode = MODE_PUNCTUATION;
    public String[] customEmoticons = new String[0];
    public List<Rule> rules = new ArrayList<>();
    public String[] targetPackages = new String[0];
    public String[] excludePackages = new String[0];
    public boolean enableSendFallback = true;

    public static Rule parseRule(String line) {
        if (line == null) {
            return null;
        }
        String s = line.trim();
        if (s.isEmpty()) {
            return null;
        }
        String separators = "=＝→";
        int idx = -1;
        for (int i = 0; i < separators.length(); i++) {
            int p = s.indexOf(separators.charAt(i));
            if (p >= 0 && (idx < 0 || p < idx)) {
                idx = p;
            }
        }
        if (idx <= 0) {
            return null;
        }
        String from = s.substring(0, idx).trim();
        String to = s.substring(idx + 1).trim();
        if (from.isEmpty()) {
            return null;
        }
        return new Rule(from, to);
    }

    public static String rulesToString(List<Rule> rules) {
        StringBuilder sb = new StringBuilder();
        if (rules != null) {
            for (Rule r : rules) {
                if (r == null || r.from.isEmpty()) {
                    continue;
                }
                if (sb.length() > 0) {
                    sb.append("\n");
                }
                sb.append(r.from).append('=').append(r.to);
            }
        }
        return sb.toString();
    }

    public static CatConfig load(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS_NAME, 0);
        CatConfig cfg = new CatConfig();
        cfg.enableAppend = sp.getBoolean(KEY_ENABLE_APPEND, true);
        cfg.appendText = sp.getString(KEY_APPEND_TEXT, "喵");
        cfg.enableRandomEmoticon = sp.getBoolean(KEY_ENABLE_EMOTICON, true);
        cfg.processingMode = sp.getString(KEY_PROCESSING_MODE, MODE_PUNCTUATION);
        cfg.enableSendFallback = sp.getBoolean(KEY_ENABLE_SEND_FALLBACK, true);

        String rulesStr = sp.getString(KEY_RULES, "");
        if (rulesStr != null && !rulesStr.trim().isEmpty()) {
            List<Rule> list = new ArrayList<>();
            for (String line : rulesStr.split("\n")) {
                Rule r = parseRule(line);
                if (r != null) {
                    list.add(r);
                }
            }
            cfg.rules = list;
        }

        String custom = sp.getString(KEY_CUSTOM_EMOTICONS, "");
        if (custom != null && !custom.trim().isEmpty()) {
            List<String> list = new ArrayList<>();
            for (String s : custom.split("\n")) {
                String t = s.trim();
                if (!t.isEmpty()) {
                    list.add(t);
                }
            }
            cfg.customEmoticons = list.toArray(new String[0]);
        } else {
            cfg.customEmoticons = new String[0];
        }

        String targets = sp.getString(KEY_TARGET_PACKAGES, "");
        cfg.targetPackages = splitLines(targets);

        String excludes = sp.getString(KEY_EXCLUDE_PACKAGES, "");
        cfg.excludePackages = splitLines(excludes);

        return cfg;
    }

    private static String[] splitLines(String s) {
        if (s == null || s.trim().isEmpty()) {
            return new String[0];
        }
        List<String> list = new ArrayList<>();
        for (String line : s.split("\n")) {
            String t = line.trim();
            if (!t.isEmpty()) {
                list.add(t);
            }
        }
        return list.toArray(new String[0]);
    }

    public void save(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS_NAME, 0);
        SharedPreferences.Editor ed = sp.edit();
        ed.putBoolean(KEY_ENABLE_APPEND, this.enableAppend);
        ed.putString(KEY_APPEND_TEXT, this.appendText == null ? "" : this.appendText);
        ed.putBoolean(KEY_ENABLE_EMOTICON, this.enableRandomEmoticon);
        ed.putString(KEY_PROCESSING_MODE, this.processingMode == null ? MODE_PUNCTUATION : this.processingMode);
        ed.putString(KEY_RULES, rulesToString(this.rules));
        ed.putString(KEY_CUSTOM_EMOTICONS, join(this.customEmoticons, "\n"));
        ed.putString(KEY_TARGET_PACKAGES, join(this.targetPackages, "\n"));
        ed.putString(KEY_EXCLUDE_PACKAGES, join(this.excludePackages, "\n"));
        ed.putBoolean(KEY_ENABLE_SEND_FALLBACK, this.enableSendFallback);
        ed.apply();
    }

    public String[] getActiveEmoticons() {
        if (this.customEmoticons != null && this.customEmoticons.length > 0) {
            return this.customEmoticons;
        }
        return BUILTIN_EMOTICONS;
    }

    /** 是否允许处理指定包名：白名单非空则必须命中；否则不在黑名单且不在默认排除列表 */
    public boolean shouldHandlePackage(String pkg) {
        if (pkg == null || pkg.isEmpty()) {
            return false;
        }
        if (this.targetPackages != null && this.targetPackages.length > 0) {
            return contains(this.targetPackages, pkg);
        }
        if (contains(DEFAULT_EXCLUDE_PACKAGES, pkg)) {
            return false;
        }
        return !contains(this.excludePackages, pkg);
    }

    public static boolean contains(String[] arr, String s) {
        if (arr == null || s == null) {
            return false;
        }
        for (String x : arr) {
            if (s.equals(x)) {
                return true;
            }
        }
        return false;
    }

    private static String join(String[] arr, String delim) {
        StringBuilder sb = new StringBuilder();
        if (arr != null) {
            for (int i = 0; i < arr.length; i++) {
                String s = arr[i];
                if (s == null) {
                    continue;
                }
                if (sb.length() > 0) {
                    sb.append(delim);
                }
                sb.append(s);
            }
        }
        return sb.toString();
    }
}
