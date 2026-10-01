package fr.universalserverloader.platform;

import fr.universalserverloader.discovery.ExtensionInfo;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Plan de chargement : pour chaque extension détectée, indique si la plateforme
 * la chargera réellement et par quel mécanisme, ou pourquoi elle est bloquée.
 * Le plan est écrit dans runtime/load-plan.txt pour l'interface graphique.
 */
public final class LoadPlan {
    public static final class Entry {
        public final ExtensionInfo item;
        public final boolean willLoad;
        /** Mode de chargement réel, null si l'extension ne sera pas chargée. */
        public final String mode;
        /** Raison du blocage, null si l'extension sera chargée. */
        public final String blocker;

        Entry(ExtensionInfo item, boolean willLoad, String mode, String blocker) {
            this.item = item;
            this.willLoad = willLoad;
            this.mode = mode;
            this.blocker = blocker;
        }
    }

    private final Path root;
    private final PlatformInfo platform;
    private final String warning;
    private final boolean autoInstallPlugins;
    private final boolean stageMods;
    private final List<Entry> entries;

    public LoadPlan(Path root, PlatformInfo platform, String warning, boolean autoInstallPlugins,
                    boolean stageMods, List<ExtensionInfo> items) {
        this.root = root;
        this.platform = platform;
        this.warning = warning;
        this.autoInstallPlugins = autoInstallPlugins;
        this.stageMods = stageMods;
        List<Entry> built = new ArrayList<Entry>();
        for (ExtensionInfo item : items) built.add(evaluate(item));
        this.entries = Collections.unmodifiableList(built);
    }

    public PlatformInfo platform() { return platform; }
    public String warning() { return warning; }
    public List<Entry> entries() { return entries; }

    public int loaded() {
        int count = 0;
        for (Entry entry : entries) if (entry.willLoad) count++;
        return count;
    }

    public int rejected() {
        int count = 0;
        for (Entry entry : entries) if (entry.item.kind == ExtensionInfo.Kind.REJECTED) count++;
        return count;
    }

    public int blocked() { return entries.size() - loaded(); }

    public int mods() {
        int count = 0;
        for (Entry entry : entries) {
            ExtensionInfo.Kind kind = entry.item.kind;
            if (kind == ExtensionInfo.Kind.FORGE_MOD || kind == ExtensionInfo.Kind.FABRIC_MOD) count++;
        }
        return count;
    }

    public int plugins() {
        int count = 0;
        for (Entry entry : entries) if (entry.item.kind == ExtensionInfo.Kind.PLUGIN) count++;
        return count;
    }

    public String summary() {
        return loaded() + " chargé(s), " + (blocked() - rejected()) + " non chargé(s), " + rejected() + " refusé(s)";
    }

    public String relative(Path path) {
        String value = root.relativize(path).toString().replace('\\', '/');
        return value.length() == 0 ? path.getFileName().toString() : value;
    }

