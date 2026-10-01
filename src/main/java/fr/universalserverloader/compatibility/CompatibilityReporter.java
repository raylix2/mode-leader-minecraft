package fr.universalserverloader.compatibility;

import fr.universalserverloader.discovery.ExtensionInfo;
import java.nio.file.Path;
import java.util.List;

public final class CompatibilityReporter {
    public String create(Path root, List<ExtensionInfo> items) {
        StringBuilder out = new StringBuilder();
        out.append("=== Rapport de compatibilité ===\n");
        if (items.isEmpty()) out.append("Aucune extension détectée.\n");
        for (ExtensionInfo item : items) {
            out.append("[").append(item.kind).append("] ")
               .append(root.relativize(item.path)).append(" - ").append(item.message).append('\n');
        }
        out.append("Note: les sous-dossiers plugins/* sont organisationnels; selon le serveur, placez les plugins dans plugins/.\n");
        out.append("Forge/Fabric ne sont jamais injectés dans un serveur Bukkit par ce prototype.\n");
        return out.toString();
    }
}
