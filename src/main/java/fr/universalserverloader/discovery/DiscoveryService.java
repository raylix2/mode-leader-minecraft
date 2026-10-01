package fr.universalserverloader.discovery;

import fr.universalserverloader.config.LoaderConfig;
import fr.universalserverloader.platform.JarInspector;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Inventaire des extensions. Le classement final tient compte du contenu du
 * JAR (JarInspector) autant que du dossier d'accueil, et un fichier présent à
 * deux endroits n'est déclaré qu'une seule fois : le dossier plugins/ pour les
 * plugins, les sous-dossiers de mods/ pour les mods.
 */
public final class DiscoveryService {
    private final JarInspector inspector = new JarInspector();
    private List<ExtensionInfo> cache;

    public List<ExtensionInfo> scan(Path root, LoaderConfig config) throws IOException {
        if (cache != null) return cache;
        List<ExtensionInfo> found = new ArrayList<ExtensionInfo>();

        if (config.scanPlugins) {
            String pluginMessage = "Plugin Bukkit; son chargement reste assuré par Bukkit/Spigot/Paper.";
            // plugins/ est la cible réelle : indexé en premier pour écarter les doublons des sous-dossiers.
            Set<String> installed = index(root.resolve("plugins"));
            inspectDirectory(root.resolve("plugins"), ExtensionInfo.Kind.PLUGIN, found, pluginMessage, null);
            inspectTree(root.resolve("plugins/bukkit"), ExtensionInfo.Kind.PLUGIN, found, pluginMessage, installed);
            inspectTree(root.resolve("plugins/spigot"), ExtensionInfo.Kind.PLUGIN, found, pluginMessage, installed);
            inspectTree(root.resolve("plugins/paper"), ExtensionInfo.Kind.PLUGIN, found, pluginMessage, installed);
        }

        if (config.scanAddons) {
            inspectTree(root.resolve("addons"), ExtensionInfo.Kind.ADDON, found,
                    "Addon Universal (descripteur universal-addon.json requis).", null);
        }

        if (config.scanMods) {
            Set<String> sources = new HashSet<String>();
            sources.addAll(index(root.resolve("mods/fabric")));
            sources.addAll(index(root.resolve("mods/forge")));
            sources.addAll(index(root.resolve("mods/neoforge")));
            inspectTree(root.resolve("mods/fabric"), ExtensionInfo.Kind.FABRIC_MOD, found, "Mod Fabric (fabric.mod.json).", null);
            inspectTree(root.resolve("mods/forge"), ExtensionInfo.Kind.FORGE_MOD, found, "Mod Forge (mods.toml).", null);
            inspectTree(root.resolve("mods/neoforge"), ExtensionInfo.Kind.FORGE_MOD, found, "Mod NeoForge (neoforge.mods.toml).", null);
            // mods/ racine : où Fabric, Forge et NeoForge cherchent réellement les mods.
            // Les copies installées par le loader sont ignorées ici pour éviter le doublon.
            inspectDirectory(root.resolve("mods"), ExtensionInfo.Kind.UNKNOWN, found,
                    "Fichier dans mods/ (mod ou plugin, selon son contenu).", sources);
        }

        Collections.sort(found, new Comparator<ExtensionInfo>() {
            public int compare(ExtensionInfo a, ExtensionInfo b) { return a.path.toString().compareToIgnoreCase(b.path.toString()); }
        });
        cache = Collections.unmodifiableList(found);
        return cache;
    }

    /** Parcours récursif d'un dossier, fichiers cachés exclus. */
    private void inspectTree(Path directory, ExtensionInfo.Kind folderKind, List<ExtensionInfo> result,
                             String message, Set<String> skip) throws IOException {
        if (!Files.isDirectory(directory)) return;
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(directory)) {
            for (Path p : entries) {
                if (p.getFileName().toString().startsWith(".")) continue; // fichiers de maintien Git
                if (Files.isDirectory(p)) inspectTree(p, folderKind, result, message, skip);
                else inspect(p, folderKind, result, message, skip);
            }
        }
    }

    /** Analyse les JAR d'un dossier sans descendre dans les sous-dossiers. */
    private void inspectDirectory(Path directory, ExtensionInfo.Kind folderKind, List<ExtensionInfo> result,
                                  String message, Set<String> skip) throws IOException {
        if (!Files.isDirectory(directory)) return;
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(directory)) {
            for (Path p : entries) inspect(p, folderKind, result, message, skip);
        }
    }

    /** Analyse un fichier : dossiers et fichiers cachés ignorés, non-JAR refusés. */
    private void inspect(Path file, ExtensionInfo.Kind folderKind, List<ExtensionInfo> result,
                         String message, Set<String> skip) {
        String name = file.getFileName().toString();
        if (name.startsWith(".") || Files.isDirectory(file)) return;
        if (skip != null && skip.contains(key(file))) return;
        if (!name.toLowerCase().endsWith(".jar")) {
            result.add(new ExtensionInfo(ExtensionInfo.Kind.REJECTED, file, ExtensionInfo.Type.UNKNOWN,
                    "Refusé: une extension doit être un fichier .jar."));
            return;
        }
        ExtensionInfo.Type type = inspector.inspect(file);
        result.add(new ExtensionInfo(kindOf(folderKind, type), file, type, message));
    }

    /** Le contenu du JAR prime; sinon le dossier d'accueil a le bénéfice du doute. */
    private static ExtensionInfo.Kind kindOf(ExtensionInfo.Kind folderKind, ExtensionInfo.Type type) {
        switch (type) {
            case BUKKIT_PLUGIN: return ExtensionInfo.Kind.PLUGIN;
            case FABRIC_MOD: return ExtensionInfo.Kind.FABRIC_MOD;
            case FORGE_MOD:
            case NEOFORGE_MOD:
            case MULTI_MOD: return ExtensionInfo.Kind.FORGE_MOD;
            case UNIVERSAL_ADDON: return ExtensionInfo.Kind.ADDON;
            default: return folderKind;
        }
    }

    /** Index des fichiers d'un dossier (nom + taille), pour repérer les doublons. */
    private static Set<String> index(Path directory) {
        Set<String> keys = new HashSet<String>();
        if (!Files.isDirectory(directory)) return keys;
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(directory)) {
            for (Path p : entries) {
                if (Files.isDirectory(p) || p.getFileName().toString().startsWith(".")) continue;
                keys.add(key(p));
            }
        } catch (IOException ignored) {
            return keys;
        }
        return keys;
    }

    private static String key(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        try { return name + ":" + Files.size(file); }
        catch (IOException e) { return name + ":-1"; }
    }
}
