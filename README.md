# EnderrModded

App Android pour **patcher automatiquement** des applications avec des patchs compatibles
[ReVanced](https://github.com/ReVanced) (`.rvp`), à la manière de ReVanced Manager.

## Fonctionnalités

- **Mode full auto** : un bouton → patchs à jour → version recommandée → téléchargement de l'APK
  officiel → vérification de la signature → fusion des splits → patch → signature → installation.
- **Sources d'APK fiables**, dans l'ordre (configurable) :
  1. l'app déjà installée depuis le Play Store (si la version correspond) → **100 % automatique** ;
  2. **APKMirror** (APK vérifiés : même signature que le développeur). L'app trouve seule la bonne
     version et la bonne variante ; la dernière étape s'ouvre dans un **navigateur intégré** car le site
     exige une vérification anti-robot (Cloudflare) qu'EnderrModded ne contourne pas. Un appui suffit,
     puis le patch reprend automatiquement.
- **Vérification de signature** : l'APK téléchargé doit avoir le certificat de l'app installée, ou
  celui enregistré la première fois. Sinon le patch est **bloqué**.
- **APK scindés** (`.apks`, `.xapk`, `.apkm`, apps installées en splits) fusionnés automatiquement.
- **Mises à jour** :
  - patchs mis à jour automatiquement depuis l'**API ReVanced** (source par défaut, comme ReVanced
    Manager) ou depuis un dépôt GitHub publiant des `.rvp` ;
  - vérification en arrière-plan (6/12/24 h) des nouvelles versions pour les apps patchées, avec
    notification ou **reconstruction automatique** ;
  - mise à jour d'EnderrModded lui-même via les releases GitHub.
- **Réglages** : source des patchs, pré-releases, sources d'APK, vérification de signature, installation
  auto, fréquence des vérifications, thème, couleurs dynamiques, export de la clé de signature, cache…
- **Mode manuel** : patcher un fichier APK/bundle ou une app installée en choisissant les patchs.

## Architecture

| Dossier | Rôle |
|---|---|
| `core/` | Module Kotlin/JVM testable : source APKMirror, sources de patchs (API ReVanced / GitHub), fusion des splits (ARSCLib), releases GitHub, versions |
| `app/.../pipeline/` | Pipeline full auto (`AutoPatcher`), récupération + vérification des APK (`ApkProvider`) |
| `app/.../patcher/` | Patch et signature (`revanced-patcher`, `revanced-library`, `aapt2` embarqué) |
| `app/.../work/` | Tâches de fond WorkManager : patch (`PatchWorker`), vérifications (`UpdateWorker`), notifications |
| `app/.../update/` | Mise à jour de l'app |
| `app/.../data/` | Réglages, apps patchées, sélections de patchs, certificats épinglés, bundle de patchs |
| `app/.../ui/` | Interface Jetpack Compose |

## Compiler

`revanced-patcher` et `revanced-library` sont sur **GitHub Packages** (jeton requis, même en lecture).

- **Sur GitHub** : rien à faire, le workflow utilise le jeton automatique `GITHUB_TOKEN`.
- **Sur ton PC** : crée un jeton (classic) avec le scope `read:packages`, puis dans
  `~/.gradle/gradle.properties` (jamais dans le dépôt) :
  ```properties
  gpr.user=TON_PSEUDO
  gpr.key=ghp_...
  ```
  puis `./gradlew assembleDebug`.

Tests : `./gradlew :core:test` (ajoute `NETWORK_TESTS=1` pour tester les vraies sources).

## Publier une version (mise à jour automatique de l'app)

1. Ajoute une fois les secrets du dépôt (Settings → Secrets and variables → Actions) :
   `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
   Garde toujours la **même clé** : sinon Android refusera les mises à jour.
2. Pousse un tag : `git tag v0.2.0 && git push origin v0.2.0`.
3. Le workflow `Release` publie l'APK ; les apps installées proposent la mise à jour.

## Limites

- APKMirror peut changer : le job CI `sources` vérifie que la recherche de version/variante fonctionne.
- La reconstruction 100 % en arrière-plan n'est possible que si la bonne version de l'app d'origine est
  installée ; sinon une notification demande de terminer le téléchargement dans le navigateur intégré.
- Uptodown n'est pas utilisé : ses téléchargements sont protégés par un captcha.
- Le patch tourne sur le téléphone (`largeHeap`) : les très grosses apps demandent beaucoup de mémoire.
- Certaines apps patchées (ex. YouTube) nécessitent **GmsCore/MicroG** pour la connexion au compte Google.
- L'APK patché est signé avec la clé d'EnderrModded : désinstalle l'app d'origine si le paquet n'est pas renommé.

## Licence

GPLv3 (requis par `revanced-patcher`). Binaires `aapt2` issus de ReVanced Manager.
Utilise cette app uniquement sur des applications que tu as le droit de modifier.
