import com.meow.assistant.CatConfig;
import com.meow.assistant.TextProcessor;
import java.util.ArrayList;
import java.util.List;

/**
 * 喵喵助手引擎行为测试（编译运行 app 中逐字节一致的 TextProcessor.java + 纯 Java CatConfig）。
 *
 * 编译：javac -encoding UTF-8 -d out src/com/meow/assistant/*.java src/EngineTest.java
 * 运行：java -cp out EngineTest
 */
public class EngineTest {
    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        System.out.println("喵喵助手 · Java 引擎测试");
        System.out.println("=========================");

        testDefaultConfig();
        testRulesOnly();
        testRuleParsing();
        testCustomEmoticons();
        testScopeWhitelist();
        testScopeBlacklist();
        testRulesToString();

        System.out.println("\n=========================");
        System.out.println("通过 " + passed + " 项，失败 " + failed + " 项");
        if (failed > 0) {
            System.exit(1);
        }
        System.out.println("✔ 全部通过");
    }

    /** 默认配置：我=本喵 / 你=主人 + 断句追加喵 + 随机颜文字 */
    private static void testDefaultConfig() {
        CatConfig cfg = new CatConfig();
        cfg.rules.add(new CatConfig.Rule("我", "本喵"));
        cfg.rules.add(new CatConfig.Rule("你", "主人"));
        String out = TextProcessor.process("今天我很好，你准备好了吗？我们去公园玩吧", cfg);
        check("替换规则生效", out.contains("今天本喵很好喵") && out.contains("主人准备好了吗喵"));
        check("断句追加生效", out.contains("今天本喵很好喵，") && out.contains("主人准备好了吗喵？"));
        check("句末随机颜文字（来自内置库）", endsWithBuiltinEmoticon(out));
        System.out.println("  改写结果: " + out);

        // 只关闭颜文字，验证可精确预期（注意：朴素替换会把"我们"→"本喵们"，与原版一致）
        cfg.enableRandomEmoticon = false;
        String out2 = TextProcessor.process("今天我很好，你准备好了吗？我们去公园玩吧", cfg);
        check("无颜文字输出精确匹配",
                "今天本喵很好喵，主人准备好了吗喵？本喵们去公园玩吧喵".equals(out2));
        System.out.println("  无颜文字: " + out2);
    }

    /** 只做规则替换 */
    private static void testRulesOnly() {
        CatConfig cfg = new CatConfig();
        cfg.enableAppend = false;
        cfg.enableRandomEmoticon = false;
        cfg.rules.add(new CatConfig.Rule("我", "本喵"));
        cfg.rules.add(new CatConfig.Rule("你", "主人"));
        cfg.rules.add(new CatConfig.Rule("好", "妙"));
        String out = TextProcessor.process("你好，我很好，今天天气真好", cfg);
        check("规则替换顺序应用", "主人妙，本喵很妙，今天天气真妙".equals(out));
        System.out.println("  规则替换: " + out);
    }

    private static void testRuleParsing() {
        check("半角等号", CatConfig.parseRule("我=本喵") != null && "本喵".equals(CatConfig.parseRule("我=本喵").to));
        check("全角等号", CatConfig.parseRule("你＝主人") != null && "主人".equals(CatConfig.parseRule("你＝主人").to));
        check("箭头分隔", CatConfig.parseRule("好→妙") != null && "妙".equals(CatConfig.parseRule("好→妙").to));
        check("无分隔符返回 null", CatConfig.parseRule("noseparator") == null);
        check("空原词返回 null", CatConfig.parseRule("=值") == null);
        check("空行返回 null", CatConfig.parseRule("   ") == null);
    }

    private static void testCustomEmoticons() {
        CatConfig cfg = new CatConfig();
        cfg.enableAppend = false;
        cfg.enableRandomEmoticon = true;
        cfg.customEmoticons = new String[]{"(=^･ω･^=)", "ฅ(≧▽≦)ฅ"};
        String out = TextProcessor.process("今天也要加油", cfg);
        boolean ok = out.equals("今天也要加油 (=^･ω･^=)") || out.equals("今天也要加油 ฅ(≧▽≦)ฅ");
        check("自定义颜文字库", ok);
        System.out.println("  自定义颜文字: " + out);
    }

    /** 作用范围：白名单非空 → 只处理白名单 */
    private static void testScopeWhitelist() {
        CatConfig cfg = new CatConfig();
        cfg.targetPackages = new String[]{"com.tencent.mobileqq", "com.tencent.mm"};
        check("白名单内 QQ 放行", cfg.shouldHandlePackage("com.tencent.mobileqq"));
        check("白名单内微信放行", cfg.shouldHandlePackage("com.tencent.mm"));
        check("白名单外排除", !cfg.shouldHandlePackage("com.whatsapp"));
        // 白名单模式只匹配白名单：输入法不在其中 → 不放行
        check("输入法不在白名单则不放行", !cfg.shouldHandlePackage("com.android.inputmethod.latin"));
    }

    /** 作用范围：空白名单（所有应用）→ 默认排除列表 + 用户排除列表生效 */
    private static void testScopeBlacklist() {
        CatConfig cfg = new CatConfig();
        check("默认排除输入法", !cfg.shouldHandlePackage("com.android.inputmethod.latin"));
        check("默认排除搜狗输入法", !cfg.shouldHandlePackage("com.sohu.inputmethod.sogou"));
        check("默认排除桌面", !cfg.shouldHandlePackage("com.miui.home"));
        check("默认排除系统设置", !cfg.shouldHandlePackage("com.android.settings"));
        check("普通聊天应用放行", cfg.shouldHandlePackage("com.tencent.mobileqq"));
        check("微信放行", cfg.shouldHandlePackage("com.tencent.mm"));
        check("Telegram 放行", cfg.shouldHandlePackage("org.telegram.messenger"));
        check("WhatsApp 放行", cfg.shouldHandlePackage("com.whatsapp"));
        cfg.excludePackages = new String[]{"org.telegram.messenger"};
        check("用户排除列表生效", !cfg.shouldHandlePackage("org.telegram.messenger"));
        check("用户排除不影响其他应用", cfg.shouldHandlePackage("com.whatsapp"));
    }

    private static void testRulesToString() {
        List<CatConfig.Rule> rules = new ArrayList<>();
        rules.add(new CatConfig.Rule("我", "本喵"));
        rules.add(new CatConfig.Rule("你", "主人"));
        String s = CatConfig.rulesToString(rules);
        check("rulesToString", "我=本喵\n你=主人".equals(s));
    }

    private static boolean endsWithBuiltinEmoticon(String s) {
        for (String em : CatConfig.BUILTIN_EMOTICONS) {
            if (s.endsWith(" " + em)) {
                return true;
            }
        }
        return false;
    }

    private static void check(String name, boolean cond) {
        if (cond) {
            passed++;
            System.out.println("  ✔ " + name);
        } else {
            failed++;
            System.out.println("  ✘ 失败: " + name);
        }
    }
}
