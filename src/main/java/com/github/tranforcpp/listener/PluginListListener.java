package com.github.tranforcpp.listener;

import com.github.tranforcpp.compiler.CppCompiler;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;

import java.io.File;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 插件列表监听器
 * <p>
 * 监听服务器命令事件，处理与插件列表相关的命令。
 * 主要用于显示已安装的C++插件信息。
 * <p>
 * 监听的命令：
 * - /plugins: 显示所有插件列表，包括C++插件
 */
public class PluginListListener implements Listener {
    
    private final MiniMessage mm = MiniMessage.miniMessage();
    private final ScheduledExecutorService delayedExecutor;
    
    public PluginListListener() {
        this.delayedExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "PluginList-DelayedExecutor");
            t.setDaemon(true);
            return t;
        });
    }
    
    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage().toLowerCase().trim();
        if (message.equals("/pl") || message.equals("/plugins")) {
            delayedExecutor.schedule(() -> {
                try {
                    showTranforCPlugins(event.getPlayer());
                } catch (Exception ignored) {
                }
            }, 50, TimeUnit.MILLISECONDS);
        }
    }
    
    @EventHandler
    public void onServerCommand(ServerCommandEvent event) {
        String command = event.getCommand().toLowerCase().trim();
        if (command.equals("pl") || command.equals("plugins")) {
            delayedExecutor.schedule(() -> {
                try {
                    showTranforCPlugins(event.getSender());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, 50, TimeUnit.MILLISECONDS);
        }
    }
    
    private void showTranforCPlugins(org.bukkit.command.CommandSender sender) {
        CppCompiler compiler = new CppCompiler();
        File cppDir = compiler.getCppDirectory();
        
        if (!cppDir.exists()) {
            return;
        }
        
        List<File> cppFiles = compiler.getPluginFiles();
        
        if (cppFiles.isEmpty()) {
            sender.sendMessage(mm.deserialize("<white>[<aqua>TranforC++<white>]<red>当前模块并未安装插件,因此本模块并不会开始正常工作!"));
        } else {
            sender.sendMessage(mm.deserialize("<aqua>□ <white>C+ plugins (<white>" + cppFiles.size() + "<white>):"));
            sender.sendMessage(mm.deserialize("<aqua>TranforC++ Plugins (<aqua>" + cppFiles.size() + "<aqua>):"));
            
            StringBuilder pluginNames = new StringBuilder();
            for (int i = 0; i < cppFiles.size(); i++) {
                File file = cppFiles.get(i);
                String fileName = file.getName().replace(".cpp", "");
                pluginNames.append("<dark_gray>- <green>").append(fileName);
                if (i < cppFiles.size() - 1) {
                    pluginNames.append("<gray>, ");
                }
            }
            sender.sendMessage(mm.deserialize(pluginNames.toString()));
        }
    }
    public void shutdown() {
        if (delayedExecutor != null && !delayedExecutor.isShutdown()) {
            delayedExecutor.shutdown();
            try {
                if (!delayedExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    delayedExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                delayedExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}