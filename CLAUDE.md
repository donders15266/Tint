# Tint

Android-app (Kotlin, Jetpack Compose) waarmee de gebruiker het uiterlijk van een Nothing Phone (3a) met Android 16 aanpast, zonder root.

## Onderdelen
- `MainActivity.kt`: instellingen-app met tabbladen Wallpaper, Live, Lijn, Start en Info (incl. Updates).
- `WallpaperArt.kt`: gegenereerde wallpapers (Gloed, Verloop, Dots, Ring) in een accentkleur.
- `LiveWallpaper.kt`: live wallpaper "Tint Dots"; tekent op een eigen thread en geeft de accentkleur via `onComputeColors` door aan Material You.
- `ProgressLine.kt`: voortgangslijn langs de schermrand (NotificationListener + overlay).
- `HomeActivity.kt`, `Library.kt`, `HomeCustom.kt`: eigen startscherm met klokthema's, widgets (AppWidgetHost), lade en App-bibliotheek.
- `Tiles.kt`: tegels voor snelle instellingen.
- `Updater.kt`: updates over the air vanaf GitHub Releases.
- `Ui.kt`: gedeelde stijl (donker, iOS-achtige secties). `TintApp.kt`: instellingen (`Prefs`) en accentkleuren.

## Afspraken
- Alle tekst in de app en de commentaren zijn **Nederlands**. Strakke, goed leesbare donkere stijl; Nothing-accenten (dot-matrix-lettertype Doto, rood/accentkleur).
- Gebruik de bestaande bouwstenen uit `Ui.kt` (`Section`, `Cell`, `Toggle`, `Segmented`, `PrimaryButton`) in plaats van Material-componenten.
- Nieuwe instellingen horen in `Prefs` (`TintApp.kt`), zodat schermen, tegels en services ze delen.
- Verander `versionCode` niet met de hand: GitHub Actions zet die (100 + runnummer).
- minSdk 31, targetSdk/compileSdk 35, JDK 17.

## Bouwen en uitbrengen
- Lokaal: `./gradlew assembleRelease` (APK in `app/build/outputs/apk/release/`).
- Pull requests worden gecontroleerd door `.github/workflows/check.yml`.
- Elke push naar `main` bouwt een ondertekende APK en maakt een GitHub Release `v<versionCode>` met `Tint.apk` (`.github/workflows/release.yml`). De app vindt die in Info › Updates.
- De repository is privé: de app haalt releases op met een alleen-lezen token dat de gebruiker in Info › Updates invult (`Prefs.updateToken`).
- De ondertekensleutel staat als secret `SIGNING_KEYSTORE` (base64) in de repository. Zonder dezelfde sleutel kan de update niet over de geïnstalleerde app heen.
