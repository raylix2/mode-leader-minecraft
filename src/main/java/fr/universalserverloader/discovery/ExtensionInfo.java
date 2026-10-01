package fr.universalserverloader.discovery;

import java.nio.file.Path;

public final class ExtensionInfo {
    public enum Kind { PLUGIN, ADDON, FORGE_MOD, FABRIC_MOD, UNKNOWN, REJECTED }
    public enum Type { BUKKIT_PLUGIN, FABRIC_MOD, FORGE_MOD, NEOFORGE_MOD, MULTI_MOD, UNIVERSAL_ADDON, UNKNOWN }

    public final Kind kind;
    public final Path path;
    public final String message;
    /** Nature réelle lue dans le contenu du JAR (plugin.yml, fabric.mod.json, mods.toml...). */
    public final Type type;

    public ExtensionInfo(Kind kind, Path path, String message) {
        this(kind, path, Type.UNKNOWN, message);
    }

    public ExtensionInfo(Kind kind, Path path, Type type, String message) {
        this.kind = kind;
        this.path = path;
        this.type = type;
        this.message = message;
    }
}
