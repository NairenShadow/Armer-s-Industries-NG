# ShadowState-NG

**Plugins open source pour faire tourner un serveur NationsGlory complet**, sans les plugins privés du serveur officiel.
Tu ajoutes les `.jar` dans le dossier `plugins/` de ton serveur NG de base (MCPC+ 1.6.4 avec les mods NationsGlory), tu redémarres, et ça marche : aucune configuration obligatoire.

Un projet **Armer's Industries** (Armerdu59), partie *Serveur Minecraft* du **Projet Shadow State**.

> Licence : libre d'utilisation, de modification et de partage. **Une seule règle : préviens-moi** quand tu l'utilises (voir [LICENCE.md](LICENCE.md)).

---

## Les plugins

| Plugin | À quoi il sert | Obligatoire ? |
|---|---|---|
| **SSNG-Core** | Boîte à outils commune : réseau du mod NationsGUI, marqueurs, zones et colis NG, kits au format NG. | Oui (les autres en ont besoin) |
| **SSNG-Ponts** (+ les `SSNG-Pont-*.jar`) | Remplace les plugins privés (NationsGUI, NationsUtils, NationsBattle, ServerBridge…) que les mods appellent. Sans eux : machines, missiles, armes, marché… ne marchent pas. | Fortement conseillé |
| **SSNG-Ressources** | Gisements de **pétrole** et de **gaz** par continent ou région (`/gisement`). Sans réglage : gisement normal partout. | Non |
| **SSNG-Carte** | Zones / territoires (comme les pays NG) affichés sur la **carte NG**, gestion des chunks depuis la carte, règles par zone, commandes `/f`. | Non (requis par SSNG-Machines) |
| **SSNG-Assaut** | L'**assaut** NG : HUD et écran de résultat du mod, 6 phases, zone de capture 10×10 au hasard, **colis** NG, marqueurs. Arène réglée en jeu. **Nations d'évènement** et assaut grandeur nature. | Non |
| **SSNG-Iles** | Les **îles** NG (interface Îles du mod : création, visite, membres, permissions, sauvegardes) dans un monde dédié. | Non |
| **SSNG-Machines** | Pétrolier, extracteur de gaz, réservoirs, silos, **ateliers** (recettes), **entreprises** et marchés, `/recharge`, vitrine des machines. | Non |

## Installation

1. Télécharge les `.jar` de la dernière version (onglet **Releases** de ce dépôt).
2. Copie-les dans `plugins/` de ton serveur NG. Pour les ponts : `SSNG-Ponts.jar` **et** tous les `SSNG-Pont-*.jar`.
3. Redémarre. C'est tout.

Ne mets **pas** d'autres plugins qui portent les mêmes noms que les ponts (NationsGUI, NationsBattle…).

## Commandes principales

| Commande | Qui | Rôle |
|---|---|---|
| `/ssng` | tous | état du pack |
| `/ponts journal` | admin | ce que les mods demandent au serveur |
| `/gisement ici` · `creer <id> <pétrole> <gaz>` · `pos1/pos2 <id>` | admin | gisements par continent / région (1 = normal, 0 = rien) |
| `/zone …` · `/f claim` · `/f map` | admin | zones et territoires (aussi depuis la carte NG) |
| `/assaut rejoindre` · `quitter` · `info` | joueurs | assaut automatique dès qu'il y a assez d'inscrits |
| `/assautadmin monde` · `champ pos1/pos2` · `attaquants ajouter` · `kit attaquants` · `regle` | admin | régler l'arène d'assaut en jeu |
| `/nation creer <id> <chef>` · `territoire <id> pos1/pos2` · `assaut <attaquant> <défenseur>` | haut staff | assauts entre nations, avec mur invisible |
| `/i` (ou `/ile`) | joueurs | interface des îles |
| `/machines vitrine` · `/atelier` · `/entreprise` · `/recharge` | admin / testeurs | machines, recettes, entreprises |

## Permissions

| Permission | Par défaut | Donne |
|---|---|---|
| `ssng.admin` | op | tout le pack |
| `ssng.hautstaff` | op | nations, assauts de nations |
| `ssng.staff` | op | outils du staff |
| `ssng.iles.creer` | tout le monde | créer une île |
| `ssng.iles.worldedit` | op | WorldEdit sur son île |
| `ssng.test` | op | vitrine des machines, `/recharge` |
| `ssng.grade.<nom>` | — | grade pour les droits de construction par chunk (SSNG-Carte) |

## Compiler soi-même

Il faut un JDK 9 ou plus récent et le `server.jar` (MCPC+ 1.6.4) de ton serveur NG. Pour SSNG-Iles, mets aussi `worldedit-5.6.jar` dans `libs/`.

```
./build.sh /chemin/vers/server.jar                                   (Linux / macOS)
powershell -ExecutionPolicy Bypass -File build.ps1 "C:\...\server.jar"   (Windows)
```

Les `.jar` arrivent dans `dist/`.

## Avertissement

Projet de fans, **non affilié à NationsGlory**. « NationsGlory » et ses mods appartiennent à leurs auteurs ; ce dépôt ne contient aucun code de NationsGlory, seulement des plugins qui répondent aux mêmes demandes que les plugins privés du serveur officiel.

## Contact

Tu utilises ce code ? **Ouvre une Issue** « J'utilise ShadowState-NG » (nom du projet ou du serveur, lien si tu en as un), ou écris à **Armerdu59** sur Discord. Les gros projets m'intéressent : j'aime voir ce que ça devient.
