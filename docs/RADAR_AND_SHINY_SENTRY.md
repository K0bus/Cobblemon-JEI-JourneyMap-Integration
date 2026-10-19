# 🛡️ Radar & Sentinelle Shiny Globale

Le module **PokemonRadarService** apporte une couche de détection d'entités vivantes côté client pour identifier les Pokémon dans un rayon d'action autour du joueur et les afficher dynamiquement sur JourneyMap.

---

## 🎯 Vue d'ensemble

Le radar fonctionne sous deux modes complémentaires exécutés en parallèle :

1. **La Sentinelle Shiny Globale (Permanent)** :
   - Écoute en continu les entités chargées dans le monde client (`ClientLevel`).
   - Détecte **tous les Pokémon Shiny sauvages** indépendamment de l'espèce ou de la recherche en cours.
   - Même si aucun calque de spawn n'est ouvert ou si vous recherchez un autre Pokémon, un Shiny qui apparaît déclenche immédiatement l'alerte et place un marqueur doré sur la carte.

2. **Le Radar de Cible Traquée (Dynamique)** :
   - S'active dès qu'un Pokémon est sélectionné via l'interface de recherche JourneyMap ou une recette JEI.
   - Piste les spécimens de l'espèce (et de la forme) cible.
   - Met à jour leur position à chaque seconde tant qu'ils restent vivants et à portée.

---

## ⚙️ Spécifications Techniques

| Paramètre | Valeur | Description |
|---|---|---|
| **Cadence de Scan** | 20 ticks (1.0 seconde) | Écouteur `ClientTickEvent.Post` avec modulo `tickCounter % 20 == 0`. Impact CPU négligeable (< 0.2ms par seconde). |
| **Périmètre Horizontal (XZ)** | 160 blocs | Couvre l'intégralité de la bulle d'apparition client Cobblemon standard. |
| **Périmètre Vertical (Y)** | 64 blocs | Détection en profondeur (grottes) et en altitude. |
| **Entité cible** | `PokemonEntity` | Entités vivantes non retirées (`entity.isAlive && !entity.isRemoved`). |
| **Filtrage Strict (Radar & Shiny)** | `isWildAndCatchable(entity)` | **Uniquement sauvages et capturables** : exclut formellement les Pokémon possédés/suivis (`ownerUUID`, `isTame`), les Pokémon en pâturage (`tethering`, `tetheringId`), portés sur l'épaule/montures (`isPassenger`, `vehicle`), et incapturables (`isUncatchable()`, `UNBATTLEABLE`). |

---

## 🎨 Affichage & Marqueurs JourneyMap

Le service utilise des `MarkerOverlay` rattachés au groupe `"Cobblemon Radar"` :

### Marqueur Normal
- **Texture** : `assets/cobblemon_jei/textures/gui/radar_marker.png` (Pokéball haute résolution avec anneau cyan).
- **Label** : `NomDuPokemon Lv.X` (texte blanc avec ombre portée).
- **Z-Index** : 1500 (affiché au-dessus des polygones de spawn).

