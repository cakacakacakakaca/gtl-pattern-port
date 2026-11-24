# GTL Pattern Port 1.0.2

Minecraft 1.20.1 / Forge 47 / Java 17。增强 gtlcore:me_pattern_buffer_copy。

下载 [gtl-pattern-port-1.20.1-1.0.2.jar](https://github.com/cakacakacakakaca/gtl-pattern-port/releases/tag/v1.0.2)。

## 安装与升级

关闭游戏，停用旧版 gtl-pattern-port JAR，放入 gtl-pattern-port-1.20.1-1.0.2.jar。只启用一个版本。多人游戏两端均需升级到 1.0.2，通信版本已更新，避免混用新旧导入逻辑。

已有工具和 .gtlp 文件可继续使用，无需重新制作或导出。

## 使用

1. Shift + 右键样板总成，用原工具复制样板。
2. 主手持有工具，按 O 打开文件管理（可在按键设置中修改）。
3. 输入不含扩展名的文件名，点击“导出当前样板”。文件位于当前游戏实例的 gtl-patterns 文件夹。同名导出不会覆盖。
4. 换存档后，主手持有同种工具，按 O，选择文件并确认导入。
5. 导入直接覆盖原工具保存的数据，不追加，不在工具内生成备份。成功时同时移除旧版 gtlpatternport_backup 字段。想保留原数据，请先手动导出。
6. 右键目标样板总成粘贴，仍使用原模组的空白样板消耗规则。粘贴后保留样板数据，便于再次粘贴。

“导出旧版遗留备份”只用于读取 1.0.0 / 1.0.1 留下的备份；在新一次成功导入前仍可导出。

客户端与服务器都校验数据；校验失败不会修改工具。跨实例或电脑迁移时，复制 .gtlp 到目标实例的 gtl-patterns。多人模式文件保存在玩家电脑。

## 1.0.2 修复说明（2026-11-18）

在 GTLE 3.9.2 中复现了导入失败：NBT 读取分配量超过 2097152 字节。排查确认加载的是 1.0.1。

实际测试的 .gtlp 文件包含 244 个样板，单份完整工具数据的 NBT 读取计费约 2415622 字节，即约 2.30 MiB。不是再次导入造成追加，去掉备份也不能解决这份文件超限。

GTLCore 1.2.3.3-fix4 内置 Packet Fixer 2.0.0。实例配置 allSizesUnlimited=true，日志确认它修改了游戏的 FriendlyByteBuf NBT 读取方法；旧附属模组却独立写死 2 MiB 检查，因此拒绝了游戏能够读取的数据。

新版本使用实际游戏网络 NBT 读取方法进行校验，跟随已加载的 Packet Fixer 等兼容处理，而不自行更改全局设置；同时按用户要求改为直接覆盖并移除旧备份。

仍保留边界：压缩文件最多 1 MiB，解压最多 4 MiB，最多 4096 个样板，完整工具序列化最多 1 MiB，本模组独立 NBT 读取计费最多 16 MiB。即使 Packet Fixer 设置为无限制，也保留本地上限。普通未扩展环境继续遵循游戏 2 MiB 网络额度；需分批迁移超限数据。客户端与服务器设置应一致。

失败日志现在包含异常堆栈，便于后续定位。没有改变样板 ID、物品 NBT、缓存数量、顺序和原生粘贴逻辑。

## 验证状态

- Forge 编译与生产环境重映射通过。
- 46 项通用数据断言通过。
- 使用实例内 7 个真实 .gtlp 文件，共 360 项断言通过。每个文件连续覆盖 12 次，核对完整内容一致、不保留旧备份、无关字段保留、失败不修改原对象。
- 测试重现默认 2 MiB 读取器拒绝超限文件；使用与实例 Packet Fixer 配置一致的读取预算通过。另测试无限网络额度下仍执行 16 MiB 本地上限。
- 以上是离线数据及兼容读取路径测试，Packet Fixer 预算通过测试适配器模拟；尚未重新启动整合包进行游戏内导入和粘贴实测。

## 构建

JDK 17，运行 gradlew.bat build（Windows）或 sh gradlew build。首次构建需联网。输出 build/libs/gtl-pattern-port-1.20.1-1.0.2.jar。

通用测试：gradlew.bat codecTest。
实例回归可运行 gradlew.bat instanceTest 并通过 -PinstancePath 指向测试实例。此任务使用原回归用例中的样本文件，样本文件由测试者自行提供。

MIT 许可。本项目不包含 GTLCore 源码。格式依据为 GTLCore 的 MEPatternBufferCopyBehavior 与 MEPatternBufferPartMachine.copyToTag/copyFromTag；目标存档应有相同的物品、配方及模组。
