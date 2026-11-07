---
trigger: always_on
---

# Règle : Internationalisation Stricte (Zéro Texte en Dur)

## 1. Interdiction Absolue du Texte en Dur
- **AUCUNE chaîne de caractères visible par l'utilisateur ne doit être codée en dur dans le code source** (Kotlin, Java, templates, etc.).
- Cette règle s'applique obligatoirement à :
  - Tous les composants d'interface graphique (boutons, titres, onglets, sous-onglets, badges, libellés, menus déroulants, compteurs, textes de pied de page).
  - Toutes les infobulles (tooltips simples ou multilignes).
  - Tous les messages de chat, notifications HUD, alertes et toasts.
  - Tous les overlays JourneyMap, bannières et indicateurs radar / boussole.
  - Tous les libellés de données dynamiques visibles (noms de boîtes PC, conditions d'évolution, notes d'apparition, explications de PokéSnacks, métiers de pâturage, structures archéologiques).

## 2. Utilisation Systématique de `Component.translatable`
- Tout texte affiché dans Minecraft doit obligatoirement utiliser l'API officielle :
  ```kotlin
  Component.translatable("gui.cobblemon_jei.votre_cle_ici")
  // ou avec arguments de formatage :
  Component.translatable("gui.cobblemon_jei.votre_cle_ici", arg1, arg2)
  ```
- Pour les enums (ex: onglets, filtres, modes), stocker la clé de traduction (`translationKey` / `labelKey`) et exposer un getter dynamique :
  ```kotlin
  val label: String get() = Component.translatable(labelKey).string
  ```
- Pour les libellés combinés ou préfixés, utiliser des placeholders dans le fichier de langue (`%s`, `%d`, etc.) plutôt que de la concaténation directe de texte en dur dans le code.

## 3. Convention de Nommage des Clés de Traduction
Adopter une arborescence stricte et cohérente selon le domaine :
- `gui.cobblemon_jei.pokedex.*` : Écrans et filtres du Pokédex
- `gui.cobblemon_jei.detail.*` : Panneau d'inspection et onglets de détails
- `gui.cobblemon_jei.tracker.*` : Radar, système de suivi et stockage PC
- `gui.cobblemon_jei.category.*` : Catégories et intégrations JEI/REI/EMI
- `gui.cobblemon_jei.evo.*` : Conditions et variantes d'évolution
- `gui.cobblemon_jei.fossil.*` : Objets fossiles et structures archéologiques
- `gui.cobblemon_jei.snack.*` : PokéSnacks et assaisonnements
- `chat.cobblemon_jm.*` : Messages envoyés dans le chat Minecraft
- `config.jade.*` & `jade.*` : Intégration Waila / Jade
- `key.*` : Raccourcis clavier

## 4. Maintenance Immédiate des Fichiers Lang (Parité 100%)
- Lors de l'ajout ou de la modification de N'IMPORTE QUELLE clé de traduction au cours du développement :
  - **Mettre à jour immédiatement et simultanément l'ensemble des 8 fichiers de langue** sous `common/src/main/resources/assets/cobblemon_jei/lang/` :
    1. `en_us.json` (Anglais - Référence)
    2. `fr_fr.json` (Français - Référence)
    3. `de_de.json` (Allemand)
    4. `es_es.json` (Espagnol)
    5. `pt_br.json` (Portugais Brésilien)
    6. `ru_ru.json` (Russe)
    7. `ja_jp.json` (Japonais)
    8. `zh_cn.json` (Chinois Simplifié)
  - Conserver strictement les codes de couleur Minecraft (`§a`, `§c`, `§e`, `§6`, `§r`, etc.) et les spécificateurs de formatage (`%s`, `%d`).
  - Vérifier systématiquement que le nombre total de clés reste rigoureusement identique entre tous les fichiers JSON (0 clé manquante, 0 clé orpheline).
