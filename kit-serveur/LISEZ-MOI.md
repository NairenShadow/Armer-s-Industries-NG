# Kit serveur NationsGlory (ShadowState-NG)

Monter un serveur NationsGlory complet sur ton PC, pas à pas. Compte 10 à 15 minutes.

Ce kit **ne contient ni les mods NationsGlory ni le `server.jar`** : ils n'appartiennent pas à ce projet. Le script les récupère dans **ton** jeu NationsGlory installé.

## Ce qu'il te faut

1. **Windows** et le **launcher NationsGlory** installé ; lance le jeu une fois pour qu'il télécharge ses mods.
2. **Java 17** : Eclipse Temurin 17 (JDK), sur https://adoptium.net (choisis « 17 - LTS », Windows x64, `.msi`).
3. **Le serveur de base NG (MCPC+ 1.6.4)** : `server.jar`, `mcpc-java17-agent.jar` et le dossier `libraries`. On le trouve dans les guides de la communauté NG (par exemple celui de Croux), pas dans ce dépôt.
4. **Les plugins ShadowState-NG** : les `.jar` de la dernière Release du dépôt.

## Installation

1. **Crée un dossier** pour ton serveur, par exemple `C:\ServeurNG`.
2. **Copie dedans** les fichiers de ce kit : `Demarrer.bat`, `Installer-mods-NG.bat`, `Sauvegarder.bat`, `mods-client-a-exclure.txt`, `parametres.txt`, `server.properties`.
3. **Copie aussi** les fichiers du serveur de base : `server.jar`, `mcpc-java17-agent.jar`, `libraries`.
4. **Crée un dossier `plugins`** et mets-y les `.jar` ShadowState-NG :
   - `SSNG-Core.jar` (obligatoire) ;
   - `SSNG-Ponts.jar` **et tous** les `SSNG-Pont-*.jar` (conseillé : sans eux, les machines et les missiles ne marchent pas) ;
   - ceux que tu veux parmi `SSNG-Ressources`, `SSNG-Carte`, `SSNG-Assaut`, `SSNG-Iles`, `SSNG-Machines` (`SSNG-Machines` a besoin de `SSNG-Carte`).
   - Conseillé : **WorldEdit 5.6** et **Essentials** pour 1.6.4.
5. **Ferme le jeu NationsGlory**, puis double-clique **`Installer-mods-NG.bat`**. Il copie les mods et la config du jeu et retire ceux qui sont réservés au PC (liste `mods-client-a-exclure.txt`).
6. Double-clique **`Demarrer.bat`**. Le premier démarrage prend 1 à 3 minutes. Le serveur est prêt quand la console affiche `[NGUpgrades] Available plugins`.
7. Dans le launcher NG, rejoins `localhost` pour tester.

## Pour aller plus loin

- **Te donner les droits admin** : tape `op TonPseudo` dans la console.
- **Réglages** :
  - `parametres.txt` : mémoire, Java, redémarrage automatique, sauvegardes ;
  - `server.properties` : nom du serveur, port, nombre de joueurs.
- **Après une mise à jour de NationsGlory** : lance le jeu une fois, ferme-le, puis relance `Installer-mods-NG.bat`.
- **Sauvegardes** : une copie du monde est faite à chaque démarrage dans `sauvegardes` (les 10 dernières sont gardées). `Sauvegarder.bat` en fait une à la main.
- **Ouvrir aux autres joueurs** : ouvre le port 25565 sur ta box, ou passe par un hébergeur compatible MCPC+ 1.6.4.

## En cas de problème

| Message | Solution |
|---|---|
| « Le chemin d'accès spécifié est introuvable » ou « Aucun Java trouvé » | Installe Java 17 (étape 2), ou indique son chemin dans `parametres.txt` (`JAVA=...`). |
| « Les mods NationsGlory ne sont pas installés » | Lance `Installer-mods-NG.bat` (étape 5). |
| Le serveur s'arrête tout de suite avec une erreur de mod | Un mod réservé au PC est resté : ajoute son nom dans `mods-client-a-exclure.txt` et relance l'installation. |
| Les machines ou les missiles ne font rien | Vérifie que `SSNG-Ponts.jar` **et tous** les `SSNG-Pont-*.jar` sont dans `plugins`. |

La liste des mods à exclure vient du guide communautaire de **Croux**. Merci à lui.
