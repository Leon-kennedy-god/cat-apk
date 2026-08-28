package com.meow.assistant;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;

public class ClipboardRewriter {
    public static String rewrite(Context ctx) {
        try {
            ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm == null) {
                return "系统剪贴板不可用";
            }
            ClipData clip = cm.getPrimaryClip();
            if (clip == null || clip.getItemCount() == 0) {
                return "剪贴板没有内容";
            }
            CharSequence cs = clip.getItemAt(0).getText();
            if (cs == null) {
                return "剪贴板没有文本";
            }
            String raw = cs.toString();
            if (raw.trim().isEmpty()) {
                return "剪贴板没有文本";
            }
            CatConfig cfg = CatConfig.load(ctx);
            String result = TextProcessor.process(raw, cfg);
            if (result == null || result.trim().isEmpty()) {
                result = raw;
            }
            cm.setPrimaryClip(ClipData.newPlainText("喵喵助手", result));
            if (result.equals(raw)) {
                return "已改写（规则未命中，内容不变）";
            }
            return "已改写，去粘贴吧";
        } catch (Exception e) {
            return "改写失败：" + e.getMessage();
        }
    }
}