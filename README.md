# UniversalServerLoader

UniversalServerLoader est un lanceur et superviseur local pour serveurs Minecraft Java. Il fournit une interface sombre, une console en direct, la gestion propre du processus Paper, le réglage de la mémoire, des métriques propres au serveur et un inventaire clair des extensions.

La version actuelle cible **Paper 1.21.11 avec Java 21**. Le loader ne modifie pas Minecraft et ne prétend pas rendre Paper, Forge et Fabric compatibles entre eux.

## Fonctionnalités

- démarrage d’un `server.jar` existant avec `ProcessBuilder` ;
- **détection automatique de la plateforme** (Paper, Spigot, Bukkit, Fabric, Forge, NeoForge, hybrides Mohist/Arclight/CatServer) depuis le contenu du server.jar ;
- **installation automatique** des plugins déposés dans `plugins/{bukkit,spigot,paper}` et des mods de `mods/{fabric,forge,neoforge}` vers les dossiers réellement lus par le serveur ;
- **plan de chargement** : ce qui sera chargé, ce qui est bloqué et pourquoi (`scan`, `runtime/load-plan.txt`, commande `/plm` en jeu) ;
- interface graphique avec console, commandes, état et uptime ;
- boutons démarrer, arrêter et redémarrer ;
- limites mémoire configurables (`Xms` 512 Mio, `Xmx` réglable) ;
- métriques RAM et CPU du processus Paper uniquement ;
- journal du loader dans `logs/loader-latest.log` ;
- arrêt propre avec la commande Minecraft `stop` ;
- verrou empêchant deux instances du serveur ;
- création automatique de l’arborescence au premier lancement ;
- rapport de compatibilité des plugins, addons et mods ;
- API légère `UniversalAddon` ;
- plugin Paper optionnel `UniversalPLM` avec la commande `/plm` ;
- scripts Windows et Linux/macOS.

## Ce que le projet ne fait pas

- il ne distribue ni Minecraft ni Paper ;
- il ne télécharge pas automatiquement de serveur ;
- il ne transforme pas Paper en serveur à mods : Paper ne peut pas exécuter des mods Forge/Fabric (limite de Minecraft) ; le loader détecte ce cas, l’explique et vous oriente vers un serveur Fabric/Forge ou un hybride (voir [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)) ;
- il ne fusionne pas plusieurs mod loaders dans un même processus ;
- il ne garantit pas l’absence totale de crash ou de lag.

Pour faire tourner plugins **et** mods dans un seul processus, placez un serveur hybride (Mohist, Arclight, CatServer…) sous le nom `server.jar` : le loader détecte la plateforme et installe plugins et mods automatiquement.

## Installation rapide

