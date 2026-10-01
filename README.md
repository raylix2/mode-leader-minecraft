# UniversalServerLoader

UniversalServerLoader est un lanceur et superviseur local pour serveurs Minecraft Java. Il fournit une interface sombre, une console en direct, la gestion propre du processus Paper, le réglage de la mémoire, des métriques propres au serveur et un inventaire clair des extensions.

La version actuelle cible **Paper 1.21.11 avec Java 21**. Le loader ne modifie pas Minecraft et ne prétend pas rendre Paper, Forge et Fabric compatibles entre eux.

## Fonctionnalités

- démarrage d’un `server.jar` existant avec `ProcessBuilder` ;
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
- il ne charge pas un mod Forge ou Fabric dans Paper ;
- il ne fusionne pas plusieurs mod loaders dans un même processus ;
- il ne garantit pas l’absence totale de crash ou de lag.

Les profils Fabric et Forge doivent utiliser des serveurs séparés avec leurs propres runtimes et dossiers de mods.

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
  "platform": "paper",
  "targetMinecraftVersion": "1.21.11",
  "minimumJavaVersion": 21,
  "minMemory": "512M",
  "maxMemory": "2G",
  "agreeToEula": false,
  "autoRestart": false,
  "scanPlugins": true,
  "scanMods": true,
  "scanAddons": true,
  "maxCrashRestarts": 3,
  "restartBackoffSeconds": 10,
  "javaArguments": [],
  "serverArguments": ["--nogui"]
}
```

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

Dans Minecraft, `/plm` affiche les plugins Paper actifs et inventorie séparément les mods présents. Les mods signalés comme inactifs ne sont jamais injectés dans Paper.

## Plugins et mods

Les plugins Paper doivent être placés directement dans `UniversalServer/plugins/`. Les JAR Forge et Fabric restent dans leurs dossiers respectifs pour un futur profil séparé :

```text
UniversalServer/mods/forge/
UniversalServer/mods/fabric/
```

Un JAR non compatible n’est jamais exécuté aveuglément. Les fichiers qui ne se terminent pas par `.jar` sont refusés comme extensions.

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
