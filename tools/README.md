# 喵喵助手 · 工具与验证

## engine-test/ —— Java 引擎测试（真实源码级验证）

`src/com/meow/assistant/TextProcessor.java` 是从 app 主源码**逐字节复制**的
（SHA-256 与 `app/src/main/java/com/meow/assistant/TextProcessor.java` 一致），
`CatConfig.java` 为去掉 Android 依赖的纯 Java 变体（公开 API 与 app 版一致），
`EngineTest.java` 覆盖：规则替换、断句追加、随机颜文字、规则解析、作用范围白名单/黑名单。

```bash
cd tools/engine-test
javac -encoding UTF-8 -d out src/com/meow/assistant/*.java src/EngineTest.java
java -cp out EngineTest
```

## meow_engine_demo.py —— Python 复刻版演示（无需 JDK）

忠实复刻引擎算法与服务端跟踪逻辑（前缀增量 / 逆向剥离 / 回显跳过），
可在任意装有 Python 3 的机器上体验与验证改写效果：

```bash
python meow_engine_demo.py              # 运行内置演示用例
python meow_engine_demo.py 你好，你是谁？ # 用默认配置改写一段文本
```

> 说明：演示输出与 Android 版行为一致——标点触发仅认 。！!？? 与空格结尾，
> 逗号不触发；颜文字剥离可精确还原，追加的"喵"依赖服务端前缀增量跟踪（见 REVERSE.md）。
