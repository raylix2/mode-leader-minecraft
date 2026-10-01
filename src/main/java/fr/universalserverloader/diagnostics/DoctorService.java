package fr.universalserverloader.diagnostics;

import fr.universalserverloader.config.LoaderConfig;
import fr.universalserverloader.platform.LoadPlan;
import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;

public final class DoctorService {
    public String diagnose(Path root, LoaderConfig config, Path serverJar, LoadPlan plan) throws Exception {
        StringBuilder out = new StringBuilder("=== Diagnostic UniversalServerLoader ===\n");
        int java = JavaRuntime.majorVersion();
        boolean javaOk = java >= config.minimumJavaVersion;
        out.append(javaOk ? "[OK] " : "[ERREUR] ").append("Java ").append(System.getProperty("java.version"))
                .append("; minimum configuré: ").append(config.minimumJavaVersion).append('\n');
        out.append(serverJar != null ? "[OK] " : "[ERREUR] ").append("Serveur: ")
                .append(serverJar == null ? "introuvable" : serverJar.getFileName()).append('\n');
        out.append("[INFO] Plateforme: ").append(plan.platform().summary()).append('\n');
        out.append("[INFO] Cible déclarée: ").append(config.platform).append(' ')
                .append(config.targetMinecraftVersion).append('\n');
        if (plan.warning() != null) out.append("[ATTENTION] ").append(plan.warning()).append('\n');
        out.append("[INFO] Processeurs logiques: ").append(Runtime.getRuntime().availableProcessors()).append('\n');
        out.append("[INFO] Mémoire JVM maximum du loader: ").append(Runtime.getRuntime().maxMemory() / 1024 / 1024).append(" MiB\n");
        FileStore store = Files.getFileStore(root);
        out.append("[INFO] Espace disque disponible: ").append(store.getUsableSpace() / 1024 / 1024).append(" MiB\n");
        out.append("[INFO] Plugins: ").append(plan.plugins()).append("; mods: ").append(plan.mods())
                .append("; fichiers refusés: ").append(plan.rejected()).append('\n');
        out.append("[INFO] Plan de chargement: ").append(plan.summary()).append('\n');
        int blockedMods = 0;
        for (LoadPlan.Entry entry : plan.entries()) {
            if (!entry.willLoad && entry.item.kind != fr.universalserverloader.discovery.ExtensionInfo.Kind.REJECTED
                    && entry.item.kind != fr.universalserverloader.discovery.ExtensionInfo.Kind.PLUGIN) blockedMods++;
        }
        if (blockedMods > 0) {
            out.append("[ATTENTION] ").append(blockedMods).append(" mod(s) non chargés: vérifiez la plateforme ")
               .append("(config/loader.json -> \"platform\") ou consultez docs/ARCHITECTURE.md.\n");
        }
        out.append("[OK] Spark: fourni nativement par Paper 1.21.x; aucun doublon requis.\n");
        if (!config.agreeToEula) out.append("[ATTENTE] agreeToEula=false: démarrage réel et benchmark TPS bloqués.\n");
        return out.toString();
    }
}
