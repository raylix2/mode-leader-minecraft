# Architecture multi-modloaders

## Le problème

Un serveur Minecraft n'exécute qu'un seul « monde d'exécution » à la fois :

- **Paper / Spigot / Bukkit** chargent des **plugins** (API Bukkit, dossier `plugins/`) ;
- **Fabric, Forge, NeoForge** chargent des **mods** (dossier `mods/`, descripteurs
  `fabric.mod.json` / `mods.toml` / `neoforge.mods.toml`) ;
- les **hybrides** (Mohist, Arclight, CatServer, Magma, Crucible, Cardboard)
  sont des forks qui injectent l'API Bukkit dans un serveur à mods : ils
  chargent plugins **et** mods, au prix d'une stabilité moindre.

Aucun mélange n'est possible « gratuitement » : c'est une limite de Minecraft,
pas du loader. Le message « Paper ne charge pas directement les mods
Forge/Fabric » ne peut pas disparaître sur Paper — en revanche le loader sait
désormais :

1. **détecter** la plateforme réelle du server.jar ;
2. **installer** plugins et mods exactement là où la plateforme les cherche ;
3. **annoncer** ce qui sera chargé ou bloqué, avant même le démarrage ;
4. vous **guider** vers le bon serveur quand un mod ne peut pas tourner.

## Détection de plateforme

`PlatformDetector` ouvre le server.jar en zip et cherche des marqueurs sans
ambiguïté :

| Marqueur dans le jar | Plateforme |
|---|---|
| `io/papermc/`, `paperclip/`, `META-INF/versions.list` | Paper (launcher « paperclip ») |
| `org/bukkit/` seul | Spigot/CraftBukkit |
| `net/fabricmc/` | Fabric |
| `META-INF/neoforge.mods.toml`, `net/neoforged/` | NeoForge |
| `META-INF/mods.toml`, `net/minecraftforge/` | Forge |
| Bukkit + Forge/NeoForge | Hybride Bukkit+Forge |
| Bukkit + Fabric | Hybride Bukkit+Fabric |

La config `platform` accepte `auto` (défaut : le contenu du jar fait foi),
ou une valeur explicite (`paper`, `spigot`, `bukkit`, `fabric`, `forge`,
`neoforge`, `hybrid-forge`, `hybrid-fabric`). En cas de contradiction, le
contenu du jar gagne et un avertissement est affiché.

## Installation automatique (staging)

Les dossiers utiles réels sont `plugins/` et `mods/`. Le loader y installe :

- `plugins/{bukkit,spigot,paper}/*.jar` → `plugins/` (vérifiés : il faut un
  `plugin.yml` réel, un mod déposé là est refusé avec l'explication) ;
- `mods/{fabric,forge,neoforge}/*.jar` → `mods/`, **seulement** pour le loader
  de la plateforme courante, et seulement si le descripteur du JAR correspond ;
- au changement de plateforme, les copies installées par le loader mais non
  chargeables sont **retirées** de `mods/`, jamais vos fichiers d'origine des
  boîtes de réception.

Les copies créées sont tracées dans `runtime/staged-mods.txt`. Un fichier déjà
présent dans `mods/` avant le loader n'est jamais touché ni supprimé.

## Plan de chargement

`LoadPlan` produit une ligne par extension : `OUI/NOM`, chemin, mode de
chargement ou raison du blocage. Exemple réel sur ce projet (Paper 1.21.11) :

```text
[BLOQUÉ] mods/fabric/lithium-fabric-0.21.4+mc1.21.11.jar — Mod Fabric — nécessite un serveur Fabric ou un hybride (Mohist, Arclight, CatServer, Magma) : Paper ne chargera jamais ce mod.
[CHARGÉ] plugins/LuckPerms-Bukkit-5.5.71.jar — Plugin Paper — chargé depuis plugins/
```

Le plan est affiché par `scan`/`start`, écrit dans `runtime/load-plan.txt`,
lu par la GUI (section MONITORING) et consultable en jeu avec `/plm`.

## Pour faire tourner vos mods Forge et Fabric

Trois options, de la plus simple à la plus fidèle à votre demande « un serveur
qui prend tout » :

1. **Un serveur hybride** (Mohist, Arclight, CatServer…) : renommez son jar en
   `server.jar` à la place de Paper. Le loader détecte l'hybride, installe vos
   plugins dans `plugins/` et vos mods Forge dans `mods/` — tout tourne dans un
   seul processus.
2. **Deux serveurs** : Paper pour les plugins (port 25565) et un serveur
   Fabric/Forge séparé (port différent) dont le jar est pointé par
   `config/loader.json`. Le loader gère chaque dossier indépendamment.
3. **Rester sur Paper** : vos mods restent listés avec la raison exacte de leur
   blocage, sans crash ni fausse promesse.

Le loader ne télécharge jamais un serveur tout seul : vous restez maître du jar
exécuté (aucune redistribution de Minecraft, conformité Mojang).
