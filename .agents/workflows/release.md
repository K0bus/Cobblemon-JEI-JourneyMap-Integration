---
description: Workflow Release & Déploiement CurseForge
---

# Workflow Release & Déploiement CurseForge

Ce workflow s'applique dès que l'utilisateur demande : *"Release le mod"*, *"Fais une mise à jour"*, *"Publie une version"* ou une instruction équivalente.

---

## 1. Détection de version actuelle & calcul SemVer
1. Récupérer le tag le plus récent :
   ```bash
   git describe --tags --abbrev=0
   ```
   *(Si aucun tag n'existe, partir de `v0.1.0`).*
2. Analyser les commits et changements depuis ce dernier tag :
   ```bash
   git diff $(git describe --tags --abbrev=0)..HEAD
   ```
3. Déterminer la nouvelle version selon la convention SemVer :
   - **PATCH (+0.0.1)** : Uniquement corrections de bugs, refactorings internes, ajustements de compatibilité mineurs.
   - **MINOR (+0.1.0)** : Nouveaux blocs, items, commandes, mécaniques de gameplay ou ajouts rétrocompatibles.
   - **MAJOR (+1.0.0)** : Changements d'API cassants, réécritures complètes ou changement majeur de version Minecraft.

---

## 2. Rédaction du Patchnote & Génération du Changelog
1. Rédiger un changelog bilingue clair et structuré (**EN / FR**) basé strictement sur le diff analysé.
2. Écrire le contenu au format Markdown dans le fichier temporaire dédié :
   ```
   build/release_changelog.md
   ```
   *(Ce fichier sera directement injecté par Gradle lors de la publication CurseForge).*

---

## 3. Build local & Vérification (Zéro Publication)
Exécuter la vérification complète en local :
```bash
./gradlew clean build
```
- **Règle stricte** : Cette commande valide la compilation, les mappings et les tests unitaires.
- **Sécurité** : L'exécution de `build` ne doit en aucun cas déclencher de publication CurseForge.

---

## 4. 🛑 GATE DE VALIDATION OBLIGATOIRE (Arrêt Interactif)

> [!CAUTION]
> **RÈGLE ABSOLUE D'ARRÊT OBLIGATOIRE :**
> L'agent a l'**INTERDICTION FORMELLE** d'exécuter la moindre action de release (création de tag Git, push Git ou commande de publication CurseForge) tant que l'utilisateur n'a pas répondu par l'affirmative dans la conversation.

### Actions à réaliser obligatoirement à cette étape :
1. **Présenter le récapitulatif complet à l'utilisateur** :
   - **Tag calculé** (ex. `v1.2.0`).
   - **Noms de release CurseForge** : `vX.Y.Z [Fabric]` et `vX.Y.Z [NeoForge]`.
   - **Justification SemVer** concise.
   - **Statut du build local** (`./gradlew clean build` réussi).
   - **Aperçu complet du changelog bilingue** (EN / FR).
2. **Demander la confirmation explicite** :
   > « Valider la release **vX.Y.Z** et publier sur CurseForge ? [o/N] »
3. **STOP / Attente de réponse** : L'agent s'arrête ici et attend le retour de l'utilisateur.

---

## 5. Gestion des Réponses & Exécution Conditionnelle

### Scénario A — Refus, Annulation ou Demande de Correction
Si l'utilisateur répond « non », « N », demande des corrections ou ne valide pas explicitement :
- **Annulation immédiate et propre** de la publication.
- **AUCUN** tag Git n'est créé (`git tag`).
- **AUCUN** push distant n'est effectué (`git push`).
- **AUCUN** artefact n'est envoyé à CurseForge (commande `./gradlew curseforge` non exécutée).
- Appliquer les ajustements demandés par l'utilisateur si nécessaire, puis relancer à partir de l'étape 3.

### Scénario B — Validation Explicite (« oui », « o », confirmation affirmative)
Uniquement si l'utilisateur valide explicitement, exécuter la séquence ordonnée :

1. **Commit des changements éventuels & bump** :
   ```bash
   git commit -am "chore(release): bump to vX.Y.Z"
   ```
2. **Pose du tag Git annoté** :
   ```bash
   git tag -a vX.Y.Z -m "Release vX.Y.Z"
   ```
3. **Publication isolée vers l'API CurseForge** :
   ```bash
   ./gradlew curseforge
   ```
4. **Push du code et des tags vers le dépôt distant** :
   ```bash
   git push && git push --tags
   ```

---

## 6. Déploiement Local Modpack (Optionnel / Recommandé)
Une fois la publication terminée, exécuter ou proposer le déploiement direct dans le modpack de test local en suivant le workflow :
[deploy-modpack.md](file:///home/k0bus/dev/minecraft/Cobblemon_JourneyMap/.antigravity/workflows/deploy-modpack.md).