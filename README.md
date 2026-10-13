# 🗺️ Cobblemon JEI & JourneyMap Integration

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-brightgreen.svg)](https://minecraft.net/)
[![NeoForge](https://img.shields.io/badge/NeoForge-21.1.249+-orange.svg)](https://neoforged.net/)
[![Cobblemon](https://img.shields.io/badge/Cobblemon-1.7.3+-red.svg)](https://cobblemon.com/)
[![JourneyMap](https://img.shields.io/badge/JourneyMap-5.10.x%20API%20v2-blue.svg)](https://journeymap.info/)
[![JEI](https://img.shields.io/badge/JEI-19.21+-purple.svg)](https://www.curseforge.com/minecraft/mc-mods/jei)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

**Cobblemon JEI & JourneyMap Integration** est un mod client haute performance pour Minecraft Java (NeoForge 1.21.1), conçu pour fusionner l'écosystème **Cobblemon** avec **Just Enough Items (JEI)** et **JourneyMap**.

Il fournit :
1. **Une intégration JEI complète** pour consulter les conditions d'apparition (biomes, dimensions, météo, tranches horaires, altitudes, contextes) et les arbres d'évolution de chaque espèce.
2. **Une cartographie interactive JourneyMap** affichant sur la minimap et la carte plein écran les zones éligibles de spawn explorées avec fusion géométrique des chunks et code couleur par rareté.
3. **Un radar d'entités en temps réel** avec **Sentinelle Shiny globale** (détection de tous les Pokémon Shiny sauvages à proximité avec alertes sonores et visuelles).
4. **Un HUD immersif** flottant sous le bouton Pokéball de la carte plein écran.

---

## 🌟 Fonctionnalités Principales

### 1. 🔍 Intégration Just Enough Items (JEI)
- **Recherche par ingrédient Cobblemon** : Recherchez n'importe quel Pokémon par son nom traduit (FR/EN), numéro de Pokédex national, ou type élémentaire.
- **Fiches d'apparition ultra-détaillées** :
  - Dimensions éligibles (Overworld, Nether, End, dimensions moddées comme l'Aether).
  - Biomes autorisés (avec résolution des tags dynamiques `#minecraft:is_forest`, `#c:is_nether`, etc.).
  - Conditions environnementales : Météo (clair, pluie, orage), tranche horaire (jour, nuit, aube, crépuscule), altitude Y min/max, contextes (au sol, dans l'eau, dans la lave, souterrain, pêche).
  - Poids de spawn, rareté estimée et niveau moyen.
- **Fiches d'évolution** :
  - Niveaux requis, objets d'évolution (pierres, câbles de liaison), échanges, amitié, tenues d'objets, moments de la journée.

---

### 2. 🗺️ Cartographie & Overlays JourneyMap
- **Bouton dédié dans la barre d'outils plein écran** : Icône Pokéball cliquable permettant de rechercher et sélectionner un Pokémon à suivre.
- **Moteur de fusion géométrique de chunks (`ChunkPolygonMerger`)** :
  - Combine automatiquement les chunks adjacents éligibles en polygones 2D optimisés.
  - Zéro quadrillage interne parasite, jusqu'à 95% d'objets graphiques en moins pour des performances fluides.
- **Charte graphique indexée sur la rareté (`RarityColorPalette`)** :
  - 🟢 **Commun (Common)** : Vert émeraude (`#2ECC71`)
  - 🔵 **Peu commun (Uncommon)** : Bleu lagon (`#3498DB`)
  - 🟣 **Rare (Rare)** : Violet améthyste (`#9B59B6`)
  - 🟠 **Ultra-Rare (Ultra-Rare)** : Ambre néon (`#E67E22`)
- **Scan persistant asynchrone (`JourneyMapDiskScanner`)** :
  - **Étape 1 (Instantanée)** : Affichage immédiat (< 15ms) à partir du cache mémoire et des chunks chargés en RAM.
  - **Étape 2 (Arrière-plan)** : Analyse multithreadée des régions explorées sur disque (`.mca`, `.jmc`, tuiles JourneyMap).
  - Support multi-dimensionnel complet (Overworld, Nether, End, Aether, dimensions moddées).

---

### 3. ⚡ Radar en temps réel & Sentinelle Shiny Globale
- **Sentinelle Shiny permanente** :
  - Détecte **tous les Pokémon Shiny sauvages** à proximité (~160 blocs horizontalement, 64 blocs verticalement), même si aucun Pokémon n'est traqué ou si vous en cherchez un autre.
  - **Alerte Sonore** : Fanfare triomphale (`UI_TOAST_CHALLENGE_COMPLETE`).
  - **Alerte Visuelle** : Message doré dans l'Actionbar.
  - **Marqueur JourneyMap dédié (`MarkerOverlay`)** : Pokéball dorée scintillante pointant la position du Shiny avec son nom, niveau et statut.
- **Radar de cible traquée** :
  - Piste en direct l'espèce et la forme du Pokémon actuellement sélectionné.
  - Déplacement fluide du marqueur sur la minimap et la carte plein écran.
  - Nettoyage instantané lors de la capture, défaite ou despawn de l'entité.

---

### 4. 🎴 HUD Flottant & Compteurs de Détection
- Carte d'information moderne intégrée sous le bouton JourneyMap :
  - Modèle 3D / icône du Pokémon suivi.
  - Badges des types élémentaires colorés.
  - Rareté estimée et Pokédex #.
  - Barre de progression animée du scan global des régions.
  - État du radar en temps réel (*"X cible(s) à portée"* / *"★ SHINY détecté !"*).

---

## 🛠️ Architecture Technique

```
Cobblemon_JourneyMap/
├── common/src/main/kotlin/com/cobblemon/jei/
│   ├── CobblemonJeiPlugin.kt             # Point d'entrée JEI (ingrédients, catégories, recettes)
│   ├── data/
│   │   ├── CobblemonDataScanner.kt       # Découverte multi-sources des JSON de données (ModList, Classpath, Disque)
│   │   ├── PokemonSpawnDataLoader.kt     # Parsing & consolidation des règles de spawn Cobblemon
│   │   └── PokemonEvolutionDataLoader.kt # Parsing & résolution des branches d'évolution
│   ├── ingredient/                       # Gestion du type d'ingrédient Pokémon dans JEI
│   ├── recipe/                           # Catégories graphiques JEI (Spawn & Évolution)
│   └── journeymap/
│       ├── CobblemonJourneyMapPlugin.kt  # Enregistrement plugin JourneyMap v2 & bouton UI
│       ├── CobblemonJourneyMapOverlayHud.kt # HUD carte plein écran sous le bouton Pokéball
│       ├── CobblemonJourneyMapSearchScreen.kt # Écran de sélection et de recherche de Pokémon
│       ├── CobblemonOverlayManager.kt    # Façade publique de contrôle de l'overlay
│       ├── SpawnOverlayManager.kt        # Orchestrateur d'affichage et pipeline en 2 étapes
│       ├── CobblemonSpawnMatcher.kt      # Algorithme d'éligibilité biome/dimension
│       ├── ChunkPolygonMerger.kt         # Fusion géométrique 2D des chunks adjacents
│       ├── JourneyMapDiskScanner.kt      # Scan asynchrone des régions disques JourneyMap & Anvil
│       ├── PokemonRadarService.kt        # Radar d'entités temps réel & Sentinelle Shiny
│       └── RarityColorPalette.kt         # Charte graphique & infobulles riches
├── neoforge/                             # Module NeoForge 1.21.1
│   ├── src/main/java/.../CobblemonJeiNeoForge.java # Point d'entrée mod client NeoForge
│   └── src/main/resources/META-INF/neoforge.mods.toml
└── docs/                                 # Documentation technique détaillée
    ├── ARCHITECTURE.md
    ├── RADAR_AND_SHINY_SENTRY.md
    └── JOURNEYMAP_INTEGRATION.md
```

---

## 🚀 Installation & Compilation

### Prérequis
- **Java 21** (JDK 21 Temurin / OpenJDK recommandé)
- **Minecraft** 1.21.1
- **NeoForge** 21.1.249 ou supérieur
- **Cobblemon** 1.7.3+
- **JourneyMap** 5.10.x (API v2)
- **JEI (Just Enough Items)** 19.21+

### Compilation depuis les sources

```bash
# Cloner le dépôt
git clone git@github.com:K0bus/Cobblemon-JEI-JourneyMap-Integration.git
cd Cobblemon-JEI-JourneyMap-Integration

# Compiler le projet
./gradlew clean test build
```

Le fichier JAR compilé se trouve dans :
`neoforge/build/libs/cobblemon-jei-neoforge-1.0.0.jar`

Copiez simplement ce fichier dans votre dossier `.minecraft/mods/` ou dans votre instance PrismLauncher / CurseForge.

---

## 📄 Licence
Ce projet est sous licence [MIT](LICENSE).
Cobblemon est une marque de ses créateurs respectifs. Minecraft est une marque déposée de Mojang Studios.
