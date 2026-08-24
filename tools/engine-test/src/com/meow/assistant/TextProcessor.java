package com.meow.assistant;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文本改写引擎 —— 从原版 QQMiaoAssistant 逆向移植，行为保持一致：
 * 1. 按顺序应用替换规则（原词=替换词）
 * 2. 断句追加（按 [，,。！!？?\s]+ 分句，句末追加自定义文本，默认"喵"）
 * 3. 句末附加随机颜文字（内置 50+ 个，可自定义）
 */
public class TextProcessor {
    private static final Random RANDOM = new Random();
    private static final Pattern SENTENCE_SPLIT_PATTERN = Pattern.compile("([，,。！!？?\\s]+)");

    public static String process(String original, CatConfig config) {
        if (original == null || original.trim().isEmpty()) {
            return original;
        }
        String text = original.trim();

        if (config.rules != null) {
            for (CatConfig.Rule rule : config.rules) {
                if (rule == null || rule.from.isEmpty()) {
                    continue;
                }
                text = text.replace(rule.from, rule.to);
            }
        }

        if (config.enableAppend) {
            text = appendPerSentence(text, config.appendText);
        }

        if (config.enableRandomEmoticon) {
            String emoticon = getRandomEmoticon(config);
            if (emoticon != null && !emoticon.isEmpty()) {
                text = text + " " + emoticon;
            }
        }
        return text;
    }

    private static String appendPerSentence(String text, String suffix) {
        String s = (suffix == null) ? "" : suffix;
        List<String> parts = new ArrayList<>();
        List<String> separators = new ArrayList<>();
        Matcher matcher = SENTENCE_SPLIT_PATTERN.matcher(text);
        int lastEnd = 0;
        while (matcher.find()) {
            parts.add(text.substring(lastEnd, matcher.start()));
            separators.add(matcher.group(1));
            lastEnd = matcher.end();
        }
        if (lastEnd < text.length()) {
            parts.add(text.substring(lastEnd));
        } else if (!parts.isEmpty() && lastEnd == text.length()) {
            parts.add("");
        }
        if (parts.isEmpty()) {
            parts.add(text);
        }
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            String part = parts.get(i).trim();
            if (!part.isEmpty()) {
                result.append(part);
                result.append(s);
            }
            if (i < separators.size()) {
                result.append(separators.get(i));
            }
        }
        String resultStr = result.toString().trim();
        if (resultStr.isEmpty()) {
            return text + s;
        }
        return resultStr;
    }

    private static String getRandomEmoticon(CatConfig config) {
        String[] emoticons = config.getActiveEmoticons();
        if (emoticons == null || emoticons.length == 0) {
            emoticons = CatConfig.BUILTIN_EMOTICONS;
        }
        return emoticons.length == 0 ? "" : emoticons[RANDOM.nextInt(emoticons.length)];
    }

    /** 默认配置快速入口（原版写死行为：我→本喵、你→主人 由规则列表承载，此处给出默认示例规则） */
    public static String process(String original) {
        CatConfig defaults = new CatConfig();
        defaults.enableAppend = true;
        defaults.appendText = "喵";
        defaults.enableRandomEmoticon = true;
        defaults.customEmoticons = new String[0];
        defaults.rules = new ArrayList<>();
        defaults.rules.add(new CatConfig.Rule("我", "本喵"));
        defaults.rules.add(new CatConfig.Rule("你", "主人"));
        return process(original, defaults);
    }
}
