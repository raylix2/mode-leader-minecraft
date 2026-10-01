package fr.universalserverloader.platform;

import fr.universalserverloader.discovery.ExtensionInfo;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Lit le contenu d'un JAR pour savoir ce qu'il est réellement, indépendamment
 * du dossier où l'utilisateur l'a déposé.
 */
public final class JarInspector {
    public ExtensionInfo.Type inspect(Path jar) {
        if (jar == null || !Files.isRegularFile(jar)) return ExtensionInfo.Type.UNKNOWN;
        if (!jar.getFileName().toString().toLowerCase().endsWith(".jar")) return ExtensionInfo.Type.UNKNOWN;
        boolean plugin = false, fabric = false, forge = false, neoforge = false, addon = false;
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if ("plugin.yml".equals(name) || "paper-plugin.yml".equals(name)) plugin = true;
                else if ("fabric.mod.json".equals(name)) fabric = true;
                else if ("META-INF/mods.toml".equals(name)) forge = true;
                else if ("META-INF/neoforge.mods.toml".equals(name)) neoforge = true;
                else if ("universal-addon.json".equals(name)) addon = true;
            }
        } catch (IOException | RuntimeException e) {
            return ExtensionInfo.Type.UNKNOWN;
        }
        if (addon) return ExtensionInfo.Type.UNIVERSAL_ADDON;
        if (plugin) return ExtensionInfo.Type.BUKKIT_PLUGIN;
        int modMarkers = (fabric ? 1 : 0) + (forge ? 1 : 0) + (neoforge ? 1 : 0);
        if (modMarkers >= 2) return ExtensionInfo.Type.MULTI_MOD;
        if (fabric) return ExtensionInfo.Type.FABRIC_MOD;
        if (neoforge) return ExtensionInfo.Type.NEOFORGE_MOD;
        if (forge) return ExtensionInfo.Type.FORGE_MOD;
        return ExtensionInfo.Type.UNKNOWN;
    }

    /** Loader attendu pour un type donné, "" si le type n'est pas un mod. "multi" pour un JAR multi-plateforme. */
    public static String loaderOf(ExtensionInfo.Type type) {
        if (type == ExtensionInfo.Type.FABRIC_MOD) return "fabric";
        if (type == ExtensionInfo.Type.FORGE_MOD) return "forge";
        if (type == ExtensionInfo.Type.NEOFORGE_MOD) return "neoforge";
        if (type == ExtensionInfo.Type.MULTI_MOD) return "multi";
        return "";
    }
}
