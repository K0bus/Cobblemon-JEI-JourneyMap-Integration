# Découverte Dynamique des Structures : Fossiles & Monuments Légendaires

Ce module offre une cartographie JourneyMap dynamique et 100% asynchrone pour les **sites archéologiques de fossiles Cobblemon** et les **sanctuaires/monuments de Pokémon légendaires** (incluant l'addon *Cobblemon: Legendary Monuments* et tous les datapacks personnalisés).

---

## 1. Philosophie : Zéro Hardcoding

Aucune liste statique d'identifiants de structure n'est stockée dans le code source :
- **Interrogation directe de `Registries.STRUCTURE`** à partir du `RegistryAccess` / `ClientLevel`.
- **Filtres dynamiques par Namespace & Tags** :
  * Tag de structures Minecraft `#cobblemon:fossil`, `#cobblemon:fossils/*` ou namespace `cobblemon` avec conventions de chemin (`fossil`, `prehistoric`, `dig_site`).
  * Namespace `legendarymonuments`, tags `#cobblemon:legendary`, `#legendarymonuments:*`, ou détection automatique de concordance avec les noms d'espèces de Pokémon légendaires (`PokemonSpecies.species`).
- **Résolution automatique des métadonnées** :
  * Dérivation de la clé de traduction (`structure.<namespace>.<path>`) ou formatage lisible du nom (ex: `prehistoric_birch_tree` → "Prehistoric Birch Tree").
  * Mappage automatique des icônes à partir des assets du mod (`textures/gui/structure_icons/*.png`, textures d'items de fossiles ou textures de piédestaux).

---

## 2. Architecture & Composants

```
com.cobblemon.jei.journeymap.structure
├── DynamicStructureRegistryService.kt  # Analyse et classification dynamique du registre STRUCTURE
├── FossilStructureHandler.kt           # Gestionnaire dédié aux sites de fouilles de fossiles
├── LegendaryStructureHandler.kt        # Gestionnaire dédié aux monuments et sanctuaires légendaires
├── StructureRegionScanner.kt           # Lecteur d'E/S asynchrone d'en-têtes Anvil (.mca) et chunks RAM
└── StructureOverlayManager.kt          # Coordinateur JourneyMap (cycle de vie, calques, mise en évidence)
```

### [DynamicStructureRegistryService](file:///home/k0bus/dev/minecraft/Cobblemon_JourneyMap/common/src/main/kotlin/com/cobblemon/jei/journeymap/structure/DynamicStructureRegistryService.kt)
- Analyse à la volée le registre mondial des structures.
- Détermine le `StructureType` (`FOSSIL`, `LEGENDARY`, `OTHER`).
- Associe chaque site à ses fossiles déterrés et aux espèces de Pokémon préhistoriques ou légendaires correspondantes.

### [FossilStructureHandler](file:///home/k0bus/dev/minecraft/Cobblemon_JourneyMap/common/src/main/kotlin/com/cobblemon/jei/journeymap/structure/FossilStructureHandler.kt)
- Gère la catégorie JourneyMap **`"Cobblemon Fossils"`**.
- Crée les marqueurs JourneyMap (`MarkerOverlay`) avec l'icône de fouille / fossile.
- Génère des infobulles complètes avec les spécimens déterrés (Amonita, Kabuto, Ptéra, Lilia, Anorith, Dinoclier, Kranidos, Carapagos, Arkéapti, Ptyranidur, Amagara, fossiles de Galar).
- Scanne les chunks actifs pour repérer les blocs suspects (`BrushableBlockEntity` / sable et gravier suspects) et affiner la position.

### [LegendaryStructureHandler](file:///home/k0bus/dev/minecraft/Cobblemon_JourneyMap/common/src/main/kotlin/com/cobblemon/jei/journeymap/structure/LegendaryStructureHandler.kt)
- Gère la catégorie JourneyMap **`"Legendary Monuments"`**.
- Crée les marqueurs dorés avec les icônes de sanctuaires.
- Scanne en jeu les piédestaux (`*_pedestal`), autels (`*_shrine`, `*_altar`), cocons et boîtes pour caler précisément le marqueur au bloc près sur l'autel rituel.

### [StructureRegionScanner](file:///home/k0bus/dev/minecraft/Cobblemon_JourneyMap/common/src/main/kotlin/com/cobblemon/jei/journeymap/structure/StructureRegionScanner.kt)
- Exécution **100% asynchrone** sur un pool de threads d'E/S dédié.
- Lit les tables `structures.starts` des fichiers `.mca` de chaque région explorée sans forcer le chargement de chunks Minecraft en mémoire vive.
- Met en cache les fichiers déjà lus pour ne jamais rescanner inutilement le disque.

### [StructureOverlayManager](file:///home/k0bus/dev/minecraft/Cobblemon_JourneyMap/common/src/main/kotlin/com/cobblemon/jei/journeymap/structure/StructureOverlayManager.kt)
- Centralise les marqueurs dans JourneyMap sur la minimap et la carte plein écran.
- Coordonne la **mise en évidence interactive** : lorsqu'un joueur sélectionne un Pokémon fossile ou légendaire dans l'interface de recherche, les marqueurs des sanctuaires correspondants sont instantanément mis en surbrillance dorée avec priorité d'affichage accrue.

---

## 3. Catégories JourneyMap

| Catégorie | Icône par défaut | Zoom Min / Max | Éléments détectés |
| :--- | :--- | :--- | :--- |
| **`Cobblemon Fossils`** | `textures/gui/fossil_marker.png` (ou item fossile) | 0 (Global) à Max | Sites préhistoriques, fouilles archéologiques, sable/gravier suspects |
| **`Legendary Monuments`** | `textures/gui/legendary_marker.png` (ou icône mod) | 0 (Global) à Max | Temples, autels, piédestaux légendaires, tours, sanctuaires |
