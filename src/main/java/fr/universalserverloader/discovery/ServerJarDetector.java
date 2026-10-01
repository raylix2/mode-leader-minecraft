package fr.universalserverloader.discovery;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ServerJarDetector {
    public Path detect(Path root, String configured) throws IOException {
        Path requested = root.resolve(configured).normalize();
        if (Files.isRegularFile(requested) && requested.getFileName().toString().toLowerCase().endsWith(".jar")) return requested;
        Path conventional = root.resolve("server.jar");
        if (Files.isRegularFile(conventional)) return conventional;
        try (DirectoryStream<Path> jars = Files.newDirectoryStream(root, "*.jar")) {
            Path candidate = null;
            for (Path jar : jars) {
                String name = jar.getFileName().toString().toLowerCase();
                if (name.contains("server") && !name.contains("universal-loader")) {
                    if (candidate != null) return null; // Ambiguïté: ne jamais choisir au hasard.
                    candidate = jar;
                }
            }
            return candidate;
        }
    }
}
