package com.github.tranforcpp.listener;

import com.github.tranforcpp.compiler.CppCompiler;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;

import java.io.File;
import java.util.List;

public class PluginListListener implements Listener {
    
    private final MiniMessage mm = MiniMessage.miniMessage();
    
    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage().toLowerCase().trim();
        if (message.equals("/pl") || message.equals("/plugins")) {
            Bukkit.getScheduler().runTaskLater(
                com.github.tranforcpp.TranforCPlusPlus.getInstance(), 
                () -> showTranforCPlugins(event.getPlayer()), 
                1L
            );
        }
    }
    
    @EventHandler
    public void onServerCommand(ServerCommandEvent event) {
        String command = event.getCommand().toLowerCase().trim();
        if (command.equals("pl") || command.equals("plugins")) {
            Bukkit.getScheduler().runTaskLater(
                com.github.tranforcpp.TranforCPlusPlus.getInstance(),
                () -> showTranforCPlugins(event.getSender()),
                1L
            );
        }
    }
    
    private void showTranforCPlugins(org.bukkit.command.CommandSender sender) {
        CppCompiler compiler = new CppCompiler();
        File cppDir = compiler.getCppDirectory();
        
        if (!cppDir.exists()) {
            return;
        }
        
        List<File> cppFiles = compiler.getPluginFiles();
        
        if (!cppFiles.isEmpty()) {
            sender.sendMessage(mm.deserialize("<aqua>□ <white>C++ Plugins (<white>" + cppFiles.size() + "<white>):"));
            
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
}