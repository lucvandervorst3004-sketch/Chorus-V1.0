# Chorus

Chorus is een native Android muziekgame in Kotlin. De app gebruikt Spotify App Remote voor playback en QR-scanning voor Classic Mode.

## Download

Download de nieuwste APK via:

https://github.com/lucvandervorst3004-sketch/Chorus-V1.0/releases/latest

Installatie buiten de Google Play Store kan een Android- of Play Protect-waarschuwing tonen. Dat is normaal voor sideloaded APK-bestanden. Installeer alleen APK's uit de officiële GitHub release van dit project.

## Vereisten

- Android 8.0 of nieuwer
- Officiële Spotify-app geïnstalleerd
- Spotify-account dat is toegelaten in Spotify Developer Mode
- Spotify Premium wordt aanbevolen voor betrouwbare playback

## Security

- Release-builds zijn obfuscated en geoptimaliseerd met R8.
- App-backups zijn uitgeschakeld.
- Cleartext HTTP-verkeer is geblokkeerd.
- Alleen de launcher en Spotify callback activity zijn exported.
- Publiceer nooit de release-keystore of wachtwoorden in deze repository.
