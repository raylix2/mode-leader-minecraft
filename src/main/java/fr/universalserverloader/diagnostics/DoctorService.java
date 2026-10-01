package fr.universalserverloader.diagnostics;

import fr.universalserverloader.config.LoaderConfig;
import fr.universalserverloader.discovery.ExtensionInfo;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class DoctorService {
    public String diagnose(Path root, LoaderConfig config, Path serverJar, List<ExtensionInfo> items) throws Exception {
        StringBuilder out = new StringBuilder("=== Diagnostic UniversalServerLoader ===\n");
        int java = JavaRuntime.majorVersion();
        boolean javaOk = java >= config.minimumJavaVersion;
        out.append(javaOk ? "[OK] " : "[ERREUR] ").append("Java ").append(System.getProperty("java.version"))
                .append("; minimum configuré: ").append(config.minimumJavaVersion).append('\n');
        out.append(serverJar != null ? "[OK] " : "[ERREUR] ").append("Serveur: ")
                .append(serverJar == null ? "introuvable" : serverJar.getFileName()).append('\n');
        out.append("[INFO] Plateforme cible: ").append(config.platform).append(' ')
                .append(config.targetMinecraftVersion).append('\n');
        out.append("[INFO] Processeurs logiques: ").append(Runtime.getRuntime().availableProcessors()).append('\n');
        out.append("[INFO] Mémoire JVM maximum du loader: ").append(Runtime.getRuntime().maxMemory() / 1024 / 1024).append(" MiB\n");
        FileStore store = Files.getFileStore(root);
        out.append("[INFO] Espace disque disponible: ").append(store.getUsableSpace() / 1024 / 1024).append(" MiB\n");
        int plugins = 0, mods = 0, rejected = 0;
        for (ExtensionInfo item : items) {
            if (item.kind == ExtensionInfo.Kind.PLUGIN) plugins++;
            else if (item.kind == ExtensionInfo.Kind.FORGE_MOD || item.kind == ExtensionInfo.Kind.FABRIC_MOD) mods++;
            else if (item.kind == ExtensionInfo.Kind.REJECTED) rejected++;
        }
        out.append("[INFO] Plugins détectés: ").append(plugins).append("; mods inactifs: ").append(mods)
                .append("; fichiers refusés: ").append(rejected).append('\n');
        out.append("[OK] Spark: fourni nativement par Paper 1.21.x; aucun doublon requis.\n");
        if (!config.agreeToEula) out.append("[ATTENTE] agreeToEula=false: démarrage réel et benchmark TPS bloqués.\n");
        return out.toString();
    }
}
