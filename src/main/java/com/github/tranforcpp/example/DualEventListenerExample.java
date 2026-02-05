package com.github.tranforcpp.example;

import com.github.tranforcpp.TranforCPlusPlus;
import com.github.tranforcpp.ProcessManager.GenericTranforCEvent;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 本地事件监听器示例
 * <p>
 * 演示如何监听TranforC++插件的本地事件。
 * 展示了插件的事件处理能力。
 * <p>
 * 示例功能：
 * - 本地事件监听（PlayerJoin、BlockBreak等）
 * - 事件数据解析和处理
 */
public class DualEventListenerExample extends JavaPlugin implements Listener {
    
    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        
        getLogger().info("TranforC++ 本地事件监听示例已启用");
    }
    
    @EventHandler
    public void onTranforCEvent(GenericTranforCEvent event) {
        getLogger().info("收到本地事件: " + event.getEventName());
        
        switch (event.getEventName()) {
            case "PlayerJoin":
                handleLocalPlayerJoin(event);
                break;
            case "BlockBreak":
                handleLocalBlockBreak(event);
                break;
        }
    }
    
    // 移除了代理端支持 - 不再处理跨服务器消息
    
    private void handleLocalPlayerJoin(GenericTranforCEvent event) {
        if (event.getArgCount() > 0) {
            String playerName = event.getArg(0).toString();
            getLogger().info("玩家 " + playerName + " 加入了游戏");
        }
    }
    
    private void handleLocalBlockBreak(GenericTranforCEvent event) {
        if (event.getArgCount() > 1) {
            String playerName = event.getArg(0).toString();
            String blockType = event.getArg(1).toString();
            getLogger().info("玩家 " + playerName + " 破坏了方块: " + blockType);
        }
    }
}