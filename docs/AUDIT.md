# Audit du code — v0.3.8 → v0.4.0

Audit complet du loader (2 500 lignes Java) : sécurité, robustesse, concurrence,
cohérence fonctionnelle. Chaque point liste la constatation, le risque et le
correctif appliqué.

## Corrections appliquées

### 1. Sécurité — RCE par addon malveillant (critique)
`AddonManager` exécutait `Class.forName(main, true, loader).newInstance()` avec
`main` lu dans le JAR : tout JAR déposé dans `addons/` exécutait du code à
l'identité du loader, sans garde-fou sur les doublons ni isolement des échecs.

- deux addons avec le même `id` écrasaient silencieusement commandes et données ;
- un échec dans `onEnable()` laissait un addon à moitié enregistré.

**Corrigé** : identifiants uniques (rejet des doublons), commandes dupliquées
rejetées, échec de `onEnable` capturé avec désactivation propre, échec de
chargement de classe fermant immédiatement le classloader. La confiance d'un
addon reste un choix explicite : déposer un JAR dans `addons/` vaut exécution —
c'est maintenant documenté dans FONCTIONNEMENT.md.

### 2. Bugs — découverte des extensions (majeur)
Trois défauts de la v0.3.8, révélés par les nouveaux tests :

- `DiscoveryService.inspect()` recevait un **dossier** là où du fichier était
  attendu : `plugins/` et `mods/` racine n'étaient **jamais scannés** ;
- un plugin déposé dans `plugins/bukkit|spigot|paper` était « détecté » mais
  **jamais chargé** par Paper (qui ne lit que `plugins/`) — c'est exactement le
  problème visible sur la capture d'écran `/plm` ;
- le classement se faisait uniquement sur le dossier d'accueil : un mod
  déposé dans `plugins/` passait pour un plugin.

**Corrigé** : scan réel de `plugins/` et `mods/` racine, dédoublonnage
nom+taille entre boîtes de réception et cibles, et lecture du contenu du JAR
(`plugin.yml`, `fabric.mod.json`, `mods.toml`, `neoforge.mods.toml`) qui prime
sur le dossier.

### 3. Robustesse — config JSON à moitié corrompue (moyen)
`ConfigManager.load()` exigeait que le fichier commence par `{` et finisse par
`}` puis interprétait chaque clé indépendamment. Un fichier tronqué au milieu
pouvait donc « passer » avec des valeurs par défaut silencieuses pour les clés
perdues, et `minMemory > maxMemory` était accepté (JVM refusée au démarrage).

**Corrigé** : validation croisée `minMemory <= maxMemory` ajoutée dans
`ConfigManager.load()` (l'ancien comportement subsiste pour la tolérance de
fichiers écrits à la main, mais une config incohérente est désormais signalée
au plus tôt). La sauvegarde horodatée avant modification (`backup()`) existedéjà
et est conservée.

### 4. Fiabilité — lecture des commandes (moyen)
`ServerLauncher` sonde `System.in` avec `console.ready()` puis lit une ligne :
sous Windows avec des entrées rapides, une ligne pouvait rester en attente
jusqu'au prochain tick (latence 100 ms) ou être perdue si le flux se ferme
pendant la lecture.

**Corrigé** : boucle de vidage du buffer après `ready()`, et le flux fermé ne
provoque plus de boucle d'erreur silencieuse (sortie propre sur EOF).

### 5. Concurrence — fermeture du dashboard (mineur)
`ServerDashboard.closeWindow()` appelait `dispose()` pendant que le thread de
lecture pouvait encore écrire dans la console ; `releaseGuiLock()` n'était
appelé que sur `windowClosed`, non garanti si la fenêtre était détruite
par le système.

**Corrigé** : ordre d'arrêt strict (moniteur métriques stoppé, connexion JMX
fermée, puis dispose), et verrou GUI libéré dans un `finally` de fermeture.

### 6. Portabilité — chemins Windows dans les journaux (mineur)
Les rapports utilisaient `Path.toString()`, qui affiche `mods\forge\mod.jar`
sous Windows. Le plan écrit dans `runtime/load-plan.txt` mélangeait donc des
séparateurs selon l'OS, compliquant les outils externes.

**Corrigé** : normalisation systématique en séparateurs `/` dans
`LoadPlan.relative()`, `DiscoveryService` et `ExtensionInstaller`.

### 7. Cohérence — version et scripts (mineur)
La GUI codait en dur « Paper 1.21.11 » et la liste des plugins (les mêmes
« Chunky, LuckPerms » quelle que soit l'installation réelle) ; `VERSION`
mélangait plateforme et version (`0.3.8-paper-1.21.11`).

**Corrigé** : la GUI lit `runtime/load-plan.txt` (plateforme détectée, charge
par type, mods chargés/bloqués, plugins réellement présents dans `plugins/`),
`VERSION` est redevenu un numéro simple (`0.4.0`).

## Points relevés, non corrigés (choix assumés)

- **ConfigManager parse du JSON à la main** : suffisant pour le schéma fermé
  du loader (13 clés), aucune dépendance externe à la compilation Java 8.
  À remplacer par une vraie lib JSON si le schéma grandit.
- **`LoadPlan` écrit un fichier texte, pas du JSON** : lisible en jeu via
  `/plm` et par la GUI ; l'architecture est prête pour un format machine si un
  besoin d'intégration apparaît.
- **`InstanceControl.send()` a une course théorique** (le serveur peut s'arrêter
  entre `isRunning()` et l'écriture) : la commande est alors perdue, sans
  corruption ; la fenêtre est de quelques millisecondes.
- **Les mods refusés restent dans le rapport même avec `scanMods=false`** :
  cohérent, car ils ne sont ni déplacés ni exécutés.

## Tests

La suite `AllTests` passe de 29 à **48 assertions**, dont 19 nouvelles :
détection de plateforme (paper/fabric/forge/neoforge/hybride/spigot), lecture
des descripteurs JAR, installation automatique des plugins (idempotence),
staging des mods avec retrait au changement de plateforme, plan de chargement
complet (plugin chargé, mod bloqué, écriture du fichier).
