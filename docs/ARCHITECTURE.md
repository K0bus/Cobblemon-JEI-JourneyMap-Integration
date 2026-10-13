# 🏛️ Architecture Technique & Flux de Données

Ce document décrit l'architecture globale du mod, la séparation multi-loader, les ponts d'interopérabilité et les stratégies d'extraction de données.

---

## 📦 Organisation des Modules

Le projet suit l'architecture moderne standard multi-loader :

- **`:common`** : Contient 98% de la logique métier (Kotlin pur avec dépendances communes Minecraft, Cobblemon, JEI et JourneyMap API).
- **`:neoforge`** : Conteneur NeoForge 1.21.1 contenant l'annotation `@Mod(value = "cobblemon_jei", dist = Dist.CLIENT)`, la configuration TOML et les tests d'intégration unitaires.
- **`:fabric`** : Conteneur réservé pour le loader Fabric si ciblé à l'avenir.

---

## 🔄 Flux d'Extraction des Données Cobblemon (`CobblemonDataScanner`)

Cobblemon stocke ses définitions d'espèces et de règles de spawn dans les dossiers de données de datapacks (`data/cobblemon/species/` et `data/cobblemon/spawn_pool_world/`).

Pour fonctionner de manière transparente et exhaustive dans tous les environnements d'exécution (serveur dédié distant, monde solo, environnement de dev Gradle), le scanner interroge séquentiellement plusieurs sources :

1. **`scanNeoForgeModList`** : Utilise l'API officielle `net.neoforged.fml.ModList` pour inspecter les fichiers jar des mods montés en mémoire.
2. **`scanJavaClassPath`** : Scanne les entrées du classpath `java.class.path`.
3. **`scanClassLoaderResources`** : Détecte les ressources de marqueurs Cobblemon (`species.schema.json`).
4. **`scanCodeSource`** : Dérive l'emplacement physique du JAR à partir du `ProtectionDomain` de la classe `PokemonSpecies`.
5. **`scanModsDirectory`** : Scanne récursivement le dossier `mods/` de l'instance locale.
6. **`scanServerResourceManager` & `scanClientResourceManager`** : Interroge les gestionnaires de ressources Minecraft s'ils sont chargés.

---

## 🧩 Ponts d'Interopérabilité

### 1. Just Enough Items (JEI)
- **Plugin** : [CobblemonJeiPlugin.kt](file:///home/k0bus/dev/minecraft/Cobblemon_JourneyMap/common/src/main/kotlin/com/cobblemon/jei/CobblemonJeiPlugin.kt) annoté `@JeiPlugin`.
- **Ingrédient personnalisé** : `PokemonSpeciesIngredient` avec renderer graphique de modèles/icônes.
- **Catégories enregistrées** :
  - `cobblemon:spawn` : [PokemonSpawnRecipeCategory.kt](file:///home/k0bus/dev/minecraft/Cobblemon_JourneyMap/common/src/main/kotlin/com/cobblemon/jei/recipe/PokemonSpawnRecipeCategory.kt)
  - `cobblemon:evolution` : [PokemonEvolutionRecipeCategory.kt](file:///home/k0bus/dev/minecraft/Cobblemon_JourneyMap/common/src/main/kotlin/com/cobblemon/jei/recipe/PokemonEvolutionRecipeCategory.kt)

### 2. JourneyMap API v2
- **Plugin** : [CobblemonJourneyMapPlugin.kt](file:///home/k0bus/dev/minecraft/Cobblemon_JourneyMap/common/src/main/kotlin/com/cobblemon/jei/journeymap/CobblemonJourneyMapPlugin.kt) implémentant `IClientPlugin`.
- **Événements** :
  - `ADDON_BUTTON_DISPLAY_EVENT` : Ajout du bouton Pokéball dans la barre d'outils plein écran.
  - `ScreenEvent.Render.Post` : Dessin du HUD d'information flottant.
  - `ClientTickEvent.Post` : Scan périodique du radar d'entités et de la sentinelle Shiny.
