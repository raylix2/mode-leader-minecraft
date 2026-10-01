package fr.universalserverloader.discovery;

import java.nio.file.Path;

public final class ExtensionInfo {
    public enum Kind { PLUGIN, ADDON, FORGE_MOD, FABRIC_MOD, REJECTED }
    public final Kind kind;
    public final Path path;
    public final String message;

    public ExtensionInfo(Kind kind, Path path, String message) {
        this.kind = kind;
        this.path = path;
        this.message = message;
    }
}
