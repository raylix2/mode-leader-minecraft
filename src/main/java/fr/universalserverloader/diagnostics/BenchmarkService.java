package fr.universalserverloader.diagnostics;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.jar.JarFile;

public final class BenchmarkService {
    public String run(Path root, Path serverJar) throws Exception {
        if (serverJar == null) throw new IllegalStateException("server.jar requis pour le benchmark local");
        hash(serverJar); // échauffement disque/cache
        long bytes = Files.size(serverJar);
        long start = System.nanoTime();
        String digest = null;
        int passes = 3;
        for (int i = 0; i < passes; i++) digest = hash(serverJar);
        double seconds = (System.nanoTime() - start) / 1_000_000_000.0;
        double mibPerSecond = (bytes * passes / 1024.0 / 1024.0) / seconds;
        int entries;
        try (JarFile jar = new JarFile(serverJar.toFile())) { entries = jar.size(); }
        String report = "=== Benchmark local (pas un benchmark TPS) ===\n" +
                "Serveur: " + serverJar.getFileName() + " (" + (bytes / 1024 / 1024) + " MiB, " + entries + " entrées JAR)\n" +
                "SHA-256: " + digest + "\n" +
                "Lecture+hachage: " + String.format(Locale.ROOT, "%.1f MiB/s", mibPerSecond) + " sur " + passes + " passes\n" +
                "CPU logiques: " + Runtime.getRuntime().availableProcessors() + "\n" +
                "Pour mesurer TPS/MSPT/GC sous charge: démarrez Paper puis utilisez la commande profile.\n";
        Path destination = root.resolve("logs/benchmark-latest.txt");
        Files.createDirectories(destination.getParent());
        Files.write(destination, report.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        return report;
    }

    private static String hash(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[128 * 1024];
            int count;
            while ((count = in.read(buffer)) >= 0) digest.update(buffer, 0, count);
        }
        StringBuilder out = new StringBuilder();
        for (byte b : digest.digest()) out.append(String.format(Locale.ROOT, "%02x", b & 0xff));
        return out.toString();
    }
}
