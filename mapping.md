# Spigot API 映射到 C++

本文档说明如何将 Spigot 事件和功能映射到 C++ API。[其他api也可以,请参考本文档]: # 

## 添加新的事件映射

### 1. Java 端 (ProcessManager.java)

在 `ProcessManager` 类中添加 Spigot 事件监听器：

```java
@EventHandler
public void onBlockBreak(org.bukkit.event.block.BlockBreakEvent event) {
    sendEvent("BlockBreak", event.getPlayer().getName(), event.getBlock().getType().name());
}

@EventHandler
public void onPlayerMove(org.bukkit.event.player.PlayerMoveEvent event) {
    sendEvent("PlayerMove", event.getPlayer().getName());
}

@EventHandler
public void onBlockPlace(org.bukkit.event.block.BlockPlaceEvent event) {
    sendEvent("BlockPlace", event.getPlayer().getName(), event.getBlock().getType().name());
}
```

### 2. C++ API 头文件 (tranforcpp_api.h)

添加事件函数声明：

```cpp
extern "C" {
    // 原有事件
    void onPlayerJoin(const char* playerName);
    void onPlayerQuit(const char* playerName);
    void onPlayerChat(const char* playerName, const char* message);
    void onShutdown();

    // 新增事件
    void onBlockBreak(const char* playerName, const char* blockType);
    void onPlayerMove(const char* playerName);
    void onBlockPlace(const char* playerName, const char* blockType);
}
```

### 3. C++ 实现文件 (你的 .cpp 文件)

在 `main()` 函数的 while 循环中添加 JSON 解析逻辑：

```cpp
else if (eventName == "BlockBreak") {
    size_t start = line.find("\"", argsPos) + 1;
    size_t end = line.find("\"", start);
    std::string playerName = line.substr(start, end - start);

    start = line.find("\"", end + 1) + 1;
    end = line.find("\"", start);
    std::string blockType = line.substr(start, end - start);

    onBlockBreak(playerName.c_str(), blockType.c_str());
}
else if (eventName == "PlayerMove") {
    size_t start = line.find("\"", argsPos) + 1;
    size_t end = line.find("\"", start);
    std::string playerName = line.substr(start, end - start);

    onPlayerMove(playerName.c_str());
}
else if (eventName == "BlockPlace") {
    size_t start = line.find("\"", argsPos) + 1;
    size_t end = line.find("\"", start);
    std::string playerName = line.substr(start, end - start);

    start = line.find("\"", end + 1) + 1;
    end = line.find("\"", start);
    std::string blockType = line.substr(start, end - start);

    onBlockPlace(playerName.c_str(), blockType.c_str());
}
```

然后实现事件处理函数：

```cpp
void onBlockBreak(const char* playerName, const char* blockType) {
    std::string msg = std::string(playerName) + " 破坏了 " + std::string(blockType);
    broadcast(msg.c_str());
}

void onPlayerMove(const char* playerName) {
    // 处理玩家移动
}

void onBlockPlace(const char* playerName, const char* blockType) {
    std::string msg = std::string(playerName) + " 放置了 " + std::string(blockType);
    broadcast(msg.c_str());
}
```

## 数据类型映射

| Java 类型 | C++ 类型 | 说明 |
|-----------|-----------|------|
| `String` | `const char*` | 字符串通过 JSON 传递 |
| `int` | `const char*` | 转换为字符串后传递，需要 atoi 转换 |
| `boolean` | `const char*` | 转换为 "true" 或 "false" |
| `Player` | `const char* playerName` | 只传递玩家名称 |
| `Block` | `const char* blockType` | 只传递方块类型名称 |

## 常用 Spigot 事件

### 玩家相关事件

| Java 事件 | C++ 函数 | 参数 |
|----------|----------|------|
| `PlayerJoinEvent` | `onPlayerJoin` | `playerName` |
| `PlayerQuitEvent` | `onPlayerQuit` | `playerName` |
| `PlayerChatEvent` | `onPlayerChat` | `playerName`, `message` |
| `PlayerMoveEvent` | `onPlayerMove` | `playerName` |
| `PlayerDeathEvent` | `onPlayerDeath` | `playerName` |
| `PlayerRespawnEvent` | `onPlayerRespawn` | `playerName` |

### 方块相关事件

| Java 事件 | C++ 函数 | 参数 |
|----------|----------|------|
| `BlockBreakEvent` | `onBlockBreak` | `playerName`, `blockType` |
| `BlockPlaceEvent` | `onBlockPlace` | `playerName`, `blockType` |
| `BlockIgniteEvent` | `onBlockIgnite` | `playerName`, `blockType` |

### 实体相关事件

| Java 事件 | C++ 函数 | 参数 |
|----------|----------|------|
| `EntityDamageEvent` | `onEntityDamage` | `entityName`, `damage` |
| `EntityDeathEvent` | `onEntityDeath` | `entityName` |

## C++ API 函数

### 发送消息

```cpp
void broadcast(const char* message);           // 广播消息到所有玩家
void sendMsg(const char* player, const char* message);  // 发送消息给指定玩家
void console(const char* message);              // 输出到控制台
```

## JSON 通信格式

### Java → C++ (事件)

```json
{"event":"PlayerJoin","args":["PlayerName"]}
{"event":"PlayerChat","args":["PlayerName","message content"]}
{"event":"BlockBreak","args":["PlayerName","STONE"]}
```

### C++ → Java (动作)

```json
{"action":"broadcast","message":"Hello world!"}
{"action":"sendMessage","player":"PlayerName","message":"Hi!"}
{"action":"console","message":"Log message"}
```

## 注意事项

1. 所有字符串都通过 `const char*` 传递
2. 数值类型需要从字符串转换：`std::atoi(str)` 或 `std::stod(str)`
3. JSON 解析需要正确处理转义字符
4. 修改后需要重新编译 JAR 文件
5. 用户代码修改后使用 `/tranforcpp reload` 重载