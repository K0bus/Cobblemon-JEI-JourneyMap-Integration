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
| **Validation Shiny** | `pokemon.shiny && pokemon.isWild()` | Les Pokémon des dresseurs/joueurs ne déclenchent pas la sentinelle sauvage. |

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

## 🧹 Cycle de Vie & Zéro Fuite Mémoire

1. **Déplacement du Pokémon** : Si les coordonnées de bloc changent, `marker.setPoint(...)` réactualise la position sans détruire le marqueur.
2. **Despawn / Capture / Mort** : Si l'UUID n'est plus présent dans la liste des entités du monde client, le marqueur est immédiatement retiré de JourneyMap via `api.remove(marker)` et évincé de la mémoire.
3. **Changement de Dimension** : Si `currentDim != lastDimension`, `clearRadar()` vide l'ensemble des marqueurs et réinitialise les structures de données.
4. **Fermeture du Calque** : `clearTrackedOnly()` retire les marqueurs des cibles ordinaires mais **préserve les marqueurs des Pokémon Shiny** tant qu'ils sont encore présents dans le monde.