    /** Écrit le plan lisible par l'interface graphique et par les outils externes. */
    public void write(Path file, String version) throws IOException {
        StringBuilder out = new StringBuilder();
        out.append("# Plan de chargement UniversalServerLoader ").append(version).append('\n');
        out.append("# plateforme=").append(platform.id)
           .append(" label=").append(platform.label)
           .append(" plugins=").append(platform.bukkitPlugins ? "oui" : "non")
           .append(" modloader=").append(platform.hasModLoader() ? platform.modLoader : "-")
           .append('\n');
        out.append("# resume=").append(summary()).append('\n');
        if (warning != null) out.append("# avertissement=").append(warning.replace('\n', ' ')).append('\n');
        for (Entry entry : entries) {
            if (entry.item.kind == ExtensionInfo.Kind.REJECTED) continue;
            out.append(entry.willLoad ? "OUI" : "NON").append('\t')
               .append(relative(entry.item.path)).append('\t')
               .append(entry.willLoad ? entry.mode : entry.blocker).append('\n');
        }
        Files.createDirectories(file.getParent());
        Files.write(file, out.toString().getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
    }

    private Entry evaluate(ExtensionInfo item) {
        String relative = relative(item.path);
        if (item.kind == ExtensionInfo.Kind.REJECTED) {
            return block(item, "Fichier refusé : une extension doit être un fichier .jar.");
        }
        String location = location(relative);
        if ("addons".equals(location)) {
            if (item.type != ExtensionInfo.Type.UNIVERSAL_ADDON) {
                return block(item, "Addon invalide : universal-addon.json absent, le loader refusera ce fichier.");
            }
            return new Entry(item, true,
                    "Addon Universal — chargé par le loader via l'API UniversalAddon (dossier addons/)", null);
        }
        if ("plugins".equals(location)) {
            if (isMod(item.type)) {
                return block(item, "Mod " + label(loaderOf(item)) + " déposé dans " + parent(relative)
                        + " : " + platform.label + " ne charge pas les mods, déplacez-le dans mods/ et lancez un serveur "
                        + label(loaderOf(item)) + ".");
            }
            if (item.type == ExtensionInfo.Type.UNIVERSAL_ADDON) {
                return block(item, "Addon Universal déposé dans plugins/ : déplacez-le dans addons/.");
            }
            if (item.type != ExtensionInfo.Type.BUKKIT_PLUGIN) {
                return block(item, "Descripteur plugin.yml introuvable : " + platform.label + " refusera ce fichier.");
            }
            if (!platform.bukkitPlugins) {
                return block(item, "Plugin Bukkit — " + platform.label
                        + " ne gère pas les plugins Bukkit : utilisez un serveur Paper, Spigot, Bukkit ou un hybride.");
            }
            boolean inSubfolder = !relative.startsWith("plugins/");
            if (inSubfolder && !autoInstallPlugins) {
                return block(item, "Plugin Bukkit dans un sous-dossier : Paper ne lit que plugins/, "
                        + "activez installStagedPlugins ou déplacez le fichier.");
            }
            String mode = "Plugin " + platform.label + " — chargé depuis plugins/";
            if (inSubfolder) mode += " (installé automatiquement depuis " + relative + ")";
            return new Entry(item, true, mode, null);
        }
        if ("mods".equals(location)) {
            if (item.type == ExtensionInfo.Type.BUKKIT_PLUGIN) {
                return block(item, "Plugin Bukkit déposé dans " + parent(relative)
                        + " : déplacez-le dans plugins/ pour qu'il soit chargé.");
            }
            if (item.type == ExtensionInfo.Type.UNIVERSAL_ADDON) {
                return block(item, "Addon Universal déposé dans mods/ : déplacez-le dans addons/.");
            }
            String loader = loaderOf(item);
            if (loader.length() == 0) {
                return block(item, "Descripteur de mod introuvable (fabric.mod.json, mods.toml ou neoforge.mods.toml absent) : "
                        + "le serveur refusera ce fichier.");
            }
            String display = label(loader);
            if ("multi".equals(loader)) {
                display = "multi-plateforme (Fabric+Forge+NeoForge)";
                if (!platform.hasModLoader()) {
                    return block(item, "Mod multi-plateforme — contient fabric.mod.json et mods.toml : il faut un serveur "
                            + "Fabric, Forge, NeoForge ou un hybride (Mohist, Arclight…) ; " + platform.label
                            + " ne chargera jamais ce fichier.");
                }
                boolean inSourceFolder = folderOf(relative).length() > 0;
                if (inSourceFolder && !stageMods) {
                    return block(item, "Mod multi-plateforme dans un sous-dossier : " + platform.label
                            + " ne lit que mods/, activez stageMods ou déplacez le fichier.");
                }
                String mode = "Mod " + display + " — versions Fabric/Forge/NeoForge embarquées, " + platform.label
                        + " chargera la sienne depuis mods/";
                if (inSourceFolder) mode += " (installé automatiquement depuis " + relative + ")";
                return new Entry(item, true, mode, null);
            }
            if (platform.loads(loader)) {
                boolean inSourceFolder = folderOf(relative).length() > 0;
                if (inSourceFolder && !stageMods) {
                    return block(item, "Mod " + display + " dans un sous-dossier : " + platform.label
                            + " ne lit que mods/, activez stageMods ou déplacez le fichier.");
                }
                String mode = "Mod " + display + " — chargé par " + platform.label + " depuis mods/";
                if (inSourceFolder) mode += " (installé automatiquement depuis " + relative + ")";
                return new Entry(item, true, mode, null);
            }
            return block(item, "Mod " + display + " — nécessite un serveur " + display
                    + " ou un hybride (Mohist, Arclight, CatServer, Magma) : " + platform.label
                    + " ne chargera jamais ce mod.");
        }
        return block(item, "Dossier non reconnu : placez l'extension dans plugins/, mods/ ou addons/.");
    }

    /** true si le contenu du JAR annonce un mod plutôt qu'un plugin. */
    private static boolean isMod(ExtensionInfo.Type type) {
        return type == ExtensionInfo.Type.FABRIC_MOD
                || type == ExtensionInfo.Type.FORGE_MOD
                || type == ExtensionInfo.Type.NEOFORGE_MOD
                || type == ExtensionInfo.Type.MULTI_MOD;
    }

    /** Loader annoncé par le contenu du jar, sinon celui déduit du sous-dossier de mods/. */
    private String loaderOf(ExtensionInfo item) {
        String fromContent = JarInspector.loaderOf(item.type);
        if (fromContent.length() > 0) return fromContent;
        String folder = folderOf(relative(item.path));
        if ("fabric".equals(folder)) return "fabric";
        if ("neoforge".equals(folder)) return "neoforge";
        if ("forge".equals(folder)) return "forge";
        if (item.kind == ExtensionInfo.Kind.FABRIC_MOD) return "fabric";
        if (item.kind == ExtensionInfo.Kind.FORGE_MOD) return "forge";
        return "";
    }

    /** Sous-dossier éventuel : "mods/fabric/x.jar" donne "fabric", "mods/x.jar" donne "". */
    private static String folderOf(String relative) {
        String[] parts = relative.split("/");
        return parts.length >= 3 ? parts[1] : "";
    }

    private Entry block(ExtensionInfo item, String blocker) {
        return new Entry(item, false, null, blocker);
    }

    private static String location(String relative) {
        int slash = relative.indexOf('/');
        return slash > 0 ? relative.substring(0, slash) : "";
    }

    private static String parent(String relative) {
        int slash = relative.lastIndexOf('/');
        return slash > 0 ? relative.substring(0, slash) : relative;
    }

    private static String label(String loader) {
        if ("fabric".equals(loader)) return "Fabric";
        if ("forge".equals(loader)) return "Forge";
        if ("neoforge".equals(loader)) return "NeoForge";
        if ("multi".equals(loader)) return "multi-plateforme";
        return loader.length() == 0 ? "inconnu" : loader;
    }
}
