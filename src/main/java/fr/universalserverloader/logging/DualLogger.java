package fr.universalserverloader.logging;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;

public final class DualLogger implements Closeable {
    private final BufferedWriter writer;

    public DualLogger(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        if (Files.isRegularFile(file) && Files.size(file) > 0) {
            Path archive = file.getParent().resolve("loader-" + new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date()) + ".log");
            Files.copy(file, archive, StandardCopyOption.COPY_ATTRIBUTES);
        }
        writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
    }

    public synchronized void log(String line) {
        System.out.println(line);
        try { writer.write(line); writer.newLine(); writer.flush(); }
        catch (IOException e) { System.err.println("Impossible d'écrire le journal: " + e.getMessage()); }
    }

    public synchronized void close() throws IOException { writer.close(); }
}
