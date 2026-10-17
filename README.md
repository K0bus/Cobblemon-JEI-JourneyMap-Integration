# 🗺️ Cobblemon JEI & JourneyMap Integration

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-brightgreen.svg)](https://minecraft.net/)
[![NeoForge](https://img.shields.io/badge/NeoForge-21.1.249+-orange.svg)](https://neoforged.net/)
[![Fabric](https://img.shields.io/badge/Fabric-0.16.10+-blue.svg)](https://fabricmc.net/)
[![Cobblemon](https://img.shields.io/badge/Cobblemon-1.7.3+-red.svg)](https://cobblemon.com/)
[![JourneyMap](https://img.shields.io/badge/JourneyMap-5.10.x%20API%20v2-blue.svg)](https://journeymap.info/)
[![JEI](https://img.shields.io/badge/JEI-19.21+-purple.svg)](https://www.curseforge.com/minecraft/mc-mods/jei)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

**Cobblemon JEI & JourneyMap Integration** is a high-performance client-side mod for Minecraft Java (NeoForge & Fabric 1.21.1), designed to seamlessly bridge the **Cobblemon** ecosystem with **Just Enough Items (JEI)** and **JourneyMap**.

It provides:
1. **Comprehensive JEI Integration** to look up natural spawn conditions (biomes, dimensions, weather, time windows, altitudes, contexts) and full evolution trees for any species.
2. **Interactive JourneyMap Cartography** showing explored spawn-eligible zones on both the minimap and fullscreen map, featuring 2D geometric chunk polygon merging and rarity-coded palettes.
3. **Real-time Entity Radar & Global Shiny Sentry** (passively scans and alerts you to any nearby wild Shiny Pokémon with visual announcements and audio fanfares).
4. **Dynamic Structure Discovery** automatically locating archaeological fossil dig sites and legendary monument shrines.
5. **Immersive Floating HUD** displaying tracked species stats, element types, and live detection counters directly below the fullscreen JourneyMap toolbar.

---

## 🌟 Key Features

### 1. 🔍 Just Enough Items (JEI) Integration
- **Search by Cobblemon Species Ingredient**: Look up any Pokémon by its translated name (EN/FR), National Pokédex number, or elemental types.
- **Ultra-Detailed Spawn Cards**:
  - Eligible dimensions (Overworld, Nether, End, plus modded dimensions such as The Aether).
  - Allowed biomes (with dynamic registry tag resolution like `#minecraft:is_forest`, `#c:is_nether`, etc.).
  - Environmental criteria: Weather (clear, rain, thunder), time of day (day, night, dawn, dusk), Y altitude bounds, and spawn contexts (ground, water, lava, underground, fishing).
  - Spawn weights, estimated rarity buckets, and level ranges.
- **Evolution Tree Cards**:
  - Level thresholds, evolution stones and items (linking cords, held items), trade conditions, friendship levels, and required time-of-day windows.
- **Pokémon Defeat Drops (Loot Tables)**:
  - View all item drops obtained by defeating wild Pokémon, with quantities, exact drop percentages, roll counts, and evolution item tags.


---

### 2. 🗺️ JourneyMap Mapping & Overlays
- **Dedicated Fullscreen Toolbar Button**: Custom Pokéball icon allowing instant searching and toggling of Pokémon species tracking.
- **Geometric Chunk Polygon Merger (`ChunkPolygonMerger`)**:
  - Automatically merges contiguous eligible chunks into smooth, clean 2D polygon boundaries.
  - Eliminates interior grid lines and reduces graphical map overlay objects by up to 95% for fluid framerates.
- **Rarity Color Palette (`RarityColorPalette`)**:
  - 🟢 **Common**: Emerald Green (`#2ECC71`)
  - 🔵 **Uncommon**: Lagoon Blue (`#3498DB`)
  - 🟣 **Rare**: Amethyst Purple (`#9B59B6`)
  - 🟠 **Ultra-Rare**: Neon Amber (`#E67E22`)
- **2-Tier Asynchronous Disk Scanner (`JourneyMapDiskScanner`)**:
  - **Tier 1 (Instantaneous)**: Immediate rendering (< 15 ms) utilizing active RAM chunks and in-memory caches.
  - **Tier 2 (Background Thread)**: Multi-threaded asynchronous scanning of on-disk explored world regions (`.mca`, `.jmc`, JourneyMap map tiles).
  - Full multi-dimension support (Overworld, Nether, End, modded dimensions).

---

### 3. ⚡ Real-Time Entity Radar & Global Shiny Sentry
- **Permanent Global Shiny Sentry**:
  - Passively scans for **any wild Shiny Pokémon** within range (~160 blocks horizontally, 64 blocks vertically), even when no Pokémon is currently selected or when tracking a different target.
  - **Audio Fanfare**: Plays a celebratory chime (`UI_TOAST_CHALLENGE_COMPLETE`).
  - **Visual Notification**: Golden highlight banner in the client Actionbar.
  - **Dedicated Map Marker (`MarkerOverlay`)**: Sparkling golden Pokéball marker highlighting the Shiny's exact position with species name, level, and status tooltip.
- **Active Target Radar**:
  - Tracks live positions of the selected species and form in real-time.
  - Smooth marker movement on both the minimap and fullscreen map.
  - Automatic removal upon entity capture, defeat, or despawn.

---

### 4. 🏛️ Dynamic Structure Discovery: Fossils & Legendary Monuments
- **Zero Hardcoded Registry Lists**:
  - Inspects the game's `Registries.STRUCTURE` dynamically during world join.
  - Automatically indexes structures by namespaces (`cobblemon`, `legendarymonuments`) and data tags (`#cobblemon:fossil`, `#cobblemon:legendary`).
- **Archaeological Fossil Sites (`FossilStructureHandler`)**:
  - Dedicated JourneyMap overlay category: `"Cobblemon Fossils"`.
  - Detailed tooltips displaying resurrectable prehistoric species (Omanyte, Kabuto, Aerodactyl, Lileep, Anorith, Shieldon, Cranidos, Tirtouga, Archen, Tyrunt, Amaura, and Galar fossils).
  - Accurate spatial positioning pinpointing suspicious blocks (`BrushableBlockEntity` / suspicious sand and gravel).
- **Legendary Monuments & Shrines (`LegendaryStructureHandler`)**:
  - Dedicated JourneyMap overlay category: `"Legendary Monuments"`.
  - Distinctive shrine markers centered precisely over pedestal blocks (`*_pedestal`), altars, and sanctums in loaded chunks.
- **Interactive Cross-Highlighter**:
  - Selecting a fossil or legendary species in JEI or JourneyMap immediately illuminates its corresponding archaeological and monument sites.
- **Asynchronous Disk Performance (`StructureRegionScanner`)**:
  - Multi-threaded asynchronous parsing of Anvil chunk headers (`.mca`) with persistent caching.

---

### 5. 🎴 Floating HUD & Live Statistics
- Modern floating information card integrated directly below the JourneyMap toolbar button:
  - 3D Pokémon model preview or official sprite icon.
  - Colored elemental type badges.
  - Pokedex # and estimated rarity indicator.
  - Animated progress indicator for asynchronous region scanning.
  - Live radar status (*"X target(s) in range"* / *"★ SHINY Detected!"*).

---

### 6. 📖 Complete Pokédex & Evolution Tracker (Bindable Key: `P`)
- **Bindable Shortcut**: Open the full Pokédex Tracker screen with a single customizable keystroke (default: `P` or accessible via JourneyMap toolbar).
- **Dual-Pane Interactive Interface**:
  - **Left Responsive Grid**:
    - Browse all 1000+ Cobblemon species with visual status indicators: 🟢 **Caught** (with Pokéball badge), 🟠 **Encountered** (seen only), ⚪ **Missing / Undiscovered** (silhouette).
    - Real-time search filter by Pokémon name, National Dex #, or elemental types.
  - **Tabs**:
    - **All Pokémon**: Complete National Dex view with completion statistics.
    - **Missing Pokémon**: Filter solely undiscovered/uncaught species to target your next hunts.
    - **Pending Evolutions**: Comprehensive roadmap of all evolutions leading to missing Pokédex entries.
- **Smart PC & Inventory Evolution Matcher**:
  - Automatically scans your **Party, PC Boxes, and Player Inventory**.
  - Highlights evolutions that are **immediately feasible with what you have in your PC**:
    - ⭐ **Ready to Evolve!**: You have the base Pokémon in your PC/Party, required items (stones, linking cables) in your inventory, and level/friendship prerequisites fulfilled.
    - ⏳ **In PC Storage**: Base Pokémon is in your PC/Party, clearly showing what is missing (e.g., *"Thunder Stone (Missing)"*, *"Level 36 required (Current: 30)"*).
    - ❓ **Base Pokémon Needed**: Evolution target missing because the pre-evolution hasn't been captured yet.
- **Detailed Inspection Panel**:
  - Large scaled model/sprite preview, elemental types, Pokédex status.
  - Exact storage locations (e.g., *"In PC: Box 1 (Lv. 42)"* or *"In Party (Slot 2)"*).
  - Evolution line preview and one-click *"View on JourneyMap"* button.

### 7. 🌿 Jade Integration (Optional Soft-Dependency)
- **Automatic Pastured Pokémon Detection**:
  - Automatically identifies whether a Pokémon is tethered to a Pasture Block (`PokemonPastureBlockEntity`).
- **Live Fullness Gauge (`PastureFullnessProvider`)**:
  - Official Jade progress bar showing current fullness (`0` to `100`).
  - Dynamic color gradients: emerald green when well-fed, amber when hungry (<30), and crimson red with `(Hungry)` notice when depleted.
- **Next Drop / Metabolism Countdown (`PastureDropTimerProvider`)**:
  - Real-time countdown timer before next pasture drop (`Xm Ys` or `Xs`).
  - Dynamic cyan progress bar tracking metabolism cycle progress towards species rate.
  - Paused notice in red when fullness reaches 0 (production halts until fed).
- **Client & Server Synchronized (`PasturePokemonDataProvider`)**:
  - Server-side data provider ensuring 100% precision in singleplayer, LAN, or dedicated servers.
- **Configurable via Jade GUI**:
  - Independent toggles for Fullness and Drop Timer in Jade's in-game config screen.

---

## 🛠️ Project Structure & Architecture

```
Cobblemon_JourneyMap/
├── common/src/main/kotlin/fr/k0bus/cobblemon_jei/
│   ├── CobblemonJeiPlugin.kt             # JEI entrypoint (ingredients, categories, recipes)
│   ├── data/
│   │   ├── CobblemonDataScanner.kt       # Multi-source JSON scanner (ModList, Classpath, Disk)
│   │   ├── PokemonSpawnDataLoader.kt     # Parsing & consolidation of Cobblemon spawn rules
│   │   └── PokemonEvolutionDataLoader.kt # Evolution branch resolver
│   ├── ingredient/                       # Custom JEI Pokémon species ingredient & renderer
│   ├── recipe/                           # JEI graphical recipe categories (Spawns & Evolutions)
│   └── journeymap/
│       ├── CobblemonJourneyMapPlugin.kt  # JourneyMap API v2 client plugin & UI toolbar button
│       ├── CobblemonJourneyMapOverlayHud.kt # Fullscreen HUD card below the Pokéball button
│       ├── CobblemonJourneyMapSearchScreen.kt # Pokémon search & selection dialog
│       ├── CobblemonOverlayManager.kt    # Public overlay controller facade
│       ├── SpawnOverlayManager.kt        # 2-step display pipeline orchestrator
│       ├── CobblemonSpawnMatcher.kt      # Biome/dimension condition matching engine
│       ├── ChunkPolygonMerger.kt         # 2D geometric adjacent chunk polygon merger
│       ├── JourneyMapDiskScanner.kt      # Multi-threaded async disk region scanner
│       ├── PokemonRadarService.kt        # Live radar tracker & Global Shiny Sentry
│       ├── RarityColorPalette.kt         # Rarity color palette & rich tooltip generator
│       └── structure/                    # Dynamic structure cartography module
│           ├── DynamicStructureRegistryService.kt # Tag-based structure classifier
│           ├── FossilStructureHandler.kt          # Archaeological site handler
│           ├── LegendaryStructureHandler.kt       # Legendary monument handler
│           ├── StructureRegionScanner.kt          # Async Anvil (.mca) region scanner
│           └── StructureOverlayManager.kt         # JourneyMap structure coordinator
├── neoforge/                             # NeoForge 1.21.1 module
│   ├── src/main/java/.../CobblemonJeiNeoForge.java # Client @Mod entrypoint
│   └── src/main/resources/META-INF/neoforge.mods.toml
├── fabric/                               # Fabric 1.21.1 module
│   └── src/main/resources/fabric.mod.json # Fabric mod metadata & entrypoint configuration
└── docs/                                 # Detailed technical documentation
    ├── ARCHITECTURE.md
    ├── RADAR_AND_SHINY_SENTRY.md
    ├── JOURNEYMAP_INTEGRATION.md
    └── DYNAMIC_STRUCTURE_DISCOVERY.md
```

---

## 🚀 Installation & Build

### Requirements
- **Java 21** (Temurin / OpenJDK 21 recommended)
- **Minecraft** 1.21.1
- **NeoForge** 21.1.249+ or **Fabric Loader** 0.16.10+
- **Cobblemon** 1.7.3+
- **JourneyMap** 5.10.x (API v2)
- **JEI (Just Enough Items)** 19.21+

### Building From Source

The project uses Gradle with automated dynamic Git versioning:

```bash
# Clone the repository
git clone git@github.com:K0bus/Cobblemon-JEI-JourneyMap-Integration.git
cd Cobblemon-JEI-JourneyMap-Integration

# Run test suite and build both jars (NeoForge & Fabric)
./gradlew clean check assemble
```

Compiled mod JARs will be generated under:
- **NeoForge**: `neoforge/build/libs/cobblemon-jei-neoforge-<version>.jar`
- **Fabric**: `fabric/build/libs/cobblemon-jei-fabric-<version>.jar`

Copy the corresponding JAR file into your `.minecraft/mods/` directory or your Prism Launcher / Modrinth / CurseForge profile.

---

## 📄 License
This project is licensed under the [MIT License](LICENSE).

Cobblemon is a trademark of its respective creators. Minecraft is a registered trademark of Mojang Studios.
