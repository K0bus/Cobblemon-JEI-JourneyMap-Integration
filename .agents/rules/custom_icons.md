# Règle : Utilisation des Icônes Graphiques et Proscription de l'UTF-8

## Principes Directeurs
1. **Interdiction des caractères UTF-8 pour les icônes de boutons** :
   - Ne pas utiliser d'émojis ou symboles Unicode comme `⚙`, `🏆`, `⚔`, `🏕`, `🎯`, `★` pour les boutons d'action ou d'onglets principaux lorsque des composants visuels propres sont requis.
   - Les boutons d'interface doivent intégrer de véritables textures d'icônes PNG monochromes (blanches sur fond transparent) avec coloration dynamique et bordures soignées selon la DA du mod.

2. **Source des icônes recommandées : Game-Icons.net** :
   - Pour toute nouvelle fonctionnalité nécessitant une icône, utiliser ou proposer des icônes issues de [https://game-icons.net/](https://game-icons.net/).
   - Format requis : PNG transparent, motif blanc monochrome (Foreground: #ffffff, Background: transparent), dimensions standard 512x512 ou 256x256, redimensionné de manière nette (12x12 à 16x16) lors du rendu Minecraft.
   - Si une nouvelle icône est nécessaire, toujours demander à l'utilisateur ou lui proposer des choix concrets trouvés sur game-icons.net.

3. **Emplacement des textures dans le projet** :
   - Toutes les icônes d'interface doivent être stockées sous `common/src/main/resources/assets/cobblemon_jei/textures/gui/icons/`.
