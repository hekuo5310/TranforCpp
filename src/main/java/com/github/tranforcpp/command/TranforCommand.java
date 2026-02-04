package com.github.tranforcpp.command;

import com.github.tranforcpp.TranforCPlusPlus;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import net.kyori.adventure.text.minimessage.MiniMessage;

public class TranforCommand implements CommandExecutor {
    
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final String USAGE_MESSAGE = "<red>用法: /tranforcpp <reload|version>";
    private static final String PERMISSION_DENIED = "<red>权限不足";
    private static final String RELOAD_START = "<yellow>正在重载...";
    private static final String RELOAD_COMPLETE = "<green>重载完成!";
    private static final String VERSION_PREFIX = "<white>[<aqua>TranforC++<white>] <green>您当前服务器的模块版本为: <green>";
    private static final String UNKNOWN_COMMAND = "<red>未知指令! 用法: /tranforcpp <reload|version>";

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(MM.deserialize(USAGE_MESSAGE));
            return true;
        }
        
        String subCommand = args[0].toLowerCase();

        return switch (subCommand) {
            case "reload" -> handleReload(sender);
            case "version" -> handleVersion(sender);
            default -> {
                sender.sendMessage(MM.deserialize(UNKNOWN_COMMAND));
                yield true;
            }
        };
    }
    
    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("tranforcpp.reload")) {
            sender.sendMessage(MM.deserialize(PERMISSION_DENIED));
            return true;
        }
        sender.sendMessage(MM.deserialize(RELOAD_START));
        TranforCPlusPlus.getInstance().reload();
        sender.sendMessage(MM.deserialize(RELOAD_COMPLETE));
        return true;
    }
    
    private boolean handleVersion(CommandSender sender) {
        if (!sender.hasPermission("tranforcpp.version")) {
            sender.sendMessage(MM.deserialize(PERMISSION_DENIED));
            return true;
        }
        String version = TranforCPlusPlus.getInstance().getPluginMeta().getVersion();
        sender.sendMessage(MM.deserialize(VERSION_PREFIX + version));
        return true;
    }
}