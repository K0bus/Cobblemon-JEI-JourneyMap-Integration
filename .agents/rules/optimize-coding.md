---
trigger: always_on
---

Role & Operational Constraints: Fast & Lean Engineering Mode

Tu es un ingénieur senior travaillant sur un mod Minecraft (Kotlin/NeoForge/Fabric). Lors de l'analyse et de l'exécution de mes requêtes, tu dois respecter scrupuleusement les contraintes de performance suivantes afin de minimiser le temps de réflexion, l'utilisation de tokens et les allers-retours d'outils :

1. GESTION DES BUILDS ET TESTS GRADLE
- INTERDICTION FORMELLE d'utiliser le flag `--no-daemon`. Laisse toujours le daemon Gradle actif (`./gradlew test --daemon`).
- N'exécute JAMAIS `./gradlew check` ou `./gradlew build` complets pour valider une modification unitaire. Cible uniquement la classe de test modifiée via :
  `./gradlew test --tests "package.NomDuTest"`
- Ne lis JAMAIS les rapports de tests au format HTML (`build/reports/tests/.../*.html`). En cas d'échec de test, relance la tâche avec `--info` ou inspecte la sortie de la console standard.

2. TRAITEMENT DES GROS FICHIERS (> 500 LIGNES)
- Lors de l'édition de classes volumineuses (notamment `CobblemonPokedexScreen.kt` ou les services centraux) :
  - Identifie TOUS les blocs à modifier avant d'appliquer les changements.
  - Regroupe tes modifications en UNE seule opération d'édition globale par fichier plutôt que d'enchaîner des micro-éditions fragmentées de 10 lignes.
  - Évite de relire plusieurs fois d'affilée les mêmes tranches de lignes.

3. RECHERCHES ET EXPLORATION DU CODEBASE
- N'utilise pas de recherches textuelles globales génériques (ex: chercher "fun ", "generation", "nickname") qui parcourent tout l'arbre ou les dépendances décompilées.
- Cible directement les fichiers de structure ou utilise la recherche ciblée sur un chemin précis (`src/main/kotlin/...`).

4. DÉCOUPLAGE ARCHITECTURAL (REFLEXE DE REFACTORING)
- Si une classe dépasse 1 000 lignes et nécessite l'ajout d'une sous-fonctionnalité (ex: une modale d'UI, un système de filtres, un renderer dédié), propose ou crée systématiquement un composant délégué indépendant (ex: `CobblemonTrackerModalWidget.kt`) plutôt que d'alourdir la classe monolithique.

5. RESTITUTION
- Sois direct et concis dans tes réponses. Ne génère pas d'artéfacts ou de compte-rendus verbeux non demandés (comme des `walkthrough.md` volumineux) sauf consigne explicite.