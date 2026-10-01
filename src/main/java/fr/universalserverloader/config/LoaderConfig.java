package fr.universalserverloader.config;

import java.util.ArrayList;
import java.util.List;

public final class LoaderConfig {
    public String serverJar = "server.jar";
    public String platform = "paper";
    public String targetMinecraftVersion = "1.21.11";
    public int minimumJavaVersion = 21;
    public String minMemory = "512M";
    public String maxMemory = "2G";
    public boolean agreeToEula = false;
    public boolean autoRestart = false;
    public boolean scanPlugins = true;
    public boolean scanMods = true;
    public boolean scanAddons = true;
    public int maxCrashRestarts = 3;
    public int restartBackoffSeconds = 10;
    public List<String> javaArguments = new ArrayList<String>();
    public List<String> serverArguments = new ArrayList<String>();
}
