package com.github.tranforcpp;

import com.github.tranforcpp.command.TranforCommand;
import com.github.tranforcpp.command.TranforTabCompleter;
import com.github.tranforcpp.listener.PluginListListener;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;

public class TranforCPlusPlus extends JavaPlugin {

    private static TranforCPlusPlus instance;
    private ProcessManager processManager;
    private StartupManager startupManager;
    private com.github.tranforcpp.channel.PluginMessagingManager messagingManager;
    private MemoryOptimizer memoryOptimizer;

    @Override
    public void onEnable() {
        instance = this;
        
        registerTranforCommand();
        
        startupManager = new StartupManager(this);
        startupManager.startAsync();
        
        memoryOptimizer = new MemoryOptimizer(this);
        memoryOptimizer.initialize();
        
        com.github.tranforcpp.command.SmartEventDispatcher eventDispatcher = 
            new com.github.tranforcpp.command.SmartEventDispatcher();
        eventDispatcher.initialize();
        
        if (eventDispatcher.isUsingMessagingChannel()) {
            messagingManager = new com.github.tranforcpp.channel.PluginMessagingManager(this);
            messagingManager.initialize();
        }

        getServer().getPluginManager().registerEvents(new PluginListListener(), this);
    }
    
    private void registerTranforCommand() {
        try {
            Field commandMapField = getServer().getClass().getDeclaredField("commandMap");
            commandMapField.setAccessible(true);
            CommandMap commandMap = (CommandMap) commandMapField.get(getServer());
            
            Command tranforCommand = createTranforCommand();
            commandMap.register("tranforcpp", tranforCommand);
        } catch (Exception e) {
            getLogger().warning("命令注册失败了!!!bro!!!");
        }
    }
    
    private Command createTranforCommand() {
        TranforCommand commandExecutor = new TranforCommand();
        TranforTabCompleter tabCompleter = new TranforTabCompleter();
        
        Command tranforCommand = new Command("tranforcpp") {
            @Override
            public boolean execute(org.bukkit.command.CommandSender sender, String label, String[] args) {
                return commandExecutor.onCommand(sender, this, label, args);
            }
            
            @Override
            public java.util.List<String> tabComplete(org.bukkit.command.CommandSender sender, String alias, String[] args) {
                java.util.List<String> result = tabCompleter.onTabComplete(sender, this, alias, args);
                return result != null ? result : new java.util.ArrayList<>();
            }
        };
        
        tranforCommand.setDescription("Manage TranforC++ plugin");
        tranforCommand.setUsage("/<command> [reload|version]");
        tranforCommand.setPermission("tranforcpp.use");
        tranforCommand.setAliases(java.util.Collections.singletonList("cpp"));
        
        return tranforCommand;
    }

    @Override
    public void onDisable() {
        if (processManager != null) {
            processManager.stop();
        }
        if (startupManager != null) {
            startupManager.shutdown();
        }
        if (messagingManager != null) {
            messagingManager.cleanup();
        }
        if (memoryOptimizer != null) {
            memoryOptimizer.shutdown();
        }
        getLogger().info("TranforC++模块插件 已关闭!");
    }

    public static TranforCPlusPlus getInstance() {
        return instance;
    }

    public ProcessManager getProcessManager() {
        return processManager;
    }

    public void setProcessManager(ProcessManager processManager) {
        this.processManager = processManager;
    }

    public void reload() {
        if (processManager != null) {
            processManager.restart();
        }
        getLogger().info("TranforC++ 已重载!");
    }
    
    public com.github.tranforcpp.channel.PluginMessagingManager getMessagingManager() {
        return messagingManager;
    }

}