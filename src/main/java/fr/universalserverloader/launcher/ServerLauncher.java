package fr.universalserverloader.launcher;

import fr.universalserverloader.addon.AddonManager;
import fr.universalserverloader.config.LoaderConfig;
import fr.universalserverloader.logging.DualLogger;
import fr.universalserverloader.diagnostics.JavaRuntime;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class ServerLauncher {
    private final Path root;
    private final LoaderConfig config;
    private final DualLogger logger;
    private final InstanceControl control;
    private final AddonManager addons;

    public ServerLauncher(Path root, LoaderConfig config, DualLogger logger, InstanceControl control, AddonManager addons) {
        this.root = root; this.config = config; this.logger = logger; this.control = control; this.addons = addons;
    }

    public int run(Path serverJar, List<String> extraArguments) throws IOException, InterruptedException {
        boolean requestedRestart;
        int exitCode;
        int crashRestarts = 0;
        do {
            requestedRestart = false;
            List<String> command = buildCommand(serverJar, extraArguments);
            logger.log("Java utilisé: " + System.getProperty("java.version") + " (" + javaExecutable() + ")");
            if (JavaRuntime.majorVersion() >= config.minimumJavaVersion)
                logger.log("Java compatible avec " + config.platform + " " + config.targetMinecraftVersion + ".");
            else logger.log("Java incompatible: version " + config.minimumJavaVersion + "+ requise.");
            logger.log("Démarrage du processus serveur (arguments séparés, sans shell)...");
            Process process = new ProcessBuilder(command).directory(root.toFile()).redirectErrorStream(true).start();
            Path pidFile = root.resolve("runtime/server.pid");
            String childPid = pid(process);
            if (childPid.matches("\\d+")) Files.write(pidFile, childPid.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            final BufferedReader output = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
            Thread pump = new Thread(new Runnable() {
                public void run() {
                    String line;
                    try { while ((line = output.readLine()) != null) logger.log("[SERVER] " + line); }
                    catch (IOException e) { logger.log("Lecture serveur terminée: " + e.getMessage()); }
                }
            }, "server-log-pump");
            pump.setDaemon(true);
            pump.start();
            BufferedWriter input = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
            BufferedReader console = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
            boolean consoleClosed = false;
            logger.log("Serveur actif. Commandes loader: stop, restart, status, help. Les autres lignes vont au serveur.");
            while (process.isAlive()) {
                String line = control.poll();
                if (line == null && !consoleClosed) {
                    try {
                        if (console.ready()) {
                            String typed = console.readLine();
                            if (typed == null) consoleClosed = true; // flux fermé: on ne boucle plus dessus
                            else line = typed;
                        }
                    } catch (IOException e) { consoleClosed = true; }
                }
                if (line != null) {
                    String normalized = line.trim().toLowerCase();
                    if ("stop".equals(normalized) || "restart".equals(normalized)) {
                        requestedRestart = "restart".equals(normalized);
                        logger.log(requestedRestart ? "Redémarrage demandé..." : "Arrêt demandé...");
                        send(input, "stop");
                        if (!process.waitFor(30, TimeUnit.SECONDS)) {
                            logger.log("Le serveur ne s'est pas arrêté en 30 s; arrêt normal du processus demandé.");
                            process.destroy();
                        }
                        break;
                    } else if ("status".equals(normalized)) logger.log("État: serveur actif (PID si disponible: " + pid(process) + ")");
                    else if ("help".equals(normalized)) logger.log("Commandes: stop, restart, status, help; commandes addons; sinon transmission au serveur.");
                    else {
                        try { if (!addons.execute(line)) send(input, line); }
                        catch (Exception e) { logger.log("Erreur de commande addon: " + e.getMessage()); }
                    }
                }
                Thread.sleep(100L);
            }
            exitCode = process.waitFor();
            Files.deleteIfExists(pidFile);
            pump.join(2000L);
            output.close();
            logger.log("Processus serveur terminé avec le code " + exitCode + ".");
            if (!requestedRestart && config.autoRestart && exitCode != 0 && crashRestarts < config.maxCrashRestarts) {
                requestedRestart = true;
                crashRestarts++;
                long delay = Math.min(300L, (long) config.restartBackoffSeconds * (1L << Math.min(crashRestarts - 1, 5)));
                logger.log("autoRestart=true: tentative " + crashRestarts + "/" + config.maxCrashRestarts +
                        " dans " + delay + " s (temporisation anti-boucle de crash).");
                Thread.sleep(delay * 1000L);
            } else if (!requestedRestart && config.autoRestart && exitCode != 0) {
                logger.log("Limite de redémarrages atteinte; arrêt pour éviter une boucle de crash.");
            }
        } while (requestedRestart);
        return exitCode;
    }

    private List<String> buildCommand(Path serverJar, List<String> extras) {
        List<String> command = new ArrayList<String>();
        command.add(javaExecutable());
        command.add("-Xms" + config.minMemory);
        command.add("-Xmx" + config.maxMemory);
        command.addAll(config.javaArguments);
        command.add("-jar"); command.add(serverJar.toAbsolutePath().toString());
        command.addAll(config.serverArguments); command.addAll(extras);
        return command;
    }

    private static void send(BufferedWriter input, String line) throws IOException {
        input.write(line); input.newLine(); input.flush();
    }

    private static String javaExecutable() {
        String suffix = System.getProperty("os.name", "").toLowerCase().contains("win") ? "java.exe" : "java";
        return java.nio.file.Paths.get(System.getProperty("java.home"), "bin", suffix).toString();
    }

    private static String pid(Process process) {
        try { return String.valueOf(Process.class.getMethod("pid").invoke(process)); }
        catch (Exception ignored) { return "non disponible sous Java 8"; }
    }
}