### Marqueur Shiny
- **Texture** : `assets/cobblemon_jei/textures/gui/radar_marker_shiny.png` (Pokéball dorée avec éclats étoilés).
- **Label** : `★ NomDuPokemon Lv.X` (texte doré `#FFD700`).
- **Z-Index** : 1600 (priorité absolue d'affichage).

### Infobulle au survol (Tooltip)
```text
Pikachu ★ SHINY ★
Niveau: 24 (Sauvage)
Position: 124, 68, -342
```

---

## 🔔 Système d'Alertes

Lorsqu'un Pokémon entre pour la première fois dans le périmètre du joueur :

- **Pokémon Shiny Détecté** :
  - **Son** : `SoundEvents.UI_TOAST_CHALLENGE_COMPLETE` (volume: 1.0, pitch: 1.2).
  - **Message Actionbar** : `§6★ [Sentinelle Shiny] Pikachu §6★ SHINY détecté à proximité !`
- **Pokémon Normal Traqué Détecté** :
  - **Son** : `SoundEvents.EXPERIENCE_ORB_PICKUP` (volume: 0.75, pitch: 1.0).
  - **Message Actionbar** : `§b⚡ [Radar Cobblemon] §fPikachu §aaperçu à proximité !`

> **Note Anti-Spam** : Un ensemble thread-safe d'UUIDs (`alertedEntities`) retient les entités déjà signalées. Le son ne retentit qu'une seule fois par spécimen.

---

---

## 🎯 Modes de Suivi du Tracker / Radar

Le [PokemonTrackingManager](file:///home/k0bus/dev/minecraft/Cobblemon_JourneyMap/common/src/main/kotlin/fr/k0bus/cobblemon_jei/pokedex/PokemonTrackingManager.kt) propose 4 modes de suivi sélectionnables directement depuis le Pokédex (bouton `🎯 Tracker` ou raccourcis dans la fiche Pokémon) :

1. **Tous les non capturés (`ALL_UNCAUGHT`)** :
   - Surveille tous les Pokémon sauvages dont l'espèce ou la forme régionale/alternative n'est pas encore enregistrée comme capturée dans votre Pokédex.
   - Idéal pour compléter son Pokédex en exploration libre.
   - Les marqueurs sont annotés du tag `§c[Non capturé]`.

2. **Cible unique (`SINGLE`)** :
   - Traque un Pokémon précis (et éventuellement une forme spécifique).
   - Affiche également le calque persistant des zones de spawn sur JourneyMap.
   - Tag de marqueur : `§3[Cible]`.

3. **Lignée évolutive complète (`EVOLUTION_LINE`)** :
   - Analyse récursivement l'arbre d'évolution complet du Pokémon sélectionné (toutes les branches d'ancêtres/pré-évolutions et tous les enfants/évolutions, y compris les branches multiples comme Évoli, Debugant, etc.).
   - Vous alerte dès qu'un membre de la famille apparaît à proximité.
   - Tag de marqueur : `§b[Lignée]`.

4. **Liste personnalisée / Watchlist (`CUSTOM_LIST`)** :
   - Permet d'ajouter ou de retirer n'importe quel Pokémon à une liste de surveillance à l'aide de l'étoile ⭐ sur la fiche du Pokédex.
   - Surveille simultanément tous les spécimens de votre liste.
   - La liste est sauvegardée de manière persistante dans `config/cobblemon_tracker.json`.
   - Tag de marqueur : `§a[Liste]`.

---

## 🔍 Intégration JADE (HUD d'entité)

Le composant [PokemonStatusComponentProvider](file:///home/k0bus/dev/minecraft/Cobblemon_JourneyMap/common/src/main/kotlin/fr/k0bus/cobblemon_jei/compat/jade/PokemonStatusComponentProvider.kt) enrichit le HUD Jade lorsque vous pointez un Pokémon sauvage :

- **Statut Pokédex de l'espèce** :
  - `§aCapturé` (avec icône Pokéball)
  - `§6Vu`
  - `§cNon découvert`
- **Statut de la forme spécifique** (si l'espèce possède plusieurs formes) :
  - `§aForme capturée` / `§6Forme vue` / `§cForme manquante`
- **Statut Shiny** :
  - Mentionne si la variante chromatique est déjà possédée en collection.
- **Inventaire / Stock joueur** :
  - Nombre d'exemplaires de ce Pokémon présents dans l'**Équipe** et dans le **PC** (ex: `Équipe: 1 | PC: 3 (Total: 4)`).
  - Détail si des exemplaires correspondent exactement à la forme ciblée.
- **Badge Cible Traquée** :
  - `🎯 CIBLE TRAQUÉE PAR LE RADAR` affiché en surbrillance dorée si le spécimen regardé correspond à vos critères actuels de tracking.

