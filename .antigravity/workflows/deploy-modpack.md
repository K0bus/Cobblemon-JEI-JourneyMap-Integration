# Workflow Déploiement Local dans le Modpack

Ce workflow s'applique dès que l'utilisateur demande :
- *"build et dépose dans mon modpack"*
- *"déploie dans mon modpack"*
- *"teste en local"* / *"mets à jour mon modpack"*
- ou en étape finale post-release.

---

## 1. Informations de l'environnement Modpack

- **Launcher** : PrismLauncher
- **Instance** : `All the Mons - ATMons`
- **Version MC / Loader** : Minecraft 1.21.1 - NeoForge
- **Répertoire de destination** :
  ```
  /home/k0bus/.local/share/PrismLauncher/instances/All the Mons - ATMons/minecraft/mods/
  ```

---

## 2. Procédure étape par étape

### Étape 1 : Build de l'artefact NeoForge
Si le jar n'a pas encore été construit pour la version courante (définie dans `gradle.properties`) :
```bash
./gradlew :neoforge:build
```
*(Ou `./gradlew build` si les deux plateformes doivent être vérifiées).*

### Étape 2 : Identification du JAR produit
Le fichier généré se situe dans :
```
neoforge/build/libs/cobblemon-jei-neoforge-${mod_version}.jar
```
Vérifier son existence et sa taille :
```bash
ls -la neoforge/build/libs/cobblemon-jei-neoforge-*.jar
```

### Étape 3 : Nettoyage de l'ancienne version dans l'instance
Pour éviter les crashs de Minecraft au chargement dus à deux versions du même mod ID :
```bash
rm -f "/home/k0bus/.local/share/PrismLauncher/instances/All the Mons - ATMons/minecraft/mods/cobblemon-jei-neoforge-"*.jar
```

### Étape 4 : Copie du nouveau JAR
```bash
cp neoforge/build/libs/cobblemon-jei-neoforge-*.jar "/home/k0bus/.local/share/PrismLauncher/instances/All the Mons - ATMons/minecraft/mods/"
```

### Étape 5 : Vérification
Confirmer que le mod est bien installé dans le dossier de l'instance :
```bash
ls -la "/home/k0bus/.local/share/PrismLauncher/instances/All the Mons - ATMons/minecraft/mods/" | grep "cobblemon-jei"
```

---

## 3. Règle d'intégration avec la release
Après chaque release officielle validée par l'utilisateur (workflow `release.md`), proposer ou exécuter automatiquement le déploiement du nouveau jar NeoForge dans ce modpack afin de tester immédiatement en jeu.
