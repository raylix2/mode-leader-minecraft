package fr.universalserverloader.platform;

/**
 * Décrit la plateforme serveur réellement utilisée : un serveur Bukkit charge
 * des plugins, un serveur Fabric/Forge/NeoForge charge des mods, et certains
 * serveurs hybrides (Mohist, Arclight, CatServer, Magma, Cardboard) font les deux.
 */
public final class PlatformInfo {
    public static final String LOADER_NONE = "";
    public static final String LOADER_FABRIC = "fabric";
    public static final String LOADER_FORGE = "forge";
    public static final String LOADER_NEOFORGE = "neoforge";

    /** Identifiant de plateforme, tel qu'utilisé dans config/loader.json. */
    public final String id;
    /** Libellé affiché à l'écran. */
    public final String label;
    /** true si ce serveur charge des plugins Bukkit/Spigot/Paper. */
    public final boolean bukkitPlugins;
    /** Mod loader géré par ce serveur, LOADER_NONE s'il n'y en a pas. */
    public final String modLoader;
    /** true si la plateforme provient d'une lecture du server.jar. */
    public final boolean detected;

    private PlatformInfo(String id, String label, boolean bukkitPlugins, String modLoader, boolean detected) {
        this.id = id;
        this.label = label;
        this.bukkitPlugins = bukkitPlugins;
        this.modLoader = modLoader;
        this.detected = detected;
    }

    public static PlatformInfo of(String id, boolean detected) {
        String key = id == null ? "" : id.trim().toLowerCase();
        if ("paper".equals(key)) return new PlatformInfo("paper", "Paper", true, LOADER_NONE, detected);
        if ("spigot".equals(key)) return new PlatformInfo("spigot", "Spigot", true, LOADER_NONE, detected);
        if ("bukkit".equals(key)) return new PlatformInfo("bukkit", "Bukkit", true, LOADER_NONE, detected);
        if ("fabric".equals(key)) return new PlatformInfo("fabric", "Fabric", false, LOADER_FABRIC, detected);
        if ("forge".equals(key)) return new PlatformInfo("forge", "Forge", false, LOADER_FORGE, detected);
        if ("neoforge".equals(key)) return new PlatformInfo("neoforge", "NeoForge", false, LOADER_NEOFORGE, detected);
        if ("hybrid-forge".equals(key)) return new PlatformInfo("hybrid-forge", "Hybride Bukkit+Forge", true, LOADER_FORGE, detected);
        if ("hybrid-fabric".equals(key)) return new PlatformInfo("hybrid-fabric", "Hybride Bukkit+Fabric", true, LOADER_FABRIC, detected);
        return unknown(detected);
    }

    public static PlatformInfo unknown(boolean detected) {
        return new PlatformInfo("unknown", "Inconnue", false, LOADER_NONE, detected);
    }

    /** "auto" (ou valeur vide) signifie : détecter la plateforme depuis le server.jar. */
    public static boolean isAuto(String configured) {
        return configured == null || configured.trim().length() == 0 || "auto".equalsIgnoreCase(configured.trim());
    }

    public boolean isUnknown() { return "unknown".equals(id); }

    public boolean hasModLoader() { return modLoader.length() > 0; }

    /** true si ce serveur chargera un mod du loader indiqué ("fabric", "forge", "neoforge" ou "multi"). */
    public boolean loads(String loader) {
        if (loader == null || loader.length() == 0) return false;
        if ("multi".equals(loader)) return hasModLoader();
        if (LOADER_FABRIC.equals(loader)) return LOADER_FABRIC.equals(modLoader);
        if (LOADER_FORGE.equals(loader)) return LOADER_FORGE.equals(modLoader)
                || LOADER_NEOFORGE.equals(modLoader) || "hybrid-forge".equals(id);
        if (LOADER_NEOFORGE.equals(loader)) return LOADER_NEOFORGE.equals(modLoader) || "hybrid-forge".equals(id);
        return false;
    }

    /** Dossier d'origine des mods à installer pour ce loader ("fabric", "forge" ou "neoforge"). */
    public String stagingFolder(String loader) {
        if (LOADER_FABRIC.equals(loader)) return "fabric";
        if (LOADER_NEOFORGE.equals(loader)) return "neoforge";
        return "forge";
    }

    public String summary() {
        return label + " — plugins Bukkit: " + (bukkitPlugins ? "oui" : "non")
                + " — mod loader: " + (hasModLoader() ? modLoader : "aucun")
                + (detected ? " (détecté depuis le server.jar)" : " (déclaré dans config/loader.json)");
    }
}
