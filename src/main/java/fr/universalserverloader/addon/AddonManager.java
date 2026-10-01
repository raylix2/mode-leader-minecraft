package fr.universalserverloader.addon;

import fr.universalserverloader.api.UniversalAddon;
import fr.universalserverloader.api.UniversalCommand;
import fr.universalserverloader.api.UniversalContext;
import fr.universalserverloader.logging.DualLogger;
import java.io.Closeable;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AddonManager implements Closeable {
    private final Path root;
    private final DualLogger logger;
    private final List<UniversalAddon> addons = new ArrayList<UniversalAddon>();
    private final List<URLClassLoader> loaders = new ArrayList<URLClassLoader>();
    private final Map<String, UniversalCommand> commands = new HashMap<String, UniversalCommand>();
    private final Set<String> ids = new HashSet<String>();

    public AddonManager(Path root, DualLogger logger) { this.root = root; this.logger = logger; }

    public void load(List<Path> jars) {
        for (Path jar : jars) {
            try { loadOne(jar); }
            catch (Exception e) { logger.log("[ADDON] Ignoré " + jar.getFileName() + ": " + e.getMessage()); }
        }
    }

    private void loadOne(Path path) throws Exception {
        String json;
        try (JarFile jar = new JarFile(path.toFile())) {
            JarEntry descriptor = jar.getJarEntry("universal-addon.json");
            if (descriptor == null) throw new IOException("universal-addon.json absent");
            ByteArrayOutputStream data = new ByteArrayOutputStream();
            java.io.InputStream in = jar.getInputStream(descriptor);
            byte[] buffer = new byte[1024];
            int count;
            while ((count = in.read(buffer)) >= 0) data.write(buffer, 0, count);
            in.close();
            json = new String(data.toByteArray(), StandardCharsets.UTF_8);
        }
        final String id = field(json, "id");
        if (ids.contains(id)) throw new IOException("identifiant d'addon déjà utilisé: " + id);
        String main = field(json, "main");
        String api = field(json, "apiVersion");
        if (!"1".equals(api)) throw new IOException("apiVersion non supportée: " + api);
        final Path dataFolder = root.resolve("addons-data").resolve(id);
        Files.createDirectories(dataFolder);
        URLClassLoader loader = new URLClassLoader(new java.net.URL[]{path.toUri().toURL()}, UniversalAddon.class.getClassLoader());
        Object instance;
        try { instance = Class.forName(main, true, loader).newInstance(); }
        catch (Exception e) { loader.close(); throw new IOException("classe main illisible: " + e.getMessage()); }
        if (!(instance instanceof UniversalAddon)) { loader.close(); throw new IOException("la classe main n'implémente pas UniversalAddon"); }
        UniversalAddon addon = (UniversalAddon) instance;
        addons.add(addon); loaders.add(loader); ids.add(id);
        try {
            addon.onEnable(new UniversalContext() {
                public void log(String message) { logger.log("[" + id + "] " + message); }
                public Path dataFolder() { return dataFolder; }
                public void registerCommand(String name, UniversalCommand command) {
                    if (!name.matches("[a-zA-Z0-9_-]+")) throw new IllegalArgumentException("Nom de commande invalide");
                    if (commands.containsKey(name.toLowerCase())) throw new IllegalArgumentException("Commande déjà enregistrée: " + name);
                    commands.put(name.toLowerCase(), command);
                }
            });
        } catch (Exception e) {
            // L'addon est enregistré : close() le désactivera proprement malgré l'échec de onEnable.
            throw new IOException("échec de onEnable: " + e.getMessage());
        }
        logger.log("[ADDON] Activé: " + id);
    }

    private static String field(String json, String key) throws IOException {
        Matcher m = Pattern.compile("\\\"" + key + "\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"").matcher(json);
        if (!m.find()) throw new IOException("champ descriptor manquant: " + key);
        return m.group(1);
    }

    public boolean execute(String line) throws Exception {
        String trimmed = line.trim();
        if (trimmed.length() == 0) return false;
        String[] split = trimmed.split("\\s+");
        UniversalCommand command = commands.get(split[0].toLowerCase());
        if (command == null) return false;
        String[] args = new String[split.length - 1];
        System.arraycopy(split, 1, args, 0, args.length);
        command.execute(args);
        return true;
    }

    public void close() {
        for (int i = addons.size() - 1; i >= 0; i--) {
            try { addons.get(i).onDisable(); }
            catch (Exception e) { logger.log("[ADDON] Erreur onDisable: " + e.getMessage()); }
        }
        for (URLClassLoader loader : loaders) try { loader.close(); } catch (IOException ignored) { }
    }
}
