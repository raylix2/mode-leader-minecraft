package fr.universalserverloader.launcher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class WorkspaceInitializer {
    private static final String[] DIRECTORIES = {
            "config", "plugins", "plugins/bukkit", "plugins/spigot", "plugins/paper",
            "mods", "mods/forge", "mods/fabric", "addons", "addons-data", "logs", "runtime"
    };

    public void initialize(Path root) throws IOException {
        Files.createDirectories(root);
        for (String directory : DIRECTORIES) Files.createDirectories(root.resolve(directory));
    }
}