1. Téléchargez l’archive de la dernière release.
2. Extrayez-la dans un dossier vide.
3. Placez votre serveur Paper dans `UniversalServer/server.jar`.
4. Installez Java 21 et vérifiez que la commande `java -version` fonctionne.
5. Lancez `LANCER-SERVEUR.bat` sous Windows ou `UniversalServer/start.sh` sous Linux/macOS.
6. Lisez l’[EULA Minecraft](https://aka.ms/MinecraftEULA), puis activez l’option dans l’interface uniquement si vous l’acceptez.
7. Cliquez sur **Démarrer**.

Paper génère automatiquement `server.properties`, les mondes et ses autres fichiers lors du premier démarrage.

## Arborescence de la release

```text
UniversalServerLoader/
├── LANCER-SERVEUR.bat
└── UniversalServer/
    ├── universal-loader.jar
    ├── start.bat
    ├── start-console.bat
    ├── start.sh
    ├── config/
    │   └── loader.json
    ├── plugins/
    ├── mods/
    │   ├── forge/
    │   └── fabric/
    ├── addons/
    └── logs/
```

Le fichier `server.jar` n’est volontairement pas inclus.

## Configuration

Le fichier `UniversalServer/config/loader.json` est créé avec une configuration prudente :

```json
{
  "serverJar": "server.jar",
  "platform": "auto",
  "targetMinecraftVersion": "1.21.11",
  "minimumJavaVersion": 21,
  "minMemory": "512M",
  "maxMemory": "2G",
  "agreeToEula": false,
  "autoRestart": false,
  "scanPlugins": true,
  "scanMods": true,
  "scanAddons": true,
  "installStagedPlugins": true,
  "stageMods": true,
  "maxCrashRestarts": 3,
  "restartBackoffSeconds": 10,
  "javaArguments": [],
  "serverArguments": ["--nogui"]
}
```

`platform` accepte `auto` (défaut : détecté depuis le contenu du server.jar) ou une valeur explicite : `paper`, `spigot`, `bukkit`, `fabric`, `forge`, `neoforge`, `hybrid-forge`, `hybrid-fabric`.

`agreeToEula` reste toujours désactivé par défaut. Le programme ne l’active qu’après une confirmation explicite dans l’interface.

## Commandes

```powershell
java -jar universal-loader.jar gui
java -jar universal-loader.jar start
java -jar universal-loader.jar stop
java -jar universal-loader.jar restart
java -jar universal-loader.jar status
java -jar universal-loader.jar scan
java -jar universal-loader.jar doctor
java -jar universal-loader.jar benchmark
java -jar universal-loader.jar help
java -jar universal-loader.jar version
```

Dans Minecraft, `/plm` affiche la plateforme détectée, les plugins actifs et, pour chaque mod, son **mode de chargement** ou la raison exacte de son blocage.

## Plugins et mods

Le loader installe lui-même les extensions au bon endroit, d’après le contenu réel de chaque JAR (et pas seulement son dossier) :

```text
UniversalServer/plugins/                <- lu par Paper, Spigot, Bukkit (et les hybrides)
UniversalServer/plugins/{bukkit,spigot,paper}/   <- boîtes de réception, copiés vers plugins/
UniversalServer/mods/                   <- lu par Fabric, Forge, NeoForge (et les hybrides)
UniversalServer/mods/{fabric,forge,neoforge}/    <- boîtes de réception, copiés vers mods/
UniversalServer/addons/                 <- addons UniversalAddon du loader
```

Un JAR déposé au mauvais endroit n’est jamais exécuté aveuglément : le plan de chargement (`scan`) indique quoi faire. Les copies créées par le loader sont tracées dans `runtime/staged-mods.txt` et retirées automatiquement si vous changez de plateforme ; vos fichiers d’origine ne sont jamais supprimés.

## Documentation

- [docs/FONCTIONNEMENT.md](docs/FONCTIONNEMENT.md) — fonctionnement interne, étape par étape ;
- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — multi-modloaders, détection, options pour vos mods ;
- [docs/AUDIT.md](docs/AUDIT.md) — audit du code et corrections appliquées.

## Compilation

Sous Windows avec un JDK 21 :

```powershell
powershell -ExecutionPolicy Bypass -File .\build.ps1
powershell -ExecutionPolicy Bypass -File .\test.ps1
```

Le loader reste compilé pour Java 8 afin de conserver la compatibilité de l’API et des outils historiques. Le profil Paper 1.21.11 exige toutefois Java 21. Le plugin `UniversalPLM` est compilé lorsque les bibliothèques Paper locales sont disponibles.

## Sécurité et stabilité

- aucun shell concaténé pour lancer le serveur ;
- aucun téléchargement silencieux ;
- aucune suppression récursive des données utilisateur ;
- sauvegarde avant modification de la configuration ;
- EULA désactivée par défaut ;
- arrêt propre du processus enfant ;
- métriques lues sans envoyer de commandes périodiques à Paper.

## Licence

Le code d’UniversalServerLoader est distribué sous licence [MIT](LICENSE). Minecraft, Paper et les extensions tierces conservent leurs licences respectives et ne sont pas inclus dans le dépôt.
