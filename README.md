# Nexiom Mobile

The household's phone app for their Nexiom box: the dashboard (what needs attention, the status
strip, the tiles and the rings, live), and Settings, Files and the services' web pages inside the
app, already signed in. Tiles open a service's official app when the phone has it (Immich,
Bitwarden, Home Assistant, Music Assistant).

What the app is and why lives in the product brief (a Claude Doc); the box's side is Nexiom
Server's `docs/architecture.md`, "Phone app".

## Layout

- `shared/`: Kotlin Multiplatform. `commonMain` holds the API client (`api/`), the app's state
  (`AppModel`) and the Compose UI (`ui/`); `androidMain` what only Android can do: finding boxes
  over mDNS, the web view, opening other apps. Every string is in
  `commonMain/composeResources/values/strings.xml`.
- `androidApp/`: the Android application around it.

The iPhone targets join `shared/` once the Android app is proven.

## Build

Needs Android Studio (for its JDK and the Android SDK).

```sh
./build.sh                          # release APK, debug-signed unless a key is set
PHONE=<adb serial> ./build.sh install
./gradlew :shared:testAndroidHostTest
```

The app targets SDK 36, not 37: Android 17 blocks local-network access for apps targeting 37
until they ask for `ACCESS_LOCAL_NETWORK` at runtime.

## Releases

`git tag vX.Y.Z && git push --tags` builds a signed APK in Actions (with the `KEYSTORE_BASE64`,
`KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD` secrets) and attaches it to the release as
`nexiom-X.Y.Z.apk`, which the box's phone setup installs. Locally, a gitignored
`signing.properties` (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`) signs the same way.

## Licences

Atkinson Hyperlegible is © Braille Institute of America, under the SIL Open Font License
(`licenses/AtkinsonHyperlegible-OFL.txt`).
