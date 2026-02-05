package com.github.tranforcpp.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;

public class TranforTabCompleter implements TabCompleter {
    
    private static final String[] ALL_COMMANDS = {"reload", "version", "ver"};
    private static final String[] RELOAD_ONLY = {"reload"};
    private static final String[] VERSION_ONLY = {"version", "ver"};
    private static final String[] EMPTY_ARRAY = new String[0];

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return java.util.Collections.emptyList();
        }
        
        String input = args[0].toLowerCase();
        String[] candidates = getCandidates(sender);
        
        List<String> result = new ArrayList<>(candidates.length);
        for (String candidate : candidates) {
            if (candidate.startsWith(input)) {
                result.add(candidate);
            }
        }
        
        return result;
    }
    
    private String[] getCandidates(CommandSender sender) {
        boolean canReload = sender.hasPermission("tranforcpp.reload");
        boolean canVersion = sender.hasPermission("tranforcpp.version");
        
        if (canReload && canVersion) {
            return ALL_COMMANDS;
        } else if (canReload) {
            return RELOAD_ONLY;
        } else if (canVersion) {
            return VERSION_ONLY;
        } else {
            return EMPTY_ARRAY;
        }
    }
}