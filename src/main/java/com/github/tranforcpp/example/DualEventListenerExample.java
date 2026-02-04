package com.github.tranforcpp.example;

import com.github.tranforcpp.TranforCPlusPlus;
import com.github.tranforcpp.ProcessManager.GenericTranforCEvent;
import com.github.tranforcpp.channel.PluginMessagingManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.charset.StandardCharsets;

public class DualEventListenerExample extends JavaPlugin implements Listener, PluginMessageListener {
    
    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);

        PluginMessagingManager messagingManager = TranforCPlusPlus.getInstance().getMessagingManager();
        if (messagingManager != null) {
            getServer().getMessenger().registerIncomingPluginChannel(
                this, 
                PluginMessagingManager.CHANNEL_TRANFORCPP, 
                this
            );
        }
        
        getLogger().info("TranforC++ 双方案事件监听示例已启用");
    }
    
    @EventHandler
    public void onTranforCEvent(GenericTranforCEvent event) {
        getLogger().info("[方案A] 收到本地事件: " + event.getEventName());
        
        switch (event.getEventName()) {
            case "PlayerJoin":
                handleLocalPlayerJoin(event);
                break;
            case "BlockBreak":
                handleLocalBlockBreak(event);
                break;
        }
    }
    
    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (channel.equals(PluginMessagingManager.CHANNEL_TRANFORCPP)) {
            getLogger().info("[方案B] 收到跨服务器事件");
            
            try {
                String jsonData = new String(message, StandardCharsets.UTF_8);
                getLogger().info("收到数据: " + jsonData);
            } catch (Exception e) {
                getLogger().warning("处理跨服务器消息失败: " + e.getMessage());
            }
        }
    }
    
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