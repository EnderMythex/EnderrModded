# EnderrModded

Application Android pour **patcher des applications** avec des bundles de patchs compatibles
[ReVanced](https://github.com/ReVanced) (`.rvp`), directement sur le téléphone.

## Fonctionnalités

1. **Bundle de patchs** : téléchargement de la dernière release d'un dépôt GitHub
   (par défaut `ReVanced/revanced-patches`) ou import d'un fichier `.rvp` local.
2. **Choix de l'app** : depuis les applications installées ou depuis un fichier APK.
3. **Sélection des patchs** compatibles avec le paquet, avec les versions recommandées.
4. **Patch + signature** de l'APK sur l'appareil (journal en direct).
5. **Installation**, enregistrement ou partage de l'APK patché.

## Fonctionnement

| Étape | Composant |
|---|---|
| Chargement des patchs | `loadPatches()` de `revanced-patcher` (DexClassLoader) |
| Décodage / recompilation | `revanced-patcher` (apktool + smali) avec `aapt2` embarqué dans `jniLibs` |
| Écriture de l'APK | `ApkUtils.applyTo` de `revanced-library` |
| Signature | `ApkUtils.signApk` (clé générée dans le stockage privé de l'app) |
| Installation | API `PackageInstaller` |

Code principal : `app/src/main/java/com/endermythex/enderrmodded/`
- `patcher/PatcherEngine.kt` : patch + signature
- `patcher/PatchBundleManager.kt` : import/téléchargement/chargement du bundle
- `installer/Installer.kt` : installation
- `MainViewModel.kt` et `ui/screens/` : interface Jetpack Compose

## Compiler

`revanced-patcher` et `revanced-library` sont publiés sur **GitHub Packages**, qui demande un jeton
même pour les paquets publics.

1. Crée un jeton GitHub (classic) avec le scope `read:packages`.
2. Ajoute dans `~/.gradle/gradle.properties` :
   ```properties
   gpr.user=TON_PSEUDO_GITHUB
   gpr.key=ghp_xxxxxxxxxxxx
   ```
3. Lance `./gradlew assembleDebug` (JDK 17+, Android SDK 36).

Sur GitHub, le workflow `.github/workflows/build.yml` compile l'APK à chaque push et le publie
comme artefact. Si `GITHUB_TOKEN` n'a pas accès aux paquets, ajoute un secret `GPR_TOKEN`.

## Limites actuelles

- APK scindés (`.apks`, `.xapk`, apps installées en split) non supportés.
- Les options de patch utilisent leurs valeurs par défaut.
- Le patch tourne dans le processus de l'app (`largeHeap`) : les très grosses apps peuvent manquer de mémoire.
- L'APK patché est signé avec une autre clé : désinstalle l'app d'origine avant d'installer.

## Licence

GPLv3 (requis par l'utilisation de `revanced-patcher`). Les binaires `aapt2` proviennent de
ReVanced Manager. Utilise cette app uniquement sur des applications que tu as le droit de modifier.
