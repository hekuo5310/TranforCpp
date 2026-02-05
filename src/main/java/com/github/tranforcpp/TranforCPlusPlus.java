package com.github.tranforcpp;

import com.github.tranforcpp.command.TranforCommand;
import com.github.tranforcpp.command.TranforTabCompleter;
import com.github.tranforcpp.listener.PluginListListener;
import com.github.tranforcpp.optimizer.MemoryOptimizer;
import com.github.tranforcpp.optimizer.SmartThreadOptimizer;
import com.github.tranforcpp.utils.AnsiColorUtils;

import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;

/**
 * TranforC++ 主插件类
 * <p>
 * 这是一个允许在Minecraft服务器上运行C++代码的插件。
 * 它提供了完整的C++插件生态系统，包括编译、执行和事件处理功能。
 * <p>
 * 主要特性：
 * - 自动编译C++源代码
 * - 跨语言事件通信
 * - 智能线程优化
 * - 内存管理优化
 * - 多服务器消息传递支持
 * 
 * @author hekuo, Xiao-QDev
 * @version 1.1.2
 */
public class TranforCPlusPlus extends JavaPlugin {

    private static TranforCPlusPlus instance;
    private ProcessManager processManager;
    private StartupManager startupManager;
    // 移除了代理端支持 - 不再需要 messagingManager 字段
    private MemoryOptimizer memoryOptimizer;
    private SmartThreadOptimizer threadOptimizer;
    private PluginListListener pluginListListener;

    @Override
    public void onEnable() {
        instance = this;
                getLogger().info(AnsiColorUtils.colorize("正在初始化TranforC++模块...", AnsiColorUtils.COLOR_51));
        // 注册主命令
        registerTranforCommand();
        
        // 初始化启动管理器
        startupManager = new StartupManager(this);
        startupManager.startAsync();
        
        // 初始化内存优化器
        memoryOptimizer = new MemoryOptimizer();
        memoryOptimizer.initialize();
        
        // 初始化智能线程优化器
        threadOptimizer = new SmartThreadOptimizer(this);
        threadOptimizer.initialize();
        
        // 初始化事件分发器
        com.github.tranforcpp.command.SmartEventDispatcher eventDispatcher = 
            new com.github.tranforcpp.command.SmartEventDispatcher();
        eventDispatcher.initialize();

        // 注册插件监听器
        this.pluginListListener = new PluginListListener();
        getServer().getPluginManager().registerEvents(this.pluginListListener, this);
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
        // 移除了代理端支持 - 不再清理消息管理器
        if (memoryOptimizer != null) {
            memoryOptimizer.shutdown();
        }
        if (threadOptimizer != null) {
            threadOptimizer.shutdown();
        }
        if (pluginListListener != null) {
            pluginListListener.shutdown();
        }
        getLogger().info(AnsiColorUtils.colorize("TranforC++模块插件 已关闭!", AnsiColorUtils.RED));
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
    }
    public SmartThreadOptimizer getThreadOptimizer() {
        return threadOptimizer;
    }

}