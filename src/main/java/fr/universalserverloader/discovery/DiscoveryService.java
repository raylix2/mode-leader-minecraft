package fr.universalserverloader.discovery;

import fr.universalserverloader.config.LoaderConfig;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class DiscoveryService {
    private List<ExtensionInfo> cache;

    public List<ExtensionInfo> scan(Path root, LoaderConfig config) throws IOException {
        if (cache != null) return cache;
        List<ExtensionInfo> found = new ArrayList<ExtensionInfo>();
        if (config.scanPlugins) {
            String pluginMessage = "Plugin détecté; son chargement reste assuré par Bukkit/Spigot/Paper.";
            inspectDirectory(root.resolve("plugins"), ExtensionInfo.Kind.PLUGIN, found, pluginMessage);
            inspectTree(root.resolve("plugins/bukkit"), ExtensionInfo.Kind.PLUGIN, found, pluginMessage);
            inspectTree(root.resolve("plugins/spigot"), ExtensionInfo.Kind.PLUGIN, found, pluginMessage);
            inspectTree(root.resolve("plugins/paper"), ExtensionInfo.Kind.PLUGIN, found, pluginMessage);
        }
        if (config.scanAddons) {
            inspectTree(root.resolve("addons"), ExtensionInfo.Kind.ADDON, found,
                    "Addon Universal candidat (descriptor requis dans le JAR).");
        }
        if (config.scanMods) {
            inspectTree(root.resolve("mods/forge"), ExtensionInfo.Kind.FORGE_MOD, found,
                    "Réservé à un futur adaptateur Forge; ne sera pas chargé.");
            inspectTree(root.resolve("mods/fabric"), ExtensionInfo.Kind.FABRIC_MOD, found,
                    "Réservé à un futur adaptateur Fabric; ne sera pas chargé.");
        }
        Collections.sort(found, new Comparator<ExtensionInfo>() {
            public int compare(ExtensionInfo a, ExtensionInfo b) { return a.path.toString().compareToIgnoreCase(b.path.toString()); }
        });
        cache = Collections.unmodifiableList(found);
        return cache;
    }

    private void inspectDirectory(Path directory, ExtensionInfo.Kind kind, List<ExtensionInfo> result, String message) throws IOException {
        if (!Files.isDirectory(directory)) return;
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(directory)) {
            for (Path p : entries) {
                if (p.getFileName().toString().startsWith(".") || Files.isDirectory(p)) continue;
                if (p.getFileName().toString().toLowerCase().endsWith(".jar")) result.add(new ExtensionInfo(kind, p, message));
                else result.add(new ExtensionInfo(ExtensionInfo.Kind.REJECTED, p, "Refusé: une extension doit être un fichier .jar."));
            }
        }
    }

    private void inspectTree(Path directory, ExtensionInfo.Kind kind, List<ExtensionInfo> result, String message) throws IOException {
        if (!Files.isDirectory(directory)) return;
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(directory)) {
            for (Path p : entries) {
                if (p.getFileName().toString().startsWith(".")) continue; // fichiers de maintien Git, pas des extensions
                if (Files.isDirectory(p)) inspectTree(p, kind, result, message);
                else if (p.getFileName().toString().toLowerCase().endsWith(".jar")) result.add(new ExtensionInfo(kind, p, message));
                else result.add(new ExtensionInfo(ExtensionInfo.Kind.REJECTED, p, "Refusé: une extension doit être un fichier .jar."));
            }
        }
    }
}
