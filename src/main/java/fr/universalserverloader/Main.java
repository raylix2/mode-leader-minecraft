package fr.universalserverloader;

import fr.universalserverloader.addon.AddonManager;
import fr.universalserverloader.cli.CliCommand;
import fr.universalserverloader.cli.CliParser;
import fr.universalserverloader.compatibility.CompatibilityReporter;
import fr.universalserverloader.config.ConfigManager;
import fr.universalserverloader.config.LoaderConfig;
import fr.universalserverloader.discovery.DiscoveryService;
import fr.universalserverloader.discovery.ExtensionInfo;
import fr.universalserverloader.discovery.ServerJarDetector;
import fr.universalserverloader.platform.ExtensionInstaller;
import fr.universalserverloader.platform.LoadPlan;
import fr.universalserverloader.platform.PlatformDetector;
import fr.universalserverloader.platform.PlatformInfo;
import fr.universalserverloader.diagnostics.BenchmarkService;
import fr.universalserverloader.diagnostics.DoctorService;
import fr.universalserverloader.diagnostics.JavaRuntime;
import fr.universalserverloader.launcher.EulaManager;
import fr.universalserverloader.launcher.InstanceControl;
import fr.universalserverloader.launcher.ServerLauncher;
import fr.universalserverloader.launcher.WorkspaceInitializer;
import fr.universalserverloader.logging.DualLogger;
import fr.universalserverloader.gui.ServerDashboard;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public final class Main {
    public static final String VERSION = "0.4.0";

    public static void main(String[] args) {
        int code = 0;
        try { code = execute(args); }
        catch (Exception e) {
            System.err.println("ERREUR: " + e.getMessage());
            System.err.println("Solution possible: vérifiez config/loader.json, les droits d'accès et la version de Java.");
            if (Boolean.getBoolean("universal.debug")) e.printStackTrace();
            code = 1;
        }
        if (code != 0) System.exit(code);
    }

    static int execute(String[] args) throws Exception {
        Path root = runtimeRoot();
        new WorkspaceInitializer().initialize(root);
        LoaderConfig config = new ConfigManager().load(root.resolve("config/loader.json"));
        CliParser.Result parsed = new CliParser().parse(args);
        if (parsed.command == CliCommand.GUI) {
            ServerDashboard.open(root, config);
            return 0;
        }
        if (parsed.command == CliCommand.HELP) { printHelp(); return 0; }
        if (parsed.command == CliCommand.VERSION) { System.out.println("UniversalServerLoader " + VERSION); return 0; }
        if (parsed.command == CliCommand.STATUS) {
            System.out.println(InstanceControl.isRunning(root) ? "Serveur actif." : "Serveur arrêté."); return 0;
        }
        if (parsed.command == CliCommand.PROFILE || parsed.command == CliCommand.HEALTH) {
            String command = parsed.command == CliCommand.PROFILE
                    ? "spark profiler start --timeout 120"
                    : "spark health show --memory";
            InstanceControl.send(root, command);
            System.out.println("Commande Paper intégrée envoyée: " + command); return 0;
        }
        if (parsed.command == CliCommand.STOP || parsed.command == CliCommand.RESTART) {
            InstanceControl.send(root, parsed.command.name().toLowerCase());
            System.out.println("Commande " + parsed.command.name().toLowerCase() + " envoyée."); return 0;
        }

        Path serverJar = new ServerJarDetector().detect(root, config.serverJar);
        PlatformDetector.Result detected = new PlatformDetector().detect(serverJar, config.platform);
        PlatformInfo platform = detected.platform;

        // Installation réelle des extensions là où la plateforme va les chercher.
        List<String> actions = new ArrayList<String>();
        ExtensionInstaller installer = new ExtensionInstaller();
        if (config.installStagedPlugins && config.scanPlugins && platform.bukkitPlugins)
            actions.addAll(installer.installStagedPlugins(root));
        if (config.stageMods && config.scanMods)
            actions.addAll(installer.stageMods(root, platform));

        DiscoveryService discovery = new DiscoveryService();
        List<ExtensionInfo> items = discovery.scan(root, config);
        LoadPlan plan = new LoadPlan(root, platform, detected.warning,
                config.installStagedPlugins, config.stageMods, items);
        plan.write(root.resolve("runtime/load-plan.txt"), VERSION);
        String report = new CompatibilityReporter().create(root, items, plan, actions);
        System.out.print(report);
        if (parsed.command == CliCommand.SCAN) return 0;

        if (parsed.command == CliCommand.DOCTOR) {
            System.out.print(new DoctorService().diagnose(root, config, serverJar, plan)); return 0;
        }
        if (parsed.command == CliCommand.BENCHMARK) {
            System.out.print(new BenchmarkService().run(root, serverJar)); return 0;
        }

        boolean simulation = parsed.serverArguments.contains("--loader-simulate");
        if (serverJar == null && !simulation) {
            System.err.println("server.jar absent ou choix ambigu dans " + root.toAbsolutePath());
            System.err.println("Placez un serveur Bukkit/Spigot/Paper existant sous le nom server.jar, puis relancez start.");
            return 2;
        }
        if (simulation) {
            System.out.println("[SIMULATION] Java " + System.getProperty("java.version") + " disponible.");
            System.out.println("[SIMULATION] La configuration, les dossiers et le scan sont valides; aucun serveur n'a été lancé.");
            return 0;
        }
        if (JavaRuntime.majorVersion() < config.minimumJavaVersion) {
            System.err.println("Java " + config.minimumJavaVersion + "+ requis pour " + config.platform + " " + config.targetMinecraftVersion +
                    "; version active: " + System.getProperty("java.version"));
            return 4;
        }
        new EulaManager().apply(root, config.agreeToEula);
        InstanceControl control = InstanceControl.acquire(root);
        if (control == null) { System.err.println("Une instance serveur est déjà active."); return 3; }
        try (InstanceControl held = control; DualLogger logger = new DualLogger(root.resolve("logs/loader-latest.log"))) {
            AddonManager addons = new AddonManager(root, logger);
            try {
                List<Path> addonJars = new ArrayList<Path>();
                for (ExtensionInfo item : items) if (item.kind == ExtensionInfo.Kind.ADDON) addonJars.add(item.path);
                addons.load(addonJars);
                return new ServerLauncher(root, config, logger, control, addons).run(serverJar, parsed.serverArguments);
            } finally { addons.close(); }
        }
    }

    private static Path runtimeRoot() throws Exception {
        String override = System.getProperty("universal.root");
        if (override != null) return Paths.get(override).toAbsolutePath().normalize();
        URI location = Main.class.getProtectionDomain().getCodeSource().getLocation().toURI();
        Path source = Paths.get(location).toAbsolutePath();
        if (Files.isRegularFile(source) && source.getParent() != null) return source.getParent();
        return Paths.get("").toAbsolutePath().resolve("UniversalServer");
    }

    private static void printHelp() {
        System.out.println("UniversalServerLoader " + VERSION);
        System.out.println("Usage: java -jar universal-loader.jar <gui|start|stop|restart|status|scan|doctor|benchmark|profile|health|help|version> [-- arguments serveur]");
        System.out.println("Démo sans server.jar: start --loader-simulate");
        System.out.println("Paper actif: profile lance Spark 120 s; health affiche TPS/CPU/mémoire.");
    }
}
