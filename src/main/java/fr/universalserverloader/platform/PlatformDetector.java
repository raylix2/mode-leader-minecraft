package fr.universalserverloader.platform;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Identifie la plateforme en lisant les entrées du server.jar : Paper est un
 * « paperclip » (io/papermc/paperclip), CraftBukkit expose org/bukkit, Fabric
 * expose net/fabricmc, Forge expose net/minecraftforge et META-INF/mods.toml,
 * NeoForge expose META-INF/neoforge.mods.toml. Un serveur hybride contient à la
 * fois les marqueurs Bukkit et les marqueurs d'un mod loader.
 */
public final class PlatformDetector {
    public static final class Result {
        public final PlatformInfo platform;
        /** Avertissement à afficher à l'utilisateur, null s'il n'y a rien à signaler. */
        public final String warning;

        Result(PlatformInfo platform, String warning) {
            this.platform = platform;
            this.warning = warning;
        }
    }

    public Result detect(Path serverJar, String configured) {
        PlatformInfo declared = PlatformInfo.isAuto(configured)
                ? PlatformInfo.unknown(false) : PlatformInfo.of(configured, false);
        PlatformInfo scanned = scan(serverJar);
        if (scanned.isUnknown()) {
            if (PlatformInfo.isAuto(configured)) {
                return new Result(PlatformInfo.of("paper", false),
                        "Plateforme non détectable" + (serverJar == null ? " (server.jar absent)"
                                : " dans " + serverJar.getFileName())
                                + " : comportement par défaut Paper. Précisez \"platform\" dans config/loader.json.");
            }
            return new Result(declared, null);
        }
        if (PlatformInfo.isAuto(configured)) return new Result(scanned, null);
        if (!scanned.id.equals(declared.id)) {
            return new Result(scanned, "config/loader.json déclare \"" + declared.id + "\" mais "
                    + (serverJar == null ? "le server.jar" : serverJar.getFileName())
                    + " ressemble à un serveur " + scanned.label + ". Le contenu du jar fait foi; mettez \"platform\": \""
                    + scanned.id + "\" pour faire taire cet avertissement.");
        }
        return new Result(scanned, null);
    }

    private PlatformInfo scan(Path serverJar) {
        if (serverJar == null || !Files.isRegularFile(serverJar)) return PlatformInfo.unknown(true);
        boolean paper = false, bukkit = false, fabric = false, forge = false, neoforge = false;
        try (ZipFile zip = new ZipFile(serverJar.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (name.startsWith("io/papermc/") || name.startsWith("paperclip/")
                        || name.startsWith("com/destroystokyo/paper/") || name.equals("paper-plugin.yml")
                        || name.equals("META-INF/versions.list")) paper = true;
                else if (name.startsWith("org/bukkit/")) bukkit = true;
                else if (name.startsWith("net/fabricmc/")) fabric = true;
                else if (name.startsWith("net/neoforged/") || name.equals("META-INF/neoforge.mods.toml")) neoforge = true;
                else if (name.startsWith("net/minecraftforge/") || name.equals("META-INF/mods.toml")) forge = true;
            }
        } catch (IOException | RuntimeException e) {
            return PlatformInfo.unknown(true);
        }
        bukkit = bukkit || paper;
        if (bukkit && (forge || neoforge)) return PlatformInfo.of("hybrid-forge", true);
        if (bukkit && fabric) return PlatformInfo.of("hybrid-fabric", true);
        if (fabric) return PlatformInfo.of("fabric", true);
        if (neoforge) return PlatformInfo.of("neoforge", true);
        if (forge) return PlatformInfo.of("forge", true);
        if (paper) return PlatformInfo.of("paper", true);
        if (bukkit) return PlatformInfo.of("spigot", true);
        return PlatformInfo.unknown(true);
    }
}
