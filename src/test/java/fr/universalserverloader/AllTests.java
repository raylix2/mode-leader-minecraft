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
import fr.universalserverloader.platform.ExtensionInstaller;
import fr.universalserverloader.platform.JarInspector;
import fr.universalserverloader.platform.LoadPlan;
import fr.universalserverloader.platform.PlatformDetector;
import fr.universalserverloader.platform.PlatformInfo;
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
        testPlatformDetection(temp.resolve("platform"));
        testJarInspector(temp.resolve("inspector"));
        testPluginInstallation(temp.resolve("install-plugins"));
        testModStaging(temp.resolve("staging"));
        testLoadPlan(temp.resolve("plan"));
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
        LoadPlan plan = new LoadPlan(root, PlatformInfo.of("paper", true), null, true, true, items);
        String report = new CompatibilityReporter().create(root, items, plan,
                java.util.Collections.<String>emptyList());
        check(report.contains("Plateforme:"), "en-tête du rapport de compatibilité");
        check(report.contains("nécessite un serveur Forge"), "mod Forge bloqué sur Paper");
        check(report.contains("Résumé:"), "résumé du rapport de compatibilité");
    }

    /** Un JAR minimal contenant les entrées indiquées. */
    private static void writeJar(Path file, String... entries) throws Exception {
        Files.createDirectories(file.getParent());
        java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(Files.newOutputStream(file));
        for (String entry : entries) {
            zip.putNextEntry(new java.util.zip.ZipEntry(entry));
            zip.write("contenu".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        zip.close();
    }

    private static String detected(Path jar) {
        return new PlatformDetector().detect(jar, "auto").platform.id;
    }

    private static void testPlatformDetection(Path root) throws Exception {
        writeJar(root.resolve("paper.jar"), "io/papermc/paperclip/Main.class", "paperclip/libs/a.class");
        check("paper".equals(detected(root.resolve("paper.jar"))), "détection Paper (paperclip)");
        writeJar(root.resolve("fabric.jar"), "net/fabricmc/loader/impl/Fabric.class");
        check("fabric".equals(detected(root.resolve("fabric.jar"))), "détection Fabric");
        writeJar(root.resolve("forge.jar"), "META-INF/mods.toml");
        check("forge".equals(detected(root.resolve("forge.jar"))), "détection Forge");
        writeJar(root.resolve("neo.jar"), "META-INF/neoforge.mods.toml");
        check("neoforge".equals(detected(root.resolve("neo.jar"))), "détection NeoForge");
        writeJar(root.resolve("hybrid.jar"), "org/bukkit/Bukkit.class", "net/minecraftforge/fml/Mod.class");
        check("hybrid-forge".equals(detected(root.resolve("hybrid.jar"))), "détection hybride Bukkit+Forge");
        writeJar(root.resolve("spigot.jar"), "org/bukkit/Bukkit.class");
        check("spigot".equals(detected(root.resolve("spigot.jar"))), "détection Bukkit/Spigot");
        // Sans jar détectable, la valeur déclarée en configuration fait foi.
        check("forge".equals(new PlatformDetector().detect(null, "forge").platform.id), "plateforme déclarée en secours");
    }

    private static void testJarInspector(Path root) throws Exception {
        JarInspector inspector = new JarInspector();
        writeJar(root.resolve("plugin.jar"), "plugin.yml", "com/demo/Demo.class");
        check(inspector.inspect(root.resolve("plugin.jar")) == ExtensionInfo.Type.BUKKIT_PLUGIN, "lecture plugin.yml");
        writeJar(root.resolve("fabric.jar"), "fabric.mod.json");
        check(inspector.inspect(root.resolve("fabric.jar")) == ExtensionInfo.Type.FABRIC_MOD, "lecture fabric.mod.json");
        writeJar(root.resolve("forge.jar"), "META-INF/mods.toml");
        check(inspector.inspect(root.resolve("forge.jar")) == ExtensionInfo.Type.FORGE_MOD, "lecture mods.toml");
        writeJar(root.resolve("addon.jar"), "universal-addon.json");
        check(inspector.inspect(root.resolve("addon.jar")) == ExtensionInfo.Type.UNIVERSAL_ADDON, "lecture universal-addon.json");
        Files.write(root.resolve("broken.jar"), new byte[]{1});
        check(inspector.inspect(root.resolve("broken.jar")) == ExtensionInfo.Type.UNKNOWN, "jar illisible");
    }

    private static void testPluginInstallation(Path root) throws Exception {
        new WorkspaceInitializer().initialize(root);
        writeJar(root.resolve("plugins/paper/demo.jar"), "plugin.yml", "com/demo/Demo.class");
        List<String> actions = new ExtensionInstaller().installStagedPlugins(root);
        check(Files.isRegularFile(root.resolve("plugins/demo.jar")), "installation du plugin dans plugins/");
        check(actions.get(0).startsWith("Installé"), "journal d'installation");
        List<String> again = new ExtensionInstaller().installStagedPlugins(root);
        check(again.get(0).contains("Déjà en place"), "installation idempotente");
    }

    private static void testModStaging(Path root) throws Exception {
        new WorkspaceInitializer().initialize(root);
        writeJar(root.resolve("mods/fabric/demo.jar"), "fabric.mod.json");
        new ExtensionInstaller().stageMods(root, PlatformInfo.of("fabric", true));
        check(Files.isRegularFile(root.resolve("mods/demo.jar")), "installation du mod Fabric dans mods/");
        new ExtensionInstaller().stageMods(root, PlatformInfo.of("paper", true));
        check(!Files.exists(root.resolve("mods/demo.jar")), "retrait du mod lors du retour sur Paper");
        check(Files.isRegularFile(root.resolve("mods/fabric/demo.jar")), "mod source conservé");
    }

    private static void testLoadPlan(Path root) throws Exception {
        new WorkspaceInitializer().initialize(root);
        writeJar(root.resolve("plugins/ok.jar"), "plugin.yml");
        writeJar(root.resolve("mods/fabric/mod.jar"), "fabric.mod.json");
        List<ExtensionInfo> items = new DiscoveryService().scan(root, new LoaderConfig());

        LoadPlan paper = new LoadPlan(root, PlatformInfo.of("paper", true), null, true, true, items);
        boolean pluginLoaded = false, modBlocked = false;
        for (LoadPlan.Entry entry : paper.entries()) {
            if (entry.item.path.endsWith("ok.jar")) pluginLoaded = entry.willLoad;
            if (entry.item.path.endsWith("mod.jar")) modBlocked = !entry.willLoad;
        }
        check(pluginLoaded, "plan Paper: plugin chargé");
        check(modBlocked, "plan Paper: mod Fabric bloqué");

        LoadPlan fabric = new LoadPlan(root, PlatformInfo.of("fabric", true), null, true, true, items);
        boolean modLoaded = false;
        for (LoadPlan.Entry entry : fabric.entries())
            if (entry.item.path.endsWith("mod.jar")) modLoaded = entry.willLoad;
        check(modLoaded, "plan Fabric: mod Fabric chargé");

        Path file = root.resolve("runtime/load-plan.txt");
        fabric.write(file, "test");
        String written = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        check(written.contains("plateforme=fabric"), "écriture du plan de chargement");
        check(written.contains("NON\tplugins/ok.jar"), "plan écrit: plugin bloqué sur Fabric");
        check(written.contains("OUI\tmods/fabric/mod.jar"), "plan écrit: mod Fabric chargé");
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
