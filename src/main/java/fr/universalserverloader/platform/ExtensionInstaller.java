package fr.universalserverloader.platform;

import fr.universalserverloader.discovery.ExtensionInfo;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Installe réellement les extensions là où la plateforme va les chercher :
 * - les plugins déposés dans plugins/bukkit, plugins/spigot et plugins/paper
 *   sont copiés dans plugins/ (seul dossier lu par Paper/Bukkit/Spigot) ;
 * - les mods du loader correspondant sont copiés dans mods/ (seul dossier lu
 *   par Fabric, Forge et NeoForge), d'après mods/fabric, mods/forge ou mods/neoforge.
 * Les copies créées par le loader sont suivies dans runtime/staged-mods.txt afin
 * de ne jamais supprimer un fichier placé là par l'utilisateur.
 */
public final class ExtensionInstaller {
    private static final String[] PLUGIN_SUBFOLDERS = {"plugins/bukkit", "plugins/spigot", "plugins/paper"};
    private static final String MANIFEST = "runtime/staged-mods.txt";

    private final JarInspector inspector = new JarInspector();

    /** Copie les plugins des sous-dossiers vers plugins/. */
    public List<String> installStagedPlugins(Path root) {
        List<String> actions = new ArrayList<String>();
        for (String folder : PLUGIN_SUBFOLDERS) {
            for (Path jar : jars(root.resolve(folder))) {
                String relative = relative(root, jar);
                ExtensionInfo.Type type = inspector.inspect(jar);
                if (type != ExtensionInfo.Type.BUKKIT_PLUGIN) {
                    actions.add("Non installé " + relative + " : " + describe(type)
                            + " — Paper ne peut pas le charger comme plugin.");
                    continue;
                }
                Path target = root.resolve("plugins").resolve(jar.getFileName());
                String destination = "plugins/" + jar.getFileName().toString();
                if (Files.exists(target)) {
                    actions.add("Déjà en place " + destination + " (le fichier de plugins/ fait foi)");
                    continue;
                }
                try {
                    Files.copy(jar, target, StandardCopyOption.COPY_ATTRIBUTES);
                    actions.add("Installé " + relative + " -> " + destination);
                } catch (IOException e) {
                    actions.add("Échec de l'installation de " + relative + " : " + e.getMessage());
                }
            }
        }
        return actions;
    }

    /** Installe les mods du loader de la plateforme dans mods/ et retire les copies devenues inutiles. */
    public List<String> stageMods(Path root, PlatformInfo platform) {
        List<String> actions = new ArrayList<String>();
        Path manifest = root.resolve(MANIFEST);
        List<String[]> tracked = readManifest(root);
        List<String[]> keep = new ArrayList<String[]>();
        for (String[] entry : tracked) {
            if (platform.loads(entry[1])) { keep.add(entry); continue; }
            Path file = root.resolve(entry[0]);
            try {
                if (Files.deleteIfExists(file)) actions.add("Retiré de mods/ : " + entry[0] + " (non chargeable par " + platform.label + ")");
            } catch (IOException e) {
                actions.add("Impossible de retirer " + entry[0] + " : " + e.getMessage());
            }
        }
        if (!platform.hasModLoader()) { writeManifest(root, manifest, keep); return actions; }

        String loader = platform.modLoader;
        Path source = root.resolve("mods").resolve(platform.stagingFolder(loader));
        for (Path jar : jars(source)) {
            ExtensionInfo.Type type = inspector.inspect(jar);
            boolean compatible = loader.equals(JarInspector.loaderOf(type))
                    || type == ExtensionInfo.Type.MULTI_MOD; // un JAR multi-plateforme convient à tout mod loader
            if (!compatible) {
                actions.add("Non installé " + relative(root, jar) + " : " + describe(type)
                        + " — ce serveur attend un mod " + loader + ".");
                continue;
            }
            Path target = root.resolve("mods").resolve(jar.getFileName());
            String destination = relative(root, target);
            boolean ours = contains(keep, destination);
            try {
                if (!Files.exists(target) || Files.size(target) != Files.size(jar)) {
                    Files.copy(jar, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                    actions.add("Installé " + relative(root, jar) + " -> " + destination);
                    if (!ours) keep.add(new String[]{destination, loader});
                } else if (!ours) {
                    actions.add("Déjà en place " + destination + " (fichier non placé par le loader, conservé tel quel)");
                }
            } catch (IOException e) {
                actions.add("Échec de l'installation de " + destination + " : " + e.getMessage());
            }
        }
        writeManifest(root, manifest, keep);
        return actions;
    }

    private List<String[]> readManifest(Path root) {
        List<String[]> result = new ArrayList<String[]>();
        Path manifest = root.resolve(MANIFEST);
        if (!Files.isRegularFile(manifest)) return result;
        try {
            for (String line : Files.readAllLines(manifest, StandardCharsets.UTF_8)) {
                if (line.trim().length() == 0 || line.startsWith("#")) continue;
                String[] parts = line.split("\t");
                if (parts.length == 2 && parts[0].length() > 0) result.add(parts);
            }
        } catch (IOException ignored) {
            return new ArrayList<String[]>();
        }
        return result;
    }

    private void writeManifest(Path root, Path manifest, List<String[]> entries) {
        try {
            Files.createDirectories(manifest.getParent());
            StringBuilder out = new StringBuilder();
            out.append("# Copies de mods créées par UniversalServerLoader (chemin<TAB>loader).\n");
            for (String[] entry : entries) out.append(entry[0]).append('\t').append(entry[1]).append('\n');
            Files.write(manifest, out.toString().getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        } catch (IOException ignored) {
            // Le manifeste est un optimisme, pas une obligation : l'installation reste possible.
        }
    }

    private static boolean contains(List<String[]> entries, String path) {
        for (String[] entry : entries) if (entry[0].equals(path)) return true;
        return false;
    }

    /** JAR d'un dossier, triés par nom, fichiers cachés exclus. */
    public static List<Path> jars(Path directory) {
        List<Path> result = new ArrayList<Path>();
        if (!Files.isDirectory(directory)) return result;
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(directory)) {
            for (Path entry : entries) {
                String name = entry.getFileName().toString();
                if (name.startsWith(".") || Files.isDirectory(entry)) continue;
                if (name.toLowerCase().endsWith(".jar")) result.add(entry);
            }
        } catch (IOException ignored) {
            return result;
        }
        Collections.sort(result, new Comparator<Path>() {
            public int compare(Path left, Path right) {
                return left.getFileName().toString().compareToIgnoreCase(right.getFileName().toString());
            }
        });
        return result;
    }

    public static String relative(Path root, Path file) {
        String value = root.relativize(file).toString().replace('\\', '/');
        return value.length() == 0 ? file.getFileName().toString() : value;
    }

    private static String describe(ExtensionInfo.Type type) {
        switch (type) {
            case BUKKIT_PLUGIN: return "plugin Bukkit (plugin.yml)";
            case FABRIC_MOD: return "mod Fabric (fabric.mod.json)";
            case FORGE_MOD: return "mod Forge (mods.toml)";
            case NEOFORGE_MOD: return "mod NeoForge (neoforge.mods.toml)";
            case MULTI_MOD: return "mod multi-plateforme (fabric.mod.json + mods.toml)";
            case UNIVERSAL_ADDON: return "addon Universal (universal-addon.json)";
            default: return "aucun descripteur reconnu";
        }
    }
}
