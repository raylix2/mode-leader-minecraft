package fr.universalserverloader.launcher;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public final class InstanceControl implements Closeable {
    private final Path commandFile;
    private final FileChannel channel;
    private final FileLock lock;

    private InstanceControl(Path root, FileChannel channel, FileLock lock) {
        this.commandFile = root.resolve("runtime/control.command");
        this.channel = channel;
        this.lock = lock;
    }

    public static InstanceControl acquire(Path root) throws IOException {
        Files.createDirectories(root.resolve("runtime"));
        FileChannel channel = FileChannel.open(root.resolve("runtime/server.lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        FileLock lock = channel.tryLock();
        if (lock == null) { channel.close(); return null; }
        InstanceControl control = new InstanceControl(root, channel, lock);
        Files.write(control.commandFile, new byte[0], StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        return control;
    }

    public static boolean isRunning(Path root) throws IOException {
        InstanceControl probe = acquire(root);
        if (probe == null) return true;
        probe.close();
        return false;
    }

    public static void send(Path root, String command) throws IOException {
        if (!isRunning(root)) throw new IOException("Aucune instance active.");
        Files.write(root.resolve("runtime/control.command"), command.getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
    }

    public String poll() throws IOException {
        if (!Files.exists(commandFile) || Files.size(commandFile) == 0) return null;
        String value = new String(Files.readAllBytes(commandFile), StandardCharsets.UTF_8).trim();
        Files.write(commandFile, new byte[0], StandardOpenOption.TRUNCATE_EXISTING);
        return value.length() == 0 ? null : value;
    }

    public void close() throws IOException {
        lock.release();
        channel.close();
    }
}
