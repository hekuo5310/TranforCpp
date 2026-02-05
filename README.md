# TranforC++

用 C++ 编写 Spigot 插件

---
## 赞助商
- [HKMC云](https://cloud.hkmc.online/) 请选择销售时选择Xiao_Q以获得Xiao_Q专属福利
- [各种AI免费api](https://qm.qq.com/q/XNgDgosu4M)
- D.R.C Minecraft服务器 [官网](https://www.drc-org.top/)
---

## 使用方法

## 1.编译

**使用 Maven 编译**

```bash
mvn clean package
```
---

**使用 Gradle 编译**

**目前还有问题，请使用maven**

```bash
./gradlew clean build
# Windows 使用
gradlew.bat clean build
```

---

## 1.不会编译?

**从 GitHub Release 下载**
1. 下载 `maven-tranforcpp` 或 `gradle-tranforcpp`
2. 将下载的 JAR 文件放入服务器的 `plugins/` 文件夹
3. 将生成的 `tranforcpp-版本号.jar` 放入服务器的 `plugins/` 文件夹

## 2.安装插件

1. 将 `tranforcpp_api.h` 复制到 `plugins/C+plugins` 文件夹

2. 使用 `/tranforcpp reload` 命令编译并重载插件



## 环境要求

- Java 21+ [Java向下兼容的]: #
- Spigot 的分支
- g++ 编译器（Windows 需要安装 MinGW-w64，Linux一般内置，未内置请安装 g++）

## 代码示例

<a herf="https://github.com/hekuo5310/tranforCgrass/blob/main/example_plugin.cpp" target="_blank">example_plugin.cpp</a>

## API 事件函数

### 玩家事件

- `void onPlayerJoin(const char* playerName)` - 玩家加入
- `void onPlayerQuit(const char* playerName)` - 玩家退出
- `void onPlayerChat(const char* playerName, const char* message)` - 玩家聊天
- `void onPlayerMove(const char* playerName)` - 玩家移动
- `void onPlayerRespawn(const char* playerName)` - 玩家重生
- `void onPlayerDeath(const char* playerName, const char* deathMessage)` - 玩家死亡
- `void onPlayerInteract(const char* playerName, const char* action, const char* itemType)` - 玩家交互
- `void onPlayerDropItem(const char* playerName, const char* itemType)` - 玩家丢弃物品
- `void onPlayerPickupItem(const char* playerName, const char* itemType)` - 玩家拾取物品

### 方块事件

- `void onBlockBreak(const char* playerName, const char* blockType)` - 方块被破坏
- `void onBlockPlace(const char* playerName, const char* blockType)` - 方块被放置
- `void onBlockIgnite(const char* playerName, const char* blockType)` - 方块被点燃

### 实体事件

- `void onEntityDamage(const char* entityName, const char* damage)` - 实体受伤
- `void onEntityDeath(const char* entityName)` - 实体死亡
- `void onEntitySpawn(const char* entityType)` - 实体生成

### 物品栏事件

- `void onInventoryClick(const char* playerName, const char* slot, const char* itemType)` - 点击物品栏
- `void onInventoryOpen(const char* playerName)` - 打开物品栏
- `void onInventoryClose(const char* playerName)` - 关闭物品栏

### 服务器事件

- `void onServerCommand(const char* sender, const char* command)` - 服务器命令

### 世界事件

- `void onWorldLoad(const char* worldName)` - 世界加载
- `void onWeatherChange(const char* worldName, const char* weatherState)` - 天气变化

### 其他事件

- `void onHangingBreak(const char* entityType, const char* cause)` - 悬挂实体被破坏
- `void onShutdown()` - 插件关闭

## API 辅助函数

- `void broadcast(const char* message)` - 广播消息到所有玩家
- `void sendMsg(const char* player, const char* message)` - 给指定玩家发送消息
- `void console(const char* message)` - 输出到控制台
- `void dispatchCommand(const char* command, bool sync = false)` - 执行 Minecraft 控制台指令
## 命令

- `/tranforcpp reload` - 重新编译并加载 C++ 插件（需要权限）
- `/tranforcpp version` - 查看插件版本

## 添加新事件映射

详见 [mapping.md](mapping.md) 文档

## 注意事项

1. 必须实现 `main()` 函数来处理 JSON 输入
2. 必须实现 `onShutdown()` 函数并设置 `tranforcpp::running = false`
3. JSON 参数解析需要根据事件类型正确处理参数数量
4. 修改 Java 端代码后需要重新编译 JAR
5. 修改 C++ 代码后使用 `/tranforcpp reload` 重载
6. 辛巴巴,巴鲁比亚
