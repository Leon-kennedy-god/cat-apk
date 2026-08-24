#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
喵喵助手 · 文本改写引擎演示工具（Python 版）
=============================================
忠实复刻 MeowMeowAssistant 的改写算法（TextProcessor.java）与服务端跟踪逻辑
（MeowAccessibilityService.java），用于：
  1. 在电脑上即时体验"喵喵助手"的改写效果（无需 Android）
  2. 验证核心算法行为（规则替换 → 断句追加 → 随机颜文字 → 逆向剥离 → 前缀增量跟踪）

用法：
  python meow_engine_demo.py            # 运行内置演示用例
  python meow_engine_demo.py "你的文本" # 用默认配置改写一段文本
"""
import random
import re
import sys

# ---- 与 CatConfig.BUILTIN_EMOTICONS 一致的猫咪颜文字库（内置 53 个） ----
BUILTIN_EMOTICONS = [
    "^⌯𖥦⌯^ ੭ ^", "⌯'ㅅ'⌯", "=^𖥦^=", "⌯•ㅅ•⌯", "ฅ•̀∀•́ฅ",
    "ฅ ̳͒•ˑ̫• ̳͒ฅ♡", "ฅ(̳•·̫•̳ฅ)♡", "ฅ^••^ฅ", "=^•ω•^=", "₍^ >ヮ<^₎",
    "/ᐠ - ˕ -マ Ⳋ", "ฅ^•ﻌ•^ฅ", "ฅ՞•ﻌ•՞ฅ", "(ฅ´ω`ฅ)", "ฅ(*`ω´*)ฅ",
    "ฅ꒰ ⸝˶• •˶⸝꒱ฅ", "₍˄·͈༝·͈˄*₎◞ ̑̑", "!!^⌯𖥦⌯^ ੭!!", "₍^⸝⸝> ·̫ <⸝⸝ ^₎",
    "ฅ^._.^ฅ", "₍🎀˄•͈༝•͈˄₎ฅ˒˒", "^•͈༝•^ฅ", "꒰ఎ(^ . ֑ .^)໒꒱", "ฅ●ω●ฅ",
    "₍⸍⸌·͈༝·͈⸍⸌₎◞", "(>^ω^<)", "ฅ^-﹃-^ฅ", "^ ̳ට ̫ ට ̳^", "୧₍˄·͈༝·͈˄₎୨",
    "^ ̳ᴗ  ̫ ᴗ ̳^", "˓˓ก(⸍⸌̣ʷ̣̫⸍̣⸌₎ค˒˒", "ヽ(ฅ≧へ≦)ฅ", "(`･ω･´)ฅ",
    "(=^･ᴥ･^=)", "(^ω^ฅ)", "ฅ(≧▽≦)ฅ", "ฅ(=´▽`=)ฅ", "ヾ((๑˘ㅂ˘๑)ฅ",
    "(ฅ◑ω◑ฅ)", "(๑•̀ω•́ฅ)", "(ฅ>ω<*ฅ)", "(=^.^=)", "(=´ᴥ`)",
    "(=ↀωↀ=)", "(=^-ω-^=)", "ฅ(*°ω°*ฅ)", "ヽ(=^･ω･^=)丿", "(^•ᴥ•^)",
    "( Φ ω Φ )", "(=^x^=)", "ฅ( ̳• ◡ • ̳)ฅ", "o( =•ω•= )m", "~o( =∩ω∩= )m", "≡ω≡",
]

SENTENCE_SPLIT_RE = re.compile(r"([，,。！!？?\s]+)")

DEFAULT_RULES = [("我", "本喵"), ("你", "主人")]  # 原版写死行为


class Rule:
    def __init__(self, from_, to_):
        self.from_ = from_
        self.to = to_

    def __repr__(self):
        return "%s=%s" % (self.from_, self.to)


def parse_rule(line):
    """与 CatConfig.parseRule 一致：支持 = ＝ → 三种分隔符，取最靠前者"""
    if not line:
        return None
    s = line.strip()
    if not s:
        return None
    idx = -1
    for sep in "=＝→":
        p = s.find(sep)
        if p >= 0 and (idx < 0 or p < idx):
            idx = p
    if idx <= 0:
        return None
    from_ = s[:idx].strip()
    to = s[idx + 1:].strip()
    if not from_:
        return None
    return Rule(from_, to)


class Config:
    """纯 Python 版 CatConfig：字段与 Java 版一一对应"""

    def __init__(self):
        self.enable_append = True
        self.append_text = "喵"
        self.enable_random_emoticon = True
        self.processing_mode = "punctuation"
        self.custom_emoticons = []
        self.rules = []

    def get_active_emoticons(self):
        return self.custom_emoticons if self.custom_emoticons else BUILTIN_EMOTICONS


def append_per_sentence(text, suffix):
    r"""与 TextProcessor.appendPerSentence 一致：按 [，,。！!？?\s]+ 分句，句末追加"""
    s = suffix or ""
    parts, separators = [], []
    last_end = 0
    for m in SENTENCE_SPLIT_RE.finditer(text):
        parts.append(text[last_end:m.start()])
        separators.append(m.group(1))
        last_end = m.end()
    if last_end < len(text):
        parts.append(text[last_end:])
    elif parts and last_end == len(text):
        parts.append("")
    if not parts:
        parts.append(text)
    result = []
    for i, part in enumerate(parts):
        part = part.strip()
        if part:
            result.append(part)
            result.append(s)
        if i < len(separators):
            result.append(separators[i])
    result_str = "".join(result).strip()
    if not result_str:
        return text + s
    return result_str


def process(original, config):
    """与 TextProcessor.process 一致：规则替换 → 断句追加 → 随机颜文字"""
    if original is None or not original.strip():
        return original
    text = original.strip()

    for rule in config.rules:
        if rule is None or not rule.from_:
            continue
        text = text.replace(rule.from_, rule.to)

    if config.enable_append:
        text = append_per_sentence(text, config.append_text)

    if config.enable_random_emoticon:
        emoticons = config.get_active_emoticons()
        if emoticons:
            text = text + " " + random.choice(emoticons)
    return text


def strip_emoticons(text, config):
    """与 MeowAccessibilityService.stripAll 的颜文字剥离部分一致：
    按长度降序移除库内颜文字（及其前导空格），可精确还原"""
    if not text:
        return ""
    result = text
    emotes = list(config.get_active_emoticons())
    emotes.sort(key=len, reverse=True)
    for em in emotes:
        if not em:
            continue
        while True:
            idx = result.find(em)
            if idx < 0:
                break
            st = idx if (idx <= 0 or result[idx - 1] != " ") else idx - 1
            result = result[:st] + result[idx + len(em):]
    return result


def strip_all(text, config):
    r"""与 MeowAccessibilityService.stripAll 完整一致：
    剥离颜文字后，再把 3 个及以上连续符号/标点串替换为单个空格（复刻
    [\p{S}\p{So}\p{Sm}\p{Sk}\p{P}]{3,} 的语义：非字母数字、非中文、非空白字符）"""
    result = strip_emoticons(text, config)
    result = re.sub(r"\s*[^\w\u4e00-\u9fff\u3400-\u4dbf\s]{3,}\s*", " ", result)
    return result.strip()


def is_punctuation_ending(s):
    """与 MeowAccessibilityService.isPunctuationEnding 一致：。！!？? 或空格结尾"""
    if not s:
        return False
    return s[-1] in "。！!？? "


class TypingSession:
    """模拟 MeowAccessibilityService 的一次输入会话（标点触发模式）：
    userOriginal / lastSet / 前缀增量 / 回显跳过 逻辑与 Java 版一致"""

    def __init__(self, config, emoticon_every_write=True):
        self.cfg = config
        self.user_original = ""
        self.last_set = ""
        self.emoticon_every_write = emoticon_every_write

    def effective_config(self, is_send_click):
        if self.emoticon_every_write or is_send_click:
            return self.cfg
        # 实时模式打字中不加颜文字（原版 cloneConfigWithoutEmoticon 行为）
        c = Config()
        c.enable_append = self.cfg.enable_append
        c.append_text = self.cfg.append_text
        c.enable_random_emoticon = False
        c.custom_emoticons = self.cfg.custom_emoticons
        c.rules = self.cfg.rules
        return c

    def on_text_changed(self, raw):
        """返回服务写入输入框的文本（None 表示不处理）"""
        raw = raw.strip()
        if not raw:
            self.user_original = ""
            self.last_set = ""
            return None
        if not is_punctuation_ending(raw):
            return None  # 标点触发：未到标点不处理
        if raw == self.last_set:
            return None  # 回显跳过（服务端还有 600ms 时间窗）
        if not self.last_set or not raw.startswith(self.last_set):
            self.user_original = strip_all(raw, self.cfg)
        else:
            self.user_original += raw[len(self.last_set):]
        if not self.user_original:
            return None
        target = process(self.user_original, self.effective_config(False))
        if target == raw:
            self.last_set = target
            return None
        self.last_set = target
        return target

    def on_send_click(self, raw):
        """发送按钮兜底：实时模式下补上句末颜文字"""
        if not raw.strip() or raw == self.last_set:
            return None
        if not self.last_set or not raw.startswith(self.last_set):
            self.user_original = strip_all(raw, self.cfg)
        else:
            self.user_original += raw[len(self.last_set):]
        if not self.user_original:
            return None
        target = process(self.user_original, self.cfg)  # 兜底总是带颜文字
        self.last_set = target
        return target


def demo():
    print("=" * 64)
    print("喵喵助手 · 文本改写引擎演示（Python 复刻版）")
    print("=" * 64)

    cfg = Config()
    cfg.rules = [Rule(*r) for r in DEFAULT_RULES]

    print("\n【用例1】一次完整打字会话（标点触发模式）")
    print("  配置：规则 我=本喵/你=主人，断句追加'喵'，句末随机颜文字")
    print("  触发标点（与原版一致）：仅 。！!？? 或空格结尾才处理，逗号不触发\n")
    random.seed(42)  # 固定随机数，输出可复现
    session = TypingSession(cfg)
    for raw in ["今天", "今天我很好，", "今天我很好，你准备好了吗？", "今天我很好，你准备好了吗？我们去公园玩吧"]:
        out = session.on_text_changed(raw)
        print("  输入框内容 : %s" % raw)
        print("  服务写入   : %s" % (out if out is not None else "（未到标点，不处理）"))
        print("  userOriginal=%s  lastSet=%s\n" % (session.user_original, session.last_set))

    print("【用例2】逆向剥离精确性验证：剥离颜文字后应等于改写文本去掉颜文字")
    for s in ["今天本喵很好喵，主人准备好了吗喵？我们去公园玩吧喵 (=^･ᴥ･^=)",
              "你好喵，我是小明喵。 (ฅ>ω<*ฅ)"]:
        stripped = strip_emoticons(s, cfg)
        print("  改写 : %s" % s)
        print("  剥离 : %s" % stripped)
        print("  %s\n" % ("✔ 精确还原" if not any(e in stripped for e in cfg.get_active_emoticons()) else "✘"))
    assert not any(e in strip_emoticons("今天本喵很好喵 (=^･ᴥ･^=)", cfg) for e in cfg.get_active_emoticons())

    print("【用例3】只做规则替换（关闭追加与颜文字）")
    cfg2 = Config()
    cfg2.enable_append = False
    cfg2.enable_random_emoticon = False
    cfg2.rules = [parse_rule("我=本喵"), parse_rule("你＝主人"), parse_rule("好→妙")]
    print("  规则 : %s" % cfg2.rules)
    s = "你好，我很好，今天天气真好"
    out2 = process(s, cfg2)
    print("  原文 : %s" % s)
    print("  改写 : %s" % out2)
    assert out2 == "主人妙，本喵很妙，今天天气真妙"  # 你好→你妙（好→妙 也作用于"你好"）
    print("  ✔ 断言通过\n")

    print("【用例4】自定义颜文字库")
    cfg3 = Config()
    cfg3.enable_append = False
    cfg3.enable_random_emoticon = True
    cfg3.custom_emoticons = ["(=^･ω･^=)", "ฅ(≧▽≦)ฅ"]
    random.seed(7)
    s = "今天也要加油"
    out3 = process(s, cfg3)
    print("  原文 : %s" % s)
    print("  改写 : %s" % out3)
    print("  剥离 : %s" % strip_emoticons(out3, cfg3))
    assert strip_emoticons(out3, cfg3) == s
    print("  ✔ 断言通过\n")

    print("【用例5】规则解析（parseRule）")
    for line in ["我=本喵", "你＝主人", "好→妙", "no-separator", "=空原词", "  "]:
        print("  %-14r -> %s" % (line, parse_rule(line)))

    print("\n✔ 全部演示用例通过")


def main():
    if len(sys.argv) > 1:
        text = " ".join(sys.argv[1:])
        cfg = Config()
        cfg.rules = [Rule(*r) for r in DEFAULT_RULES]
        print(process(text, cfg))
    else:
        demo()


if __name__ == "__main__":
    main()
