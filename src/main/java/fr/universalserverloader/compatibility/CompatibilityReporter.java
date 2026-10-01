package fr.universalserverloader.compatibility;

import fr.universalserverloader.discovery.ExtensionInfo;
import fr.universalserverloader.platform.LoadPlan;
import fr.universalserverloader.platform.PlatformInfo;
import java.nio.file.Path;
import java.util.List;

/** Rapport de compatibilité : ce qui sera chargé, ce qui ne le sera pas, et pourquoi. */
public final class CompatibilityReporter {
    public String create(Path root, List<ExtensionInfo> items, LoadPlan plan, List<String> actions) {
        PlatformInfo platform = plan.platform();
        StringBuilder out = new StringBuilder();
        out.append("=== Rapport de compatibilité ===\n");
        out.append("Plateforme: ").append(platform.summary()).append('\n');
        if (plan.warning() != null) out.append("Avertissement: ").append(plan.warning()).append('\n');

        for (LoadPlan.Entry entry : plan.entries()) {
            String label = entry.item.kind == ExtensionInfo.Kind.REJECTED ? "[REFUSÉ] "
                    : entry.willLoad ? "[CHARGÉ] " : "[BLOQUÉ] ";
            out.append(label).append(plan.relative(entry.item.path)).append(" — ")
               .append(entry.willLoad ? entry.mode : entry.blocker).append('\n');
        }
        if (items.isEmpty()) out.append("Aucune extension détectée.\n");

        out.append("Résumé: ").append(plan.summary()).append('\n');
        if (!actions.isEmpty()) {
            out.append("Actions d'installation automatique:\n");
            for (String action : actions) out.append("  - ").append(action).append('\n');
        }
        out.append("Note: plugins/, mods/ et addons/ sont les seuls dossiers réellement lus par la plateforme;\n");
        out.append("les sous-dossiers servent de boîtes de réception et sont installés automatiquement.\n");
        return out.toString();
    }
}
