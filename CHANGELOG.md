# Changelog

本文档记录 [MinecraftBoxLock](https://github.com/Lexcubia/MinecraftBoxLock) 仓库中 **XlingranAuth** 插件的发行说明。

## 0.0.1

首次以 **0.0.1** 作为 Maven / `plugin.yml` 对外版本号发布。

### 功能与行为

- 创作者认证流程：`/xauth`、进服可选弹窗、GUI 确认/关闭、已认证 UUID 持久化（`authenticated.yml`）。
- 多版本适配：**`PlatformBridge`** 在启用时探测并缓存 Bukkit / Adventure 的 `createInventory` 与 `ItemMeta` 展示名；目标约 **1.12.x–Paper 26.1.x**。
- GUI 染色玻璃：**`Material.matchMaterial`** + 1.12.x `STAINED_GLASS_PANE` 染料值，避免旧版 XSeries 无法解析 Paper **26.x** 版本号导致崩溃。

### 工程与构建

- **Maven Wrapper**（`mvnw` / `mvnw.cmd`）、`.mvn/wrapper`；CI 使用 `./mvnw clean package`。
- 本地调试脚本 `scripts/start-debug-server.ps1`；`.vscode` 共享任务与 Java 附加配置。
- `.gitignore` 排除 `.debug-server*` 与常见 IDE/构建产物。

### 产物

- `target/XlingranAuth-0.0.1.jar`：放入服务端 `plugins/` 即可。
