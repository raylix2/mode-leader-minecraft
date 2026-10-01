# Comment fonctionne UniversalServerLoader

Ce document explique le fonctionnement interne du projet, fichier par fichier
et étape par étape. Il complète le README, qui reste le guide d'utilisation.

## Vue d'ensemble

UniversalServerLoader est un **lanceur de serveurs Minecraft** qui se place
entre vous et le serveur (Paper, Spigot, Bukkit, Fabric, Forge, NeoForge ou un
hybride). Il ne modifie ni Minecraft ni le serveur : il prépare le dossier,
vérifie l'environnement, installe les extensions au bon endroit, lance le bon
processus Java et le supervise jusqu'à l'arrêt.

Deux façons de l'utiliser :

- **Interface graphique** (`java -jar universal-loader.jar gui` ou
  `LANCER-SERVEUR.bat`) : console en direct, métriques RAM/CPU, réglage mémoire ;
- **Ligne de commande** (`start`, `stop`, `restart`, `status`, `scan`, `doctor`,
  `benchmark`, `profile`, `health`) : tout ce que fait la GUI, scriptable.

## Cycle de vie d'un démarrage

Quand vous lancez `start` (ou cliquez « Démarrer », ce qui relance le jar avec
`start`), `Main.execute()` enchaîne :

1. **Racine de travail** — `runtimeRoot()` localise le dossier : le dossier qui
   contient le jar, ou `universal.root` en propriété système (utilisé par les tests).
2. **Arborescence** — `WorkspaceInitializer` crée `config/`, `plugins/`
   (+ sous-dossiers boîte de réception), `mods/{forge,fabric,neoforge}`,
   `addons/`, `addons-data/`, `logs/`, `runtime/`.
3. **Configuration** — `ConfigManager` lit `config/loader.json` (créé avec des
   valeurs prudentes au premier lancement ; la mémoire et l'EULA ne sont jamais
   activées sans vous).
4. **Détection de plateforme** — `ServerJarDetector` trouve le jar serveur,
   puis `PlatformDetector` lit son contenu (paperclip, org/bukkit, net/fabricmc,
   mods.toml…) pour dire s'il s'agit de Paper, Fabric, Forge, NeoForge ou d'un
   hybride. C'est le contenu du jar qui fait foi, pas le nom du fichier.
5. **Installation des extensions** — `ExtensionInstaller` :
   - copie les plugins de `plugins/{bukkit,spigot,paper}` vers `plugins/`
     (seul dossier lu par un serveur Bukkit) ;
   - copie les mods du loader correspondant (`mods/fabric`, `mods/forge`,
     `mods/neoforge`) vers `mods/` (seul dossier lu par Fabric/Forge/NeoForge) ;
   - retire les copies de mods devenues inutiles si vous changez de plateforme
     (suivies dans `runtime/staged-mods.txt` ; vos fichiers d'origine ne sont
     jamais supprimés).
6. **Inventaire** — `DiscoveryService` scanne les trois familles. Pour chaque
   JAR, `JarInspector` lit le contenu (`plugin.yml`, `fabric.mod.json`,
   `mods.toml`, `neoforge.mods.toml`, `universal-addon.json`) : le type réel
   prime sur le dossier où le fichier a été déposé.
7. **Plan de chargement** — `LoadPlan` décide, extension par extension :
   chargée (et par quel mécanisme) ou bloquée (et pourquoi, avec l'action à
   faire). Le plan est écrit dans `runtime/load-plan.txt`, que la GUI lit pour
   afficher la plateforme, les plugins et les mods réels.
8. **Contrôles bloquants** — version de Java, EULA (`EulaManager` n'écrit
   `eula=true` que si vous avez accepté), verrou d'instance (`InstanceControl`).
9. **Lancement** — `ServerLauncher` démarre `java -Xms… -Xmx… -jar server.jar`
   via `ProcessBuilder` (jamais de shell), relaie la sortie vers la console et
   `logs/loader-latest.log`, lit vos commandes clavier et surveille
   `runtime/control.command` (ainsi la GUI peut piloter une instance lancée
   ailleurs). `stop` envoie la commande Minecraft `stop` ; `restart` la renvoie
   puis relance ; un crash avec `autoRestart=true` redémarre avec temporisation
   exponentielle, limité à `maxCrashRestarts`.
10. **Addons** — `AddonManager` charge les JAR de `addons/` qui contiennent
    `universal-addon.json`, isole chacun dans son propre `URLClassLoader` et
    enregistre leurs commandes (exécutées avant transmission au serveur).

## Métriques de l'interface graphique

`ServerDashboard` n'envoie **aucune commande** au serveur pour mesurer : il
attache l'agent JMX local au PID écrit dans `runtime/server.pid`
(`com.sun.tools.attach.VirtualMachine` en réflexion) et lit `MemoryMXBean` et
`ProcessCpuLoad`. L'affichage se met à jour toutes les 2 secondes ; la
déconnexion est silencieuse tant que le serveur n'est pas en ligne.

## La commande /plm en jeu

Le plugin `UniversalPLM` (compilé contre les bibliothèques Paper locales du
dossier `UniversalServer/libraries`) ajoute `/plm` : plateforme détectée, liste
des plugins actifs, et pour chaque mod trouvé son **mode de chargement** et la
raison exacte s'il n'est pas chargé. Le plugin lit le contenu des JAR de `mods/`
lui-même : il ne dépend pas du loader et fonctionne même si le serveur a été
lancé à la main.

## Build

- `build.ps1` compile en Java 8 (compatibilité maximale du cœur), produit
  `UniversalServer/universal-loader.jar`, compile l'addon d'exemple et, si les
  bibliothèques Paper sont présentes, le plugin `UniversalPLM`.
- `test.ps1` compile puis exécute `AllTests` (48 assertions, sans dépendance
  externe). La CI GitHub exécute le même script sur chaque push.
