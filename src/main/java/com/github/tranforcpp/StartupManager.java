package com.github.tranforcpp;

import com.github.tranforcpp.utils.AnsiColorUtils;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class StartupManager {
    
    private final TranforCPlusPlus plugin;
    private final ExecutorService startupExecutor;
    
    public StartupManager(TranforCPlusPlus plugin) {
        this.plugin = plugin;
        int optimalThreads = calculateOptimalThreadCount();
        this.startupExecutor = Executors.newFixedThreadPool(optimalThreads);
        plugin.getLogger().fine("启动管理器使用 " + optimalThreads + " 个工作线程");
    }
    
    private int calculateOptimalThreadCount() {
        int cores = Runtime.getRuntime().availableProcessors();
        return Math.max(2, Math.min(cores * 2, 8));
    }
    
    public void startAsync() {
        setupCommands();
        
        CompletableFuture<Void> processSetup = CompletableFuture.runAsync(this::setupProcessManager, startupExecutor);
        CompletableFuture<Void> eventSetup = CompletableFuture.runAsync(this::setupEventHandlers, startupExecutor);
        CompletableFuture.allOf(processSetup, eventSetup)
            .thenRun(() -> {
                com.github.tranforcpp.compiler.CppCompiler compiler = new com.github.tranforcpp.compiler.CppCompiler();
                int pluginCount = compiler.countPlugins();

                com.github.tranforcpp.command.SmartEventDispatcher eventDispatcher = 
                    new com.github.tranforcpp.command.SmartEventDispatcher();
                eventDispatcher.initialize();
                String environmentInfo = eventDispatcher.getFullEnvironmentInfo();

                String[] logoLines = {
                    "                                             ",
                    "         ████████╗  ███████╗   ██████╗        ",
                    "         ╚══██╔══╝  ██╔════╝  ██╔════╝        ",
                    "            ██║     █████╗    ██║             ",
                    "            ██║     ██╔══╝    ██║             ",
                    "            ██║     ██║       ╚██████╗        ",
                    "            ╚═╝     ╚═╝        ╚═════╝        ",
                    "                                             ",
                };

                String[] coloredLogo = AnsiColorUtils.createGradientText(logoLines, AnsiColorUtils.LOGO_GRADIENT);
                for (String line : coloredLogo) {
                    plugin.getLogger().info(line);
                }

                plugin.getLogger().info(AnsiColorUtils.colorize("              TranforC++ 模块已启动", AnsiColorUtils.COLOR_51));
                plugin.getLogger().info(AnsiColorUtils.colorize("               当前模块版本: " + plugin.getPluginMeta().getVersion(), AnsiColorUtils.COLOR_51));
                plugin.getLogger().info(AnsiColorUtils.colorize("           C+Plugins目录中发现 " + pluginCount + " 个插件", AnsiColorUtils.COLOR_51));
                plugin.getLogger().info(AnsiColorUtils.colorize(environmentInfo, AnsiColorUtils.COLOR_51));

            })
            .exceptionally(throwable -> {
                plugin.getLogger().log(Level.SEVERE, "启动过程中发生错误: " + throwable.getMessage(), throwable);
                return null;
            });
    }
    
    private void setupCommands() {
    }
    
    private void setupProcessManager() {
        try {
            plugin.setProcessManager(new ProcessManager(plugin));
            plugin.getProcessManager().start();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "多进程设置失败: " + e.getMessage(), e);
        }
    }
    
    private void setupEventHandlers() {
    }
    
    public void shutdown() {
        if (startupExecutor != null && !startupExecutor.isShutdown()) {
            startupExecutor.shutdown();
            try {
                if (!startupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    startupExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                startupExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}