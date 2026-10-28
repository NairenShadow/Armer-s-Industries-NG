# Publier ShadowState-NG sur GitHub (pas à pas)

Tout se fait depuis le site GitHub, sans ligne de commande.

## 1. Créer le dépôt

1. Va sur https://github.com et connecte-toi avec ton compte **Armerdu59**.
2. En haut à droite : **+** → **New repository**.
3. Remplis :
   - **Repository name** : `ShadowState-NG`
   - **Description** : `Plugins open source pour serveurs NationsGlory — Armer's Industries / Projet Shadow State`
   - **Public**
   - Ne coche **rien** dans « Initialize this repository » (pas de README, pas de licence : ils sont déjà dans le dossier).
4. Clique **Create repository**.

## 2. Envoyer les fichiers

1. Sur la page du nouveau dépôt, clique le lien **uploading an existing file**.
2. Ouvre le dossier `ShadowState-NG` sur ton PC et **glisse tout son contenu** (pas le dossier lui-même) dans la page :
   `README.md`, `LICENCE.md`, `PUBLIER-SUR-GITHUB.md`, `build.sh`, `build.ps1`, `.gitignore`, `libs`, `plugins`, `kit-serveur`.
   - Ne glisse **pas** `build` ni `dist` s'ils existent (ce sont des fichiers compilés).
   - Le fichier `.gitignore` est caché par Windows : dans l'Explorateur, *Affichage → Afficher → Éléments masqués*.
3. En bas : message « Première version », puis **Commit changes**.

## 3. Publier les plugins compilés (Release)

1. Sur la page du dépôt, à droite : **Releases** → **Create a new release**.
2. **Choose a tag** : tape `v1.0.0` puis **Create new tag**.
3. **Release title** : `ShadowState-NG 1.0.0`.
4. Description (à copier) :
   ```
   Première version publique.
   Installation : copie les .jar dans plugins/ de ton serveur NG (SSNG-Core obligatoire, SSNG-Ponts + tous les SSNG-Pont-*.jar conseillés), redémarre.
   Tu l'utilises ? Ouvre une Issue « J'utilise ShadowState-NG » :)
   ```
5. Glisse **tous les .jar du dossier `dist`** dans la zone « Attach binaries ».
6. **Publish release**.

## 4. Ajouter le kit serveur (si le dépôt existe déjà sans lui)

1. Ouvre la page de ton dépôt `ShadowState-NG` sur GitHub.
2. Bouton **Add file** → **Upload files**.
3. Glisse **le dossier `kit-serveur` en entier** depuis ton PC dans la page (GitHub garde le dossier et ses fichiers).
4. Glisse aussi le nouveau **`README.md`** (il présente le kit) : GitHub remplace l'ancien.
5. En bas : message « Ajout du kit serveur », puis **Commit changes**.
6. Vérifie : le dossier `kit-serveur` apparaît dans la liste, et cliquer dessus affiche son guide.

Pour proposer le kit en téléchargement direct, dans ta Release : **Edit** → glisse un zip du dossier `kit-serveur` (clic droit sur le dossier → *Compresser dans un fichier ZIP*) → **Update release**.

## 5. Après

- Activer les Issues (normalement déjà actif) : *Settings → General → Features → Issues*.
- Pour une nouvelle version : refais l'étape 2 avec les fichiers modifiés (GitHub remplace ceux qui ont le même nom), puis une nouvelle Release (`v1.1.0`…).
- Si tu veux, je peux te préparer une version de ce guide avec GitHub Desktop (plus pratique pour les mises à jour).
