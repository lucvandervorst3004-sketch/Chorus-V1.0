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

## Spotify-account toevoegen

Chorus koppelt met het Spotify-account dat op het toestel in de officiële Spotify-app is ingelogd. Voor accounts buiten het developer-account werkt de koppeling alleen als het Spotify-account is toegevoegd aan de Spotify-appregistratie.

Controleer bij een nieuw toestel of nieuw account:

- Log in op het toestel in de officiële Spotify-app met het juiste Spotify-account.
- Open in het Spotify Developer Dashboard de app met client ID `40aa3f278cf1462c9a1c62b524c92c61`.
- Voeg het Spotify-account toe onder `Settings` -> `Users Management`.
- Development Mode staat maar een beperkt aantal toegelaten Spotify-gebruikers toe; verwijder eerst een oude gebruiker als de lijst vol is.
- Controleer onder Android Packages dat package `com.lucvdvorst.chorus` en de SHA-1 fingerprint van de geinstalleerde APK zijn toegevoegd.
- Controleer dat redirect URI `qrspotify://spotify-auth-callback` in dezelfde Spotify-appregistratie staat.
- Open Chorus daarna opnieuw en tik op `Spotify koppelen`.

Als Chorus een Spotify-authenticatiefout toont, staat in die foutmelding de package name, SHA-1 fingerprint, client ID en redirect URI van de geinstalleerde APK. Die waarden moeten exact overeenkomen met de Spotify Developer Dashboard-instellingen.

## Security

- Release-builds zijn obfuscated en geoptimaliseerd met R8.
- App-backups zijn uitgeschakeld.
- Cleartext HTTP-verkeer is geblokkeerd.
- Alleen de launcher en Spotify callback activity zijn exported.
- Publiceer nooit de release-keystore of wachtwoorden in deze repository.
