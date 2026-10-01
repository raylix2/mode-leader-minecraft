package fr.universalserverloader.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ConfigManager {
    public static final String DEFAULT_JSON = "{\n" +
            "  \"serverJar\": \"server.jar\",\n" +
            "  \"platform\": \"paper\",\n" +
            "  \"targetMinecraftVersion\": \"1.21.11\",\n" +
            "  \"minimumJavaVersion\": 21,\n" +
            "  \"minMemory\": \"512M\",\n" +
            "  \"maxMemory\": \"2G\",\n" +
            "  \"agreeToEula\": false,\n" +
            "  \"autoRestart\": false,\n" +
            "  \"scanPlugins\": true,\n" +
            "  \"scanMods\": true,\n" +
            "  \"scanAddons\": true,\n" +
            "  \"maxCrashRestarts\": 3,\n" +
            "  \"restartBackoffSeconds\": 10,\n" +
            "  \"javaArguments\": [],\n" +
            "  \"serverArguments\": []\n" +
            "}\n";

    public LoaderConfig load(Path file) throws IOException {
        if (!Files.exists(file)) {
            Files.createDirectories(file.getParent());
            Files.write(file, DEFAULT_JSON.getBytes(StandardCharsets.UTF_8));
        }
        String json = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        if (!json.trim().startsWith("{") || !json.trim().endsWith("}"))
            throw new IOException("loader.json n'est pas un objet JSON valide");
        LoaderConfig c = new LoaderConfig();
        c.serverJar = string(json, "serverJar", c.serverJar);
        c.platform = string(json, "platform", c.platform);
        c.targetMinecraftVersion = string(json, "targetMinecraftVersion", c.targetMinecraftVersion);
        c.minimumJavaVersion = integer(json, "minimumJavaVersion", c.minimumJavaVersion, 8, 99);
        c.minMemory = memory(string(json, "minMemory", c.minMemory), "minMemory");
        c.maxMemory = memory(string(json, "maxMemory", c.maxMemory), "maxMemory");
        c.agreeToEula = bool(json, "agreeToEula", c.agreeToEula);
        c.autoRestart = bool(json, "autoRestart", c.autoRestart);
        c.scanPlugins = bool(json, "scanPlugins", c.scanPlugins);
        c.scanMods = bool(json, "scanMods", c.scanMods);
        c.scanAddons = bool(json, "scanAddons", c.scanAddons);
        c.maxCrashRestarts = integer(json, "maxCrashRestarts", c.maxCrashRestarts, 0, 20);
        c.restartBackoffSeconds = integer(json, "restartBackoffSeconds", c.restartBackoffSeconds, 1, 300);
        c.javaArguments = array(json, "javaArguments");
        c.serverArguments = array(json, "serverArguments");
        return c;
    }

    public void setEulaConsent(Path file, boolean agreed) throws IOException {
        String json = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        Matcher matcher = Pattern.compile("(\\\"agreeToEula\\\"\\s*:\\s*)(true|false)", Pattern.CASE_INSENSITIVE).matcher(json);
        if (!matcher.find()) throw new IOException("Champ agreeToEula absent de loader.json");
        boolean current = Boolean.parseBoolean(matcher.group(2));
        if (current == agreed) return;
        backup(file);
        String updated = matcher.replaceFirst("$1" + Boolean.toString(agreed));
        Files.write(file, updated.getBytes(StandardCharsets.UTF_8));
    }

    public void setMemoryGib(Path file, int gib) throws IOException {
        if (gib < 1 || gib > 64) throw new IOException("La mémoire doit être comprise entre 1 et 64 Gio");
        String json = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        Pattern minPattern = Pattern.compile("(\\\"minMemory\\\"\\s*:\\s*)\\\"[^\\\"]+\\\"");
        Pattern maxPattern = Pattern.compile("(\\\"maxMemory\\\"\\s*:\\s*)\\\"[^\\\"]+\\\"");
        if (!minPattern.matcher(json).find() || !maxPattern.matcher(json).find())
            throw new IOException("Champs minMemory/maxMemory absents de loader.json");
        String minimum = "512M";
        String maximum = gib + "G";
        backup(file);
        String updated = minPattern.matcher(json).replaceFirst("$1\\\"" + minimum + "\\\"");
        updated = maxPattern.matcher(updated).replaceFirst("$1\\\"" + maximum + "\\\"");
        Files.write(file, updated.getBytes(StandardCharsets.UTF_8));
    }

    private static void backup(Path file) throws IOException {
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss-SSS").format(new Date());
        Path backup = file.resolveSibling("loader.json.backup-" + stamp);
        int suffix = 1;
        while (Files.exists(backup)) backup = file.resolveSibling("loader.json.backup-" + stamp + "-" + suffix++);
        Files.copy(file, backup, StandardCopyOption.COPY_ATTRIBUTES);
    }

    private static String string(String json, String key, String fallback) {
        Matcher m = Pattern.compile("\\\"" + key + "\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"").matcher(json);
        return m.find() ? unescape(m.group(1)) : fallback;
    }

    private static boolean bool(String json, String key, boolean fallback) {
        Matcher m = Pattern.compile("\\\"" + key + "\\\"\\s*:\\s*(true|false)", Pattern.CASE_INSENSITIVE).matcher(json);
        return m.find() ? Boolean.parseBoolean(m.group(1)) : fallback;
    }

    private static int integer(String json, String key, int fallback, int minimum, int maximum) throws IOException {
        Matcher m = Pattern.compile("\\\"" + key + "\\\"\\s*:\\s*(-?[0-9]+)").matcher(json);
        if (!m.find()) return fallback;
        int value;
        try { value = Integer.parseInt(m.group(1)); }
        catch (NumberFormatException e) { throw new IOException("Entier invalide pour " + key); }
        if (value < minimum || value > maximum)
            throw new IOException(key + " doit être compris entre " + minimum + " et " + maximum);
        return value;
    }

    private static List<String> array(String json, String key) {
        List<String> result = new ArrayList<String>();
        Matcher outer = Pattern.compile("\\\"" + key + "\\\"\\s*:\\s*\\[(.*?)\\]", Pattern.DOTALL).matcher(json);
        if (!outer.find()) return result;
        Matcher items = Pattern.compile("\\\"((?:\\\\.|[^\\\"])*)\\\"").matcher(outer.group(1));
        while (items.find()) result.add(unescape(items.group(1)));
        return result;
    }

    private static String unescape(String value) {
        return value.replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private static String memory(String value, String key) throws IOException {
        if (!value.matches("(?i)[1-9][0-9]*[KMG]"))
            throw new IOException("Valeur " + key + " invalide: " + value + " (exemple attendu: 1G)");
        return value.toUpperCase();
    }
}
