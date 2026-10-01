package fr.universalserverloader.plm;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Commande /plm : inventorie les plugins actifs et les mods présents, et indique
 * pour chacun son mode de chargement réel ainsi que la raison s'il n'est pas chargé.
 * Ce plugin ne dépend pas du loader : il fonctionne même si le serveur a été
 * lancé à la main.
 */
public final class UniversalPlmPlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        getLogger().info("Commande /plm activée.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!"plm".equalsIgnoreCase(command.getName())) return false;
        Platform platform = detectPlatform();

        sender.sendMessage("=== Plateforme ===");
        sender.sendMessage("- Serveur: " + getServer().getName() + " " + getServer().getMinecraftVersion());
        sender.sendMessage("- Plugins Bukkit: " + (platform.bukkit ? "oui — chargés depuis le dossier plugins/" : "non"));
        sender.sendMessage("- Mod loader: " + (platform.modLoader.length() == 0
                ? "aucun — ce serveur ne chargera jamais un mod Forge/Fabric"
                : platform.modLoader + " — les mods sont chargés depuis le dossier mods/"));

        Plugin[] plugins = getServer().getPluginManager().getPlugins();
        Arrays.sort(plugins, new Comparator<Plugin>() {
            public int compare(Plugin left, Plugin right) {
                return left.getName().compareToIgnoreCase(right.getName());
            }
        });
        sender.sendMessage("=== Plugins actifs (" + plugins.length + ") ===");
        for (Plugin plugin : plugins) {
            sender.sendMessage("- " + plugin.getName() + " " + plugin.getPluginMeta().getVersion()
                    + " | mode de chargement: plugin " + platform.label
                    + ", dossier plugins/" + (plugin.isEnabled() ? "" : " (désactivé)"));
        }

        List<Mod> mods = collectMods();
        sender.sendMessage("=== Mods détectés (" + mods.size() + ") ===");
        if (mods.isEmpty()) {
            sender.sendMessage("- Aucun");
        } else {
            int loaded = 0;
            for (Mod mod : mods) {
                boolean loads = platform.loads(mod.loader);
                if (loads) loaded++;
                String line = "- " + mod.path + " | mode de chargement: " + displayLoader(mod.loader);
                if (mod.staged) line += " (installé automatiquement dans mods/)";
                if (!loads) line += " | NON CHARGÉ: " + platform.label + " n'a pas de mod loader " + displayLoader(mod.loader);
                else if (!mod.inRoot) line += " | NON CHARGÉ: " + platform.label + " ne lit que le dossier mods/";
                sender.sendMessage(line);
            }
            sender.sendMessage(loaded + " mod(s) chargé(s) sur " + mods.size() + ".");
            if (loaded < mods.size()) {
                sender.sendMessage("Pour tout charger: lancez un serveur Forge, NeoForge ou Fabric,");
                sender.sendMessage("ou un hybride (Mohist, Arclight, CatServer, Magma) — voir docs/ARCHITECTURE.md.");
            }
        }
        return true;
    }

    /** Plateforme telle que le serveur la déclare, complétée par la présence des classes de mod loader. */
    private Platform detectPlatform() {
        String hint = (getServer().getName() + " " + getServer().getVersion()).toLowerCase();
        boolean forge = present("net.minecraftforge.server.ServerLifecycleHooks")
                || present("net.neoforged.neoforge.server.Main")
                || hint.contains("mohist") || hint.contains("arclight")
                || hint.contains("catserver") || hint.contains("magma") || hint.contains("crucible");
        boolean fabric = present("net.fabricmc.loader.impl.FabricLoaderImpl")
                || present("net.fabricmc.launcher.handlers.ServerLauncher")
                || hint.contains("cardboard") || hint.contains("fabric");
        if (forge) return new Platform("Hybride Bukkit+Forge", true, "forge");
        if (fabric) return new Platform("Hybride Bukkit+Fabric", true, "fabric");
        if (hint.contains("paper")) return new Platform("Paper", true, "");
        if (hint.contains("spigot")) return new Platform("Spigot", true, "");
        return new Platform("Bukkit", true, "");
    }

    private static boolean present(String className) {
        try {
            Class.forName(className, false, UniversalPlmPlugin.class.getClassLoader());
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Mods de mods/ racine et des sous-dossiers, sans doublon lorsque le loader les a installés. */
    private List<Mod> collectMods() {
        List<Mod> result = new ArrayList<Mod>();
        File root = new File("mods");
        File[] rootFiles = root.listFiles();
        if (rootFiles != null) {
            Arrays.sort(rootFiles, byName());
            for (File file : rootFiles) {
                if (file.isFile() && file.getName().toLowerCase().endsWith(".jar")) {
                    result.add(new Mod("mods/" + file.getName(), loaderOf(file), true, true));
                }
            }
        }
        String[][] folders = {{"fabric", "mods/fabric"}, {"forge", "mods/forge"}, {"neoforge", "mods/neoforge"}};
        for (String[] folder : folders) {
            File[] files = new File("mods/" + folder[0]).listFiles();
            if (files == null) continue;
            Arrays.sort(files, byName());
            for (File file : files) {
                if (!file.isFile() || !file.getName().toLowerCase().endsWith(".jar")) continue;
                File installed = new File("mods/" + file.getName());
                boolean staged = installed.isFile() && installed.length() == file.length();
                if (staged) continue; // déjà présent en racine: la ligne racine porte l'information
                result.add(new Mod("mods/" + folder[0] + "/" + file.getName(), loaderOf(file), false, false));
            }
        }
        return result;
    }

    /** Loader réel du JAR, lu dans son contenu ("multi" si plusieurs descripteurs). */
    private static String loaderOf(File file) {
        try (ZipFile zip = new ZipFile(file)) {
            boolean fabric = false, forge = false, neoforge = false;
            java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if ("fabric.mod.json".equals(name)) fabric = true;
                else if ("META-INF/neoforge.mods.toml".equals(name)) neoforge = true;
                else if ("META-INF/mods.toml".equals(name)) forge = true;
            }
            int markers = (fabric ? 1 : 0) + (forge ? 1 : 0) + (neoforge ? 1 : 0);
            if (markers >= 2) return "multi";
            if (fabric) return "fabric";
            if (neoforge) return "neoforge";
            if (forge) return "forge";
        } catch (IOException ignored) {
            return "inconnu";
        }
        return "inconnu";
    }

    private static String displayLoader(String loader) {
        if ("multi".equals(loader)) return "multi-plateforme (Fabric+Forge+NeoForge)";
        return loader;
    }

    private static Comparator<File> byName() {
        return new Comparator<File>() {
            public int compare(File left, File right) {
                return left.getName().compareToIgnoreCase(right.getName());
            }
        };
    }

    private static final class Platform {
        final String label;
        final boolean bukkit;
        final String modLoader;

        Platform(String label, boolean bukkit, String modLoader) {
            this.label = label;
            this.bukkit = bukkit;
            this.modLoader = modLoader;
        }

        boolean loads(String loader) {
            if (modLoader.length() == 0 || loader.length() == 0) return false;
            if ("multi".equals(loader)) return true;
            if ("fabric".equals(modLoader)) return "fabric".equals(loader);
            if ("forge".equals(modLoader) || "neoforge".equals(modLoader)) {
                return "forge".equals(loader) || "neoforge".equals(loader);
            }
            return modLoader.equals(loader);
        }
    }

    private static final class Mod {
        final String path;
        final String loader;
        /** true si le fichier se trouve dans mods/ (dossier réellement lu par le serveur). */
        final boolean inRoot;
        final boolean staged;

        Mod(String path, String loader, boolean inRoot, boolean staged) {
            this.path = path;
            this.loader = loader;
            this.inRoot = inRoot;
            this.staged = staged;
        }
    }
}
