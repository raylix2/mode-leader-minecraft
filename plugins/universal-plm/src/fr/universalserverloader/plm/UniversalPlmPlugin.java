package fr.universalserverloader.plm;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class UniversalPlmPlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        getLogger().info("Commande /plm activée.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!"plm".equalsIgnoreCase(command.getName())) return false;
        Plugin[] plugins = getServer().getPluginManager().getPlugins();
        Arrays.sort(plugins, new Comparator<Plugin>() {
            public int compare(Plugin left, Plugin right) {
                return left.getName().compareToIgnoreCase(right.getName());
            }
        });
        sender.sendMessage("=== Plugins actifs (" + plugins.length + ") ===");
        for (Plugin plugin : plugins) {
            sender.sendMessage("- " + plugin.getName() + " " + plugin.getPluginMeta().getVersion());
        }

        List<String> mods = new ArrayList<String>();
        collectJars(new File("mods/forge"), "Forge", mods);
        collectJars(new File("mods/fabric"), "Fabric", mods);
        sender.sendMessage("=== Mods détectés mais non chargés (" + mods.size() + ") ===");
        if (mods.isEmpty()) sender.sendMessage("- Aucun");
        else for (String mod : mods) sender.sendMessage("- " + mod);
        sender.sendMessage("Paper ne charge pas directement les mods Forge/Fabric.");
        return true;
    }

    private static void collectJars(File directory, String platform, List<String> result) {
        File[] files = directory.listFiles();
        if (files == null) return;
        Arrays.sort(files, new Comparator<File>() {
            public int compare(File left, File right) { return left.getName().compareToIgnoreCase(right.getName()); }
        });
        for (File file : files) {
            if (file.isFile() && file.getName().toLowerCase().endsWith(".jar"))
                result.add(platform + ": " + file.getName());
        }
    }
}
