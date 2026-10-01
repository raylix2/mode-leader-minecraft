# Historique des versions

## 0.4.0 — 2026-10-01

- détection automatique de la plateforme serveur depuis le contenu du server.jar
  (Paper, Spigot, Bukkit, Fabric, Forge, NeoForge, hybrides Mohist/Arclight/CatServer) ;
- installation automatique des plugins de plugins/{bukkit,spigot,paper} vers plugins/
  et des mods de mods/{fabric,forge,neoforge} vers mods/, selon la plateforme détectée ;
- plan de chargement complet : statut réel de chaque extension, mode de chargement
  ou raison exacte du blocage (console, runtime/load-plan.txt, /plm, interface) ;
- commande /plm réécrite : plateforme détectée, mode de chargement par plugin et par mod ;
- interface : plateforme, plugins présents et mods chargés/bloqués affichés dynamiquement ;
- audit complet (docs/AUDIT.md) : durcissement du chargement des addons (identifiants
  uniques, échecs isolés), scan de plugins/ et mods/ racine corrigé, classification
  des JAR par contenu réel, validation mémoire croisée, lecture console robuste,
  fermeture GUI fiabilisée, chemins normalisés ;
- documentation : docs/FONCTIONNEMENT.md, docs/ARCHITECTURE.md, docs/AUDIT.md ;
- tests : 48 assertions (détection de plateforme, inspection JAR, staging, plan).

## 0.3.8 — 2026-10-01

- interface graphique sombre et responsive ;
- console en direct et commandes start/stop/restart ;
- métriques RAM et CPU propres au processus Paper ;
- réglage mémoire dynamique avec `Xms512M` ;
- affichage et copie de l’adresse locale du serveur ;
- suppression des commandes automatiques envoyées à Paper ;
- scan d’extensions optimisé ;
- commande Paper `/plm` ;
- protections EULA, verrou d’instance et arrêt propre ;
- documentation et paquet de distribution public.

## 0.1.0

- premier prototype du launcher, de la configuration et de l’API UniversalAddon.
