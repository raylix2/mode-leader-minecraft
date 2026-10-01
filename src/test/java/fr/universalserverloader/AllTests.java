package fr.universalserverloader;

import fr.universalserverloader.cli.CliCommand;
import fr.universalserverloader.cli.CliParser;
import fr.universalserverloader.addon.AddonManager;
import fr.universalserverloader.compatibility.CompatibilityReporter;
import fr.universalserverloader.config.ConfigManager;
import fr.universalserverloader.config.LoaderConfig;
import fr.universalserverloader.discovery.DiscoveryService;
import fr.universalserverloader.discovery.ExtensionInfo;
import fr.universalserverloader.discovery.ServerJarDetector;
import fr.universalserverloader.launcher.WorkspaceInitializer;
import fr.universalserverloader.logging.DualLogger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class AllTests {
    private static int count;

    public static void main(String[] args) throws Exception {
        Path temp = Files.createTempDirectory("universal-loader-tests-");
        testFolders(temp.resolve("folders"));
        testConfig(temp.resolve("config"));
        testEulaConfigUpdate(temp.resolve("eula-config"));
        testServerDetection(temp.resolve("detect"));
        testMissingServer(temp.resolve("missing"));
        testExtensions(temp.resolve("extensions"));
        testReport(temp.resolve("report"));
        testCli();
        testExampleAddon(temp.resolve("addon"));
        System.out.println("SUCCÈS: " + count + " assertions, tous les tests sont passés.");
    }

    private static void testFolders(Path root) throws Exception {
        new WorkspaceInitializer().initialize(root);
        check(Files.isDirectory(root.resolve("plugins/bukkit")), "création plugins/bukkit");
        check(Files.isDirectory(root.resolve("mods/fabric")), "création mods/fabric");
        check(Files.isDirectory(root.resolve("logs")), "création logs");
    }

    private static void testConfig(Path root) throws Exception {
        Files.createDirectories(root.resolve("config"));
        Files.write(root.resolve("config/loader.json"), ConfigManager.DEFAULT_JSON.getBytes(StandardCharsets.UTF_8));
        LoaderConfig c = new ConfigManager().load(root.resolve("config/loader.json"));
        check("512M".equals(c.minMemory), "lecture loader.json");
        check(!c.agreeToEula, "EULA désactivée par défaut");
    }

    private static void testEulaConfigUpdate(Path root) throws Exception {
        Files.createDirectories(root.resolve("config"));
        Path file = root.resolve("config/loader.json");
        Files.write(file, ConfigManager.DEFAULT_JSON.getBytes(StandardCharsets.UTF_8));
        ConfigManager manager = new ConfigManager();
        manager.setEulaConsent(file, true);
        check(manager.load(file).agreeToEula, "consentement EULA explicite depuis la GUI");
        manager.setMemoryGib(file, 5);
        LoaderConfig changed = manager.load(file);
        check("512M".equals(changed.minMemory) && "5G".equals(changed.maxMemory), "réglage RAM dynamique depuis la GUI");
        check(Files.newDirectoryStream(root.resolve("config"), "loader.json.backup-*").iterator().hasNext(), "sauvegarde de configuration EULA");
    }

    private static void testServerDetection(Path root) throws Exception {
        Files.createDirectories(root); Files.write(root.resolve("server.jar"), new byte[]{1});
        check(new ServerJarDetector().detect(root, "server.jar") != null, "détection server.jar");
    }

    private static void testMissingServer(Path root) throws Exception {
        Files.createDirectories(root);
        check(new ServerJarDetector().detect(root, "server.jar") == null, "absence server.jar");
    }

    private static void testExtensions(Path root) throws Exception {
        new WorkspaceInitializer().initialize(root);
        Files.write(root.resolve("plugins/paper/example.jar"), new byte[]{1});
        Files.write(root.resolve("plugins/paper/readme.txt"), new byte[]{1});
        List<ExtensionInfo> items = new DiscoveryService().scan(root, new LoaderConfig());
        boolean jar = false, rejected = false;
        for (ExtensionInfo i : items) { jar |= i.kind == ExtensionInfo.Kind.PLUGIN; rejected |= i.kind == ExtensionInfo.Kind.REJECTED; }
        check(jar, "détection plugin .jar"); check(rejected, "rejet non-JAR");
    }

    private static void testReport(Path root) throws Exception {
        new WorkspaceInitializer().initialize(root);
        Files.write(root.resolve("mods/forge/mod.jar"), new byte[]{1});
        List<ExtensionInfo> items = new DiscoveryService().scan(root, new LoaderConfig());
        String report = new CompatibilityReporter().create(root, items);
        check(report.contains("futur adaptateur Forge"), "rapport de compatibilité");
    }

    private static void testCli() {
        CliParser.Result result = new CliParser().parse(new String[]{"start", "--", "nogui"});
        check(result.command == CliCommand.START, "parsing commande start");
        check(result.serverArguments.size() == 1 && "nogui".equals(result.serverArguments.get(0)), "parsing arguments serveur");
        check(new CliParser().parse(new String[]{"scan"}).command == CliCommand.SCAN, "parsing commande scan");
        check(new CliParser().parse(new String[]{"doctor"}).command == CliCommand.DOCTOR, "parsing commande doctor");
        check(new CliParser().parse(new String[]{"profile"}).command == CliCommand.PROFILE, "parsing commande profile");
        check(new CliParser().parse(new String[]{"gui"}).command == CliCommand.GUI, "parsing commande gui");
    }

    private static void testExampleAddon(Path root) throws Exception {
        new WorkspaceInitializer().initialize(root);
        Path example = java.nio.file.Paths.get("examples/example-addon/example-addon.jar").toAbsolutePath();
        check(Files.isRegularFile(example), "JAR addon d'exemple compilé");
        Path log = root.resolve("logs/addon-test.log");
        try (DualLogger logger = new DualLogger(log)) {
            AddonManager manager = new AddonManager(root, logger);
            manager.load(java.util.Collections.singletonList(example));
            check(manager.execute("hello test"), "commande addon enregistrée");
            manager.close();
        }
        check(new String(Files.readAllBytes(log), StandardCharsets.UTF_8).contains("Bonjour depuis UniversalAddon"), "cycle addon fonctionnel");
    }

    private static void check(boolean value, String name) {
        count++; if (!value) throw new AssertionError("Échec: " + name);
    }

}
