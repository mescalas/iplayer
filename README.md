# iPlayer — lecteur IPTV pour Android TV

Lecteur IPTV rapide, gratuit et épuré pour Android TV / Google TV / box Android, au style « cinéma » :
images plein écran, titres en grand, accents dorés, bouton lecture rond et suggestions « Vous aimerez aussi ».

## Installation

Téléchargez l'APK depuis la page **Releases** du dépôt (`iPlayer.apk`) puis installez-le sur le téléviseur
(par exemple avec l'application *Downloader* en saisissant le lien direct :
`https://github.com/mescalas/iplayer/releases/latest/download/iPlayer.apk`).

Chaque push sur `main` (ou un commit contenant `[release]`) construit automatiquement un APK signé via GitHub Actions.

## Fonctionnalités

- **Xtream Codes** (serveur + identifiant + mot de passe) et **playlists M3U/M3U8** ; plusieurs comptes.
  Un lien M3U `get.php?username=…&password=…` est automatiquement converti en compte Xtream.
- **TV en direct** : catégories, favoris, chaînes récentes, numéros de chaînes, programme en cours avec
  barre de progression, aperçu vidéo intégré, guide de la chaîne, **replay / catch-up** (Xtream `tv_archive`).
- **Lecteur** (Media3 / ExoPlayer + FFmpeg) : MPEG-TS, HLS, DASH, MP4/MKV ; zapping instantané ▲▼,
  saisie du numéro de chaîne, chaîne précédente, liste des chaînes en surimpression, choix de la piste
  audio, des sous-titres et de la qualité, format d'image, reconnexion automatique, Dolby/DTS via FFmpeg.
- **Films & séries** : affiches, fiches détaillées plein écran (synopsis, casting, note, « Vous aimerez aussi »), saisons et épisodes,
  reprise de lecture, épisode suivant automatique, « Reprendre la lecture » sur l'accueil.
- **Guide TV (EPG)** XMLTV (gzip pris en charge), import en streaming, association automatique des chaînes.
- **Recherche** instantanée (clavier à l'écran + dictée vocale) dans chaînes, films et séries.
- Réglages : mémoire tampon, décodeur audio, format des flux live (TS/HLS), lecture tunnelisée,
  langues préférées, User-Agent, décalage du guide, démarrage sur la dernière chaîne…

## Raccourcis télécommande

| Contexte | Touche | Action |
|---|---|---|
| Direct | ▲ / ▼, CH+ / CH− | Zapper |
| Direct | OK | Infos, puis liste des chaînes |
| Direct | ◀ | Liste des chaînes |
| Direct | ▶ / Menu | Options (audio, sous-titres, format, favori) |
| Direct | 0-9 | Aller au numéro de chaîne |
| Films / séries | OK | Lecture / pause |
| Films / séries | ◀ / ▶ | Reculer / avancer (maintenir pour accélérer) |
| Films / séries | ▼ / Menu | Options |
| Listes | OK maintenu | Ajouter / retirer des favoris |

## Technique

Kotlin, Jetpack Compose, Room (+ Paging), Media3 ExoPlayer, OkHttp, Coil 3. `minSdk 21`, `targetSdk 35`.
Build : `./gradlew assembleRelease`.
