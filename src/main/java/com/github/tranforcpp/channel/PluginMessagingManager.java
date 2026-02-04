package com.github.tranforcpp.channel;

import com.github.tranforcpp.TranforCPlusPlus;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

public class PluginMessagingManager implements PluginMessageListener {
    
    public static final String CHANNEL_TRANFORCPP = "tranforcpp:events";
    public static final String CHANNEL_BUNGEECORD = "BungeeCord";
    
    private final TranforCPlusPlus plugin;
    private final Gson gson = new Gson();
    private final Set<String> registeredChannels = new HashSet<>();
    private final AtomicLong messageCounter = new AtomicLong(0);
    private boolean initialized = false;
    
    public PluginMessagingManager(TranforCPlusPlus plugin) {
        this.plugin = plugin;
    }
    
    public void initialize() {
        registerChannel(CHANNEL_TRANFORCPP);
        registerChannel(CHANNEL_BUNGEECORD);
        
        initialized = true;
    }
    
    private void registerChannel(String channel) {
        if (registeredChannels.contains(channel)) {
            return;
        }
        
        try {
            plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, channel);
            plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, channel, this);
            registeredChannels.add(channel);
        } catch (Exception e) {
            plugin.getLogger().warning("注册消息通道失败 " + channel + ": " + e.getMessage());
        }
    }
    
    public void broadcastEvent(String eventName, Object... args) {
        if (!initialized) {
            return;
        }
        
        try {
            JsonObject message = new JsonObject();
            message.addProperty("type", "event");
            message.addProperty("event", eventName);
            message.addProperty("timestamp", System.currentTimeMillis());
            
            com.google.gson.JsonArray argsArray = new com.google.gson.JsonArray();
            for (Object arg : args) {
                argsArray.add(arg != null ? arg.toString() : "null");
            }
            message.add("args", argsArray);
            
            String jsonData = gson.toJson(message);
            byte[] data = jsonData.getBytes(StandardCharsets.UTF_8);
            
            Player[] players = Bukkit.getOnlinePlayers().toArray(new Player[0]);
            int sentCount = 0;
            
            for (Player player : players) {
                if (player != null && player.isOnline()) {
                    try {
                        player.sendPluginMessage(plugin, CHANNEL_TRANFORCPP, data);
                        sentCount++;
                    } catch (Exception e) {
                        plugin.getLogger().warning("向玩家 " + player.getName() + " 发送消息失败: " + e.getMessage());
                    }
                }
            }
            
            messageCounter.addAndGet(sentCount);
            if (sentCount > 0) {
                plugin.getLogger().fine("广播事件: " + eventName + " (发送给 " + sentCount + " 个玩家)");
            }
            
        } catch (Exception e) {
            plugin.getLogger().warning("广播事件失败 " + eventName + ": " + e.getMessage());
        }
    }
    
    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!initialized || !channel.equals(CHANNEL_TRANFORCPP)) {
            return;
        }
        
        try {
            String jsonData = new String(message, StandardCharsets.UTF_8);
            JsonObject json = gson.fromJson(jsonData, JsonObject.class);
            
            if (json == null || !json.has("type")) {
                return;
            }
            
            String type = json.get("type").getAsString();
            
            if ("event".equals(type) && json.has("event")) {
                String eventName = json.get("event").getAsString();
                messageCounter.incrementAndGet();
                plugin.getLogger().fine("收到来自 " + player.getName() + " 的事件: " + eventName);
            }
            
        } catch (Exception e) {
            plugin.getLogger().warning("处理插件消息失败: " + e.getMessage());
        }
    }
    
    public void cleanup() {
        if (!initialized) {
            return;
        }
        
        initialized = false;
        long totalMessages = messageCounter.get();
        
        for (String channel : registeredChannels) {
            try {
                plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin, channel);
                plugin.getServer().getMessenger().unregisterIncomingPluginChannel(plugin, channel, this);
            } catch (Exception e) {
                plugin.getLogger().warning("注销消息通道失败 " + channel + ": " + e.getMessage());
            }
        }
        registeredChannels.clear();
        messageCounter.set(0);
        
        plugin.getLogger().info("插件消息通道已清理，总计处理消息: " + totalMessages);
    }
}