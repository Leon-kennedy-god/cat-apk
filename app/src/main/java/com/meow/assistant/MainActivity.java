package com.meow.assistant;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;

/**
 * 喵喵助手控制面板。
 * 在原版（QQ文本改写助手）全部设置项基础上，新增"作用范围"区块：
 *  - 目标应用：白名单，每行一个包名，留空 = 所有应用（配合排除列表使用）
 *  - 排除应用：黑名单，每行一个包名（输入法/桌面等默认排除，服务端始终生效）
 *  - 发送按钮兜底：原版只认 QQ 发送按钮 View ID，这里改为通用发送关键词识别
 */
public class MainActivity extends Activity {
    /** 常用聊天软件预置包名，一键填入目标应用 */
    private static final String[][] CHAT_PRESETS = {
            {"QQ", "com.tencent.mobileqq"},
            {"微信", "com.tencent.mm"},
            {"Telegram", "org.telegram.messenger"},
            {"WhatsApp", "com.whatsapp"},
            {"钉钉", "com.alibaba.android.rimet"},
            {"Discord", "com.discord"},
            {"飞书", "com.ss.android.lark"},
            {"Slack", "com.slack"},
            {"LINE", "jp.naver.line.android"},
            {"Signal", "org.thoughtcrime.securesms"}
    };

    private CheckBox cbAppend;
    private CheckBox cbEmoticon;
    private CheckBox cbSendFallback;
    private CatConfig config;
    private EditText etAppendText;
    private EditText etCustomEmoticons;
    private EditText etRules;
    private EditText etTargets;
    private EditText etExcludes;
    private CheckBox rbPunctuation;
    private CheckBox rbRealtime;
    private TextView statusText;
    private Button toggleButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            this.config = CatConfig.load(this);
        } catch (Exception e) {
            this.config = new CatConfig();
        }
        ScrollView scrollView = new ScrollView(this);
        scrollView.setLayoutParams(new ViewGroup.LayoutParams(-1, -1));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(40, 40, 40, 80);
        root.setBackgroundColor(Color.parseColor("#FFF8E1"));
        root.setLayoutParams(new ViewGroup.LayoutParams(-1, -2));

        TextView title = new TextView(this);
        title.setText("喵喵助手");
        title.setTextSize(26.0f);
        title.setTextColor(Color.rgb(230, 81, 0));
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(17);
        title.setPadding(0, 40, 0, 8);
        root.addView(title);
        TextView subtitle = new TextView(this);
        subtitle.setText("任意聊天软件通用文本改写 · 逆向自 QQ 文本改写助手");
        subtitle.setTextSize(13.0f);
        subtitle.setTextColor(Color.rgb(141, 110, 99));
        subtitle.setGravity(17);
        subtitle.setPadding(0, 0, 0, 24);
        root.addView(subtitle);

        this.statusText = new TextView(this);
        this.statusText.setTextSize(16.0f);
        this.statusText.setGravity(17);
        this.statusText.setPadding(24, 18, 24, 18);
        this.statusText.setBackgroundColor(Color.WHITE);
        this.statusText.setTextColor(Color.rgb(51, 51, 51));
        root.addView(this.statusText);
        this.toggleButton = new Button(this);
        this.toggleButton.setTextSize(16.0f);
        this.toggleButton.setTextColor(Color.WHITE);
        this.toggleButton.setPadding(32, 16, 32, 16);
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(-1, -2);
        btnLp.setMargins(0, 16, 0, 0);
        this.toggleButton.setLayoutParams(btnLp);
        this.toggleButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MainActivity.this.openAccessibilitySettings();
            }
        });
        root.addView(this.toggleButton);
        root.addView(divider());

        // ========== 作用范围（新增：原版写死 QQ，这里改为可配置） ==========
        TextView scopeTitle = new TextView(this);
        scopeTitle.setText("作用范围");
        scopeTitle.setTextSize(18.0f);
        scopeTitle.setTextColor(Color.rgb(93, 64, 55));
        scopeTitle.setTypeface(Typeface.DEFAULT_BOLD);
        scopeTitle.setPadding(0, 16, 0, 12);
        root.addView(scopeTitle);

        TextView targetLabel = new TextView(this);
        targetLabel.setText("目标应用（每行一个包名；留空 = 所有应用）");
        targetLabel.setTextSize(14.0f);
        targetLabel.setTextColor(Color.rgb(51, 51, 51));
        targetLabel.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(targetLabel);
        this.etTargets = new EditText(this);
        this.etTargets.setInputType(131073);
        this.etTargets.setLines(3);
        this.etTargets.setMinLines(3);
        this.etTargets.setBackgroundColor(Color.WHITE);
        this.etTargets.setPadding(16, 12, 16, 12);
        this.etTargets.setHint("例如：com.tencent.mobileqq");
        this.etTargets.setText(joinLines(this.config.targetPackages));
        root.addView(this.etTargets);

        // 常用聊天软件一键填入
        LinearLayout presetRow = new LinearLayout(this);
        presetRow.setOrientation(LinearLayout.HORIZONTAL);
        presetRow.setPadding(0, 8, 0, 4);
        for (final String[] preset : CHAT_PRESETS) {
            Button b = new Button(this);
            b.setText(preset[0]);
            b.setTextSize(12.0f);
            b.setAllCaps(false);
            b.setTextColor(Color.rgb(93, 64, 55));
            b.setBackgroundColor(Color.rgb(255, 224, 178));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.setMargins(0, 0, 8, 0);
            b.setLayoutParams(lp);
            b.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    MainActivity.this.addPresetPackage(preset[1], preset[0]);
                }
            });
            presetRow.addView(b);
        }
        root.addView(presetRow);
        Button clearTargets = new Button(this);
        clearTargets.setText("清空（改为所有应用）");
        clearTargets.setTextSize(12.0f);
        clearTargets.setAllCaps(false);
        clearTargets.setTextColor(Color.rgb(93, 64, 55));
        clearTargets.setBackgroundColor(Color.rgb(255, 224, 178));
        clearTargets.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MainActivity.this.etTargets.setText("");
                Toast.makeText(MainActivity.this, "已清空目标应用（生效范围为所有应用）", 0).show();
            }
        });
        root.addView(clearTargets);

        TextView excludeLabel = new TextView(this);
        excludeLabel.setText("排除应用（每行一个包名；输入法/桌面已默认排除，始终生效）");
        excludeLabel.setTextSize(14.0f);
        excludeLabel.setTextColor(Color.rgb(51, 51, 51));
        excludeLabel.setTypeface(Typeface.DEFAULT_BOLD);
        excludeLabel.setPadding(0, 12, 0, 0);
        root.addView(excludeLabel);
        this.etExcludes = new EditText(this);
        this.etExcludes.setInputType(131073);
        this.etExcludes.setLines(3);
        this.etExcludes.setMinLines(3);
        this.etExcludes.setBackgroundColor(Color.WHITE);
        this.etExcludes.setPadding(16, 12, 16, 12);
        String excludesPrefill = joinLines(this.config.excludePackages);
        if (excludesPrefill.isEmpty()) {
            excludesPrefill = joinLines(CatConfig.DEFAULT_EXCLUDE_PACKAGES);
        }
        this.etExcludes.setText(excludesPrefill);
        root.addView(this.etExcludes);

        this.cbSendFallback = addCheckbox(root, "发送按钮兜底", "点击聊天软件的发送按钮时，对输入框做最后一次处理（原版仅支持 QQ 发送按钮，现为通用识别）", this.config.enableSendFallback);

        // ========== 处理模式 ==========
        TextView modeTitle = new TextView(this);
        modeTitle.setText("处理模式");
        modeTitle.setTextSize(18.0f);
        modeTitle.setTextColor(Color.rgb(93, 64, 55));
        modeTitle.setTypeface(Typeface.DEFAULT_BOLD);
        modeTitle.setPadding(0, 16, 0, 12);
        root.addView(modeTitle);
        LinearLayout modeRow = new LinearLayout(this);
        modeRow.setOrientation(LinearLayout.HORIZONTAL);
        modeRow.setPadding(0, 8, 0, 8);
        this.rbPunctuation = new CheckBox(this);
        this.rbPunctuation.setText("标点触发 (推荐)  ");
        this.rbPunctuation.setTextSize(16.0f);
        this.rbPunctuation.setTextColor(Color.rgb(51, 51, 51));
        this.rbPunctuation.setChecked(CatConfig.MODE_PUNCTUATION.equals(this.config.processingMode));
        this.rbPunctuation.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    MainActivity.this.rbRealtime.setChecked(false);
                }
            }
        });
        modeRow.addView(this.rbPunctuation);
        this.rbRealtime = new CheckBox(this);
        this.rbRealtime.setText("实时处理");
        this.rbRealtime.setTextSize(16.0f);
        this.rbRealtime.setTextColor(Color.rgb(51, 51, 51));
        this.rbRealtime.setChecked(CatConfig.MODE_REALTIME.equals(this.config.processingMode));
        this.rbRealtime.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    MainActivity.this.rbPunctuation.setChecked(false);
                }
            }
        });
        modeRow.addView(this.rbRealtime);
        root.addView(modeRow);
        TextView modeHint = new TextView(this);
        modeHint.setText("标点触发：打字时只在标点处立即处理\n实时处理：每输入一个字立即处理（体验可能较快）");
        modeHint.setTextSize(11.0f);
        modeHint.setTextColor(Color.rgb(161, 136, 127));
        modeHint.setPadding(0, 0, 0, 16);
        root.addView(modeHint);

        // ========== 功能开关 ==========
        TextView funcTitle = new TextView(this);
        funcTitle.setText("功能开关");
        funcTitle.setTextSize(18.0f);
        funcTitle.setTextColor(Color.rgb(93, 64, 55));
        funcTitle.setTypeface(Typeface.DEFAULT_BOLD);
        funcTitle.setPadding(0, 16, 0, 8);
        root.addView(funcTitle);
        this.cbAppend = addCheckbox(root, "断句追加", "在句号、叹号等标点分句后追加文本", this.config.enableAppend);
        this.etAppendText = new EditText(this);
        this.etAppendText.setInputType(131073);
        this.etAppendText.setBackgroundColor(Color.WHITE);
        this.etAppendText.setPadding(16, 12, 16, 12);
        this.etAppendText.setHint("追加内容（默认：喵）");
        this.etAppendText.setText(this.config.appendText != null ? this.config.appendText : "喵");
        LinearLayout.LayoutParams etLp1 = new LinearLayout.LayoutParams(-1, -2);
        etLp1.setMargins(0, 0, 0, 4);
        this.etAppendText.setLayoutParams(etLp1);
        root.addView(this.etAppendText);
        this.cbEmoticon = addCheckbox(root, "句末颜文字", "在消息末尾附加随机颜文字", this.config.enableRandomEmoticon);

        // ========== 文本替换规则 ==========
        TextView ruleTitle = new TextView(this);
        ruleTitle.setText("文本替换规则");
        ruleTitle.setTextSize(18.0f);
        ruleTitle.setTextColor(Color.rgb(93, 64, 55));
        ruleTitle.setTypeface(Typeface.DEFAULT_BOLD);
        ruleTitle.setPadding(0, 16, 0, 8);
        root.addView(ruleTitle);
        TextView ruleHint = new TextView(this);
        ruleHint.setText("每行一条，按顺序应用。格式：原词=替换词（也支持 ＝ 全角等号 / →）\n例：我=本喵 / 你＝主人 / 也支持数字等任意文本");
        ruleHint.setTextSize(12.0f);
        ruleHint.setTextColor(Color.rgb(141, 110, 99));
        ruleHint.setPadding(0, 0, 0, 12);
        root.addView(ruleHint);
        this.etRules = new EditText(this);
        this.etRules.setInputType(131073);
        this.etRules.setLines(6);
        this.etRules.setMinLines(6);
        this.etRules.setBackgroundColor(Color.WHITE);
        this.etRules.setPadding(16, 12, 16, 12);
        this.etRules.setText(CatConfig.rulesToString(this.config.rules));
        root.addView(this.etRules);

        // ========== 自定义颜文字 ==========
        TextView emojiTitle = new TextView(this);
        emojiTitle.setText("自定义颜文字");
        emojiTitle.setTextSize(18.0f);
        emojiTitle.setTextColor(Color.rgb(93, 64, 55));
        emojiTitle.setTypeface(Typeface.DEFAULT_BOLD);
        emojiTitle.setPadding(0, 16, 0, 8);
        root.addView(emojiTitle);
        TextView emojiHint = new TextView(this);
        emojiHint.setText("每行一个颜文字，留空则使用内置库");
        emojiHint.setTextSize(12.0f);
        emojiHint.setTextColor(Color.rgb(141, 110, 99));
        emojiHint.setPadding(0, 0, 0, 12);
        root.addView(emojiHint);
        this.etCustomEmoticons = new EditText(this);
        this.etCustomEmoticons.setInputType(131073);
        this.etCustomEmoticons.setLines(4);
        this.etCustomEmoticons.setMinLines(4);
        this.etCustomEmoticons.setBackgroundColor(Color.WHITE);
        this.etCustomEmoticons.setPadding(16, 12, 16, 12);
        this.etCustomEmoticons.setHint("例如: (=^w^=) 等");
        this.etCustomEmoticons.setText(joinLines(this.config.customEmoticons));
        root.addView(this.etCustomEmoticons);

        // ========== 保存 / 测试 ==========
        Button saveBtn = new Button(this);
        saveBtn.setText("保存设置");
        saveBtn.setTextSize(16.0f);
        saveBtn.setTextColor(Color.WHITE);
        saveBtn.setBackgroundColor(Color.rgb(255, 111, 0));
        saveBtn.setPadding(40, 16, 40, 16);
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(-1, -2);
        saveLp.setMargins(0, 16, 0, 0);
        saveBtn.setLayoutParams(saveLp);
        saveBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MainActivity.this.saveConfig();
            }
        });
        root.addView(saveBtn);
        Button testBtn = new Button(this);
        testBtn.setText("测试当前配置");
        testBtn.setTextSize(14.0f);
        testBtn.setTextColor(Color.rgb(255, 111, 0));
        testBtn.setBackgroundColor(Color.WHITE);
        testBtn.setPadding(40, 14, 40, 14);
        LinearLayout.LayoutParams testLp = new LinearLayout.LayoutParams(-1, -2);
        testLp.setMargins(0, 12, 0, 0);
        testBtn.setLayoutParams(testLp);
        testBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MainActivity.this.showTestDialog();
            }
        });
        root.addView(testBtn);
        TextView hint = new TextView(this);
        hint.setText("提示：修改设置后请点击保存，服务下次触发时自动加载");
        hint.setTextSize(11.0f);
        hint.setTextColor(Color.rgb(161, 136, 127));
        hint.setGravity(17);
        hint.setPadding(16, 36, 16, 8);
        root.addView(hint);

        scrollView.addView(root);
        setContentView(scrollView);
    }

    /** 将预置聊天软件包名追加到目标应用列表（去重） */
    private void addPresetPackage(String pkg, String label) {
        if (this.etTargets == null) {
            return;
        }
        String current = this.etTargets.getText() == null ? "" : this.etTargets.getText().toString().trim();
        String[] lines = current.isEmpty() ? new String[0] : current.split("\n");
        for (String line : lines) {
            if (line.trim().equals(pkg)) {
                Toast.makeText(this, label + " 已在列表中", 0).show();
                return;
            }
        }
        String next = current.isEmpty() ? pkg : current + "\n" + pkg;
        this.etTargets.setText(next);
        Toast.makeText(this, "已添加 " + label + "（" + pkg + "）", 0).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateServiceStatus();
    }

    private void updateServiceStatus() {
        if (this.statusText == null || this.toggleButton == null) {
            return;
        }
        boolean enabled = isAccessibilityServiceEnabled();
        if (enabled) {
            this.statusText.setText("服务状态：已开启");
            this.statusText.setTextColor(Color.rgb(46, 125, 50));
            this.toggleButton.setText("服务已开启");
            this.toggleButton.setEnabled(false);
            this.toggleButton.setBackgroundColor(Color.rgb(165, 214, 167));
            return;
        }
        this.statusText.setText("服务状态：未开启");
        this.statusText.setTextColor(Color.rgb(198, 40, 40));
        this.toggleButton.setText("前往开启无障碍服务");
        this.toggleButton.setEnabled(true);
        this.toggleButton.setBackgroundColor(Color.rgb(255, 111, 0));
    }

    private boolean isAccessibilityServiceEnabled() {
        try {
            AccessibilityManager am = (AccessibilityManager) getSystemService("accessibility");
            if (am == null) {
                return false;
            }
            List<AccessibilityServiceInfo> services = am.getEnabledAccessibilityServiceList(-1);
            for (AccessibilityServiceInfo info : services) {
                if (info.getResolveInfo() != null && info.getResolveInfo().serviceInfo != null && getPackageName().equals(info.getResolveInfo().serviceInfo.packageName)) {
                    return true;
                }
            }
        } catch (Exception e) {
        }
        return false;
    }

    public void openAccessibilitySettings() {
        try {
            Intent intent = new Intent("android.settings.ACCESSIBILITY_SETTINGS");
            intent.setFlags(268435456);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "无法打开设置", 0).show();
        }
    }

    private CheckBox addCheckbox(LinearLayout linearLayout, String title, String desc, boolean checked) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, 8, 0, 8);
        row.setGravity(16);
        CheckBox cb = new CheckBox(this);
        cb.setChecked(checked);
        row.addView(cb, new LinearLayout.LayoutParams(-2, -2));
        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setPadding(12, 0, 0, 0);
        TextView tvTitle = new TextView(this);
        tvTitle.setText(title);
        tvTitle.setTextSize(16.0f);
        tvTitle.setTextColor(Color.rgb(51, 51, 51));
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        textCol.addView(tvTitle);
        TextView tvDesc = new TextView(this);
        tvDesc.setText(desc);
        tvDesc.setTextSize(12.0f);
        tvDesc.setTextColor(Color.rgb(136, 136, 136));
        textCol.addView(tvDesc);
        row.addView(textCol, new LinearLayout.LayoutParams(0, -2, 1.0f));
        linearLayout.addView(row);
        return cb;
    }

    private View divider() {
        View v = new View(this);
        v.setBackgroundColor(Color.rgb(221, 221, 221));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, 2);
        lp.setMargins(0, 24, 0, 8);
        v.setLayoutParams(lp);
        return v;
    }

    private String joinLines(String[] arr) {
        if (arr == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String s : arr) {
            if (s == null) {
                continue;
            }
            String t = s.trim();
            if (t.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append("\n");
            }
            sb.append(t);
        }
        return sb.toString();
    }

    public void saveConfig() {
        try {
            this.config.enableAppend = this.cbAppend.isChecked();
            String append = this.etAppendText.getText().toString().trim();
            this.config.appendText = append.isEmpty() ? "喵" : append;
            this.config.enableRandomEmoticon = this.cbEmoticon.isChecked();
            this.config.enableSendFallback = this.cbSendFallback.isChecked();
            this.config.processingMode = this.rbRealtime.isChecked() ? CatConfig.MODE_REALTIME : CatConfig.MODE_PUNCTUATION;

            ArrayList<CatConfig.Rule> rules = new ArrayList<>();
            String rulesText = this.etRules.getText() == null ? "" : this.etRules.getText().toString();
            for (String line : rulesText.split("\n")) {
                CatConfig.Rule r = CatConfig.parseRule(line);
                if (r != null) {
                    rules.add(r);
                }
            }
            this.config.rules = rules;

            ArrayList<String> list = new ArrayList<>();
            String customText = this.etCustomEmoticons.getText() == null ? "" : this.etCustomEmoticons.getText().toString().trim();
            if (!customText.isEmpty()) {
                for (String raw : customText.split("\n")) {
                    String t = raw.trim();
                    if (!t.isEmpty()) {
                        list.add(t);
                    }
                }
            }
            this.config.customEmoticons = list.toArray(new String[0]);

            this.config.targetPackages = splitLines(this.etTargets.getText() == null ? "" : this.etTargets.getText().toString());
            this.config.excludePackages = splitLines(this.etExcludes.getText() == null ? "" : this.etExcludes.getText().toString());

            this.config.save(this);
            Toast.makeText(this, "设置已保存", 0).show();
        } catch (Exception e) {
            Toast.makeText(this, "保存失败: " + e.getMessage(), 0).show();
        }
    }

    private String[] splitLines(String s) {
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

    public void showTestDialog() {
        try {
            saveConfig();
            CatConfig testCfg = CatConfig.load(this);
            String sample = "今天我很好，你准备好了吗？我们去公园玩吧";
            String processed = TextProcessor.process(sample, testCfg);
            String scope;
            if (testCfg.targetPackages.length > 0) {
                scope = "仅 " + testCfg.targetPackages.length + " 个目标应用";
            } else {
                scope = "所有应用（排除 " + (testCfg.excludePackages.length + CatConfig.DEFAULT_EXCLUDE_PACKAGES.length) + " 个默认排除项）";
            }
            String msg = "作用范围：" + scope
                    + "\n断句追加：" + yn(testCfg.enableAppend) + "（" + (testCfg.appendText == null ? "" : testCfg.appendText) + "）"
                    + "\n句末颜文字：" + yn(testCfg.enableRandomEmoticon)
                    + "\n发送按钮兜底：" + yn(testCfg.enableSendFallback)
                    + "\n替换规则：" + testCfg.rules.size() + " 条"
                    + "\n自定义颜文字：" + (testCfg.customEmoticons.length > 0 ? testCfg.customEmoticons.length + "个" : "使用内置")
                    + "\n\n原始：\n" + sample
                    + "\n\n处理后：\n" + processed;
            new AlertDialog.Builder(this).setTitle("预览").setMessage(msg).setPositiveButton("好的", null).show();
        } catch (Exception e) {
            Toast.makeText(this, "测试失败: " + e.getMessage(), 0).show();
        }
    }

    private String yn(boolean b) {
        return b ? "开" : "关";
    }
}
