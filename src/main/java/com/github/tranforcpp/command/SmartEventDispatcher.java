package com.github.tranforcpp.command;


import org.bukkit.Bukkit;

public class SmartEventDispatcher {
    private boolean useMessagingChannel;

    
    public SmartEventDispatcher() {

    }
    
    public void initialize() {
        useMessagingChannel = shouldEnableMessaging();
    }
    
    private boolean shouldEnableMessaging() {
        return isProxyEnvironment() || hasMultipleServers() || customConfigEnabled();
    }
    
    private boolean isProxyEnvironment() {
        try {
            if (System.getProperty("bungeecord") != null || 
                System.getProperty("velocity") != null) {
                return true;
            }

            try {
                Class<?> paperConfig = Class.forName("io.papermc.paper.configuration.GlobalConfiguration");
                Object instance = paperConfig.getMethod("get").invoke(null);
                Object proxies = instance.getClass().getField("proxies").get(instance);
                Boolean isProxy = (Boolean) proxies.getClass().getField("isProxy").get(proxies);
                if (Boolean.TRUE.equals(isProxy)) {
                    return true;
                }
            } catch (Exception ignored) {}

            return checkLegacyProxyConfig();
        } catch (Exception e) {
            return false;
        }
    }
    
    private boolean checkLegacyProxyConfig() {
        return false;
    }
    
    private boolean hasMultipleServers() {
        if (Bukkit.getWorlds().size() > 1) {
            return true;
        }

        return Bukkit.getOnlinePlayers().size() > 50;
    }
    
    private boolean customConfigEnabled() {
        return false;
    }

    public enum ServerType {
        LEAF, PURPUR, PAPER, SPIGOT, BUKKIT, FOLIA, AIRPLANE, UNKNOWN
    }
    
    public ServerType detectServerType() {
        try {
            String[] leafClasses = {
                "io.leafmc.leaf.LeafConfig",
                "io.leafmc.leaf.LeafLogger",
                "io.leafmc.leaf.LeafVersion"
            };
            
            for (String leafClass : leafClasses) {
                try {
                    Class.forName(leafClass);
                    return ServerType.LEAF;
                } catch (ClassNotFoundException ignored) {
                }
            }
        } catch (Exception ignored) {}

        try {
            Class.forName("org.purpurmc.purpur.PurpurConfig");
            return ServerType.PURPUR;
        } catch (ClassNotFoundException ignored) {}

        try {
            Class.forName("com.destroystokyo.paper.PaperConfig");
            return ServerType.PAPER;
        } catch (ClassNotFoundException ignored) {}

        try {
            Class.forName("gg.airplane.AirplaneConfig");
            return ServerType.AIRPLANE;
        } catch (ClassNotFoundException ignored) {}

        try {
            Class.forName("org.spigotmc.SpigotConfig");
            return ServerType.SPIGOT;
        } catch (ClassNotFoundException ignored) {}

        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return ServerType.FOLIA;
        } catch (ClassNotFoundException ignored) {}
        
        return ServerType.BUKKIT;
    }
    
    public boolean isUsingMessagingChannel() {
        return useMessagingChannel;
    }
    
    public String getFullEnvironmentInfo() {
        StringBuilder info = new StringBuilder();
        
        ServerType serverType = detectServerType();
        info.append("       您当前的服务端核心底层架构为: ").append(serverType.name());
        return info.toString();
    }
}