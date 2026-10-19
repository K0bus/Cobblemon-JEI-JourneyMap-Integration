# Workflow Release Automatique

Quand je demande "Release le mod" ou "Fais une mise à jour" :

1. **Détection de la version actuelle** :
   - Récupère le dernier tag : `git describe --tags --abbrev=0` (si aucun, partir de `v0.1.0`).

2. **Analyse du diff & calcul SemVer** :
   - Analyse les modifications (`git diff $(git describe --tags --abbrev=0)..HEAD`).
   - Détermine la nouvelle version selon ces critères :
     - **PATCH (+0.0.1)** : Uniquement des corrections de bugs, refactorings internes ou ajustements mineurs.
     - **MINOR (+0.1.0)** : Nouveaux blocs, items, commandes, mécaniques ou changements rétrocompatibles.
     - **MAJOR (+1.0.0)** : Changement d'API cassant, réécriture complète ou changement majeur de version MC.

3. **Validation & Exécution** :
   - Indique la version calculée et la justification rapide en une phrase.
   - Commit les changements en attente : `git commit -am "chore(release): bump to vX.Y.Z"`.
   - Pose le tag annoté : `git tag -a vX.Y.Z -m "Release vX.Y.Z"`.
   - Lance le build : `./gradlew clean build`.
   - Push le code et les tags : `git push && git push --tags`.

4. **Changelog CurseForge** :
   - Rédige le patchnote bilingue (EN / FR) basé strictement sur le diff analysé.