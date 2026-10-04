# iPlayer — lecteur IPTV pour Android TV

Lecteur IPTV rapide, gratuit et épuré (design inspiré de tvOS) pour Android TV / Google TV / box Android.

## Installation

Téléchargez l'APK depuis la page **Releases** du dépôt (`iPlayer.apk`) puis installez-le sur le téléviseur
(par exemple avec l'application *Downloader* en saisissant le lien direct :
`https://github.com/mescalas/iplayer/releases/latest/download/iPlayer.apk`).

Chaque push construit un APK signé via GitHub Actions (téléchargeable dans l'onglet *Actions*) ; seul un push sur `main`
publie une release, et donc une mise à jour pour les téléviseurs.

### Mises à jour

Une fois installée, l'application se met à jour toute seule : à l'ouverture (au plus toutes les 3 h) elle consulte
la dernière release GitHub et propose « Mettre à jour » si sa version est plus récente ; l'APK est téléchargé puis
installé par le système. Vérification manuelle dans **Réglages › À propos**.
La première fois, Android TV demande d'autoriser iPlayer à « installer des applications inconnues ».
Sur Android 12+, les mises à jour suivantes peuvent s'installer sans confirmation.
Les titres des commits depuis la release précédente servent de notes de version affichées dans l'app.

## Fonctionnalités

- **Xtream Codes** (serveur + identifiant + mot de passe) et **playlists M3U/M3U8** ; plusieurs comptes.
  Un lien M3U `get.php?username=…&password=…` est automatiquement converti en compte Xtream.
- **TV en direct** : catégories, favoris, chaînes récentes, numéros de chaînes, programme en cours avec
  barre de progression, aperçu vidéo intégré, guide de la chaîne, **replay / catch-up** (Xtream `tv_archive`).
- **Lecteur** (Media3 / ExoPlayer + FFmpeg) : MPEG-TS, HLS, DASH, MP4/MKV ; zapping instantané ▲▼,
  saisie du numéro de chaîne, chaîne précédente, liste des chaînes en surimpression, choix de la piste
  audio, des sous-titres et de la qualité, format d'image, reconnexion automatique, Dolby/DTS via FFmpeg.
- **Films & séries** : affiches, fiches détaillées (synopsis, casting, note), saisons et épisodes,
  reprise de lecture, épisode suivant automatique, « Reprendre la lecture » sur l'accueil.
- **Guide TV (EPG)** XMLTV (gzip pris en charge), import en streaming, association automatique des chaînes.
- **Titres épurés** : les préfixes fournisseur (« FR| », « [EN] »), années, qualités, codecs, langues,
  « S01E02 », émojis et symboles décoratifs sont retirés des noms de films, séries, épisodes, chaînes et
  catégories. Les informations utiles deviennent de petites pastilles (4K, HDR, Dolby Vision, VOSTFR, MULTI ;
  HD / FHD / 4K pour les chaînes) et les noms en MAJUSCULES sont remis en casse normale.
- **Sous-titres** au style tvOS (texte fin sur fond translucide arrondi), personnalisables : taille, couleur,
  fond, police et position — dans Réglages ▸ Sous-titres (avec aperçu) ou en direct pendant la lecture
  (Options ▸ Sous-titres ▸ Apparence, ◀ ▶ pour ajuster). Sous-titres image (PGS/DVB) pris en charge.
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
