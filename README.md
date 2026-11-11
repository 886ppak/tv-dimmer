# TV Dimmer (Android TV)

A small Android TV app that darkens the screen with a translucent black layer, controlled over the
local network. Built for dim-at-night viewing on TVs without a usable backlight control.

- `DimService` draws the dim layer (TYPE_APPLICATION_OVERLAY) and runs a tiny HTTP control server on port 8765.
- `MainActivity` is the on-screen control panel (Dim +, Dim -, Dim off, Dim max). The dim is hidden only inside the centre box.
- `BootReceiver` restarts the service after a reboot.
- Requests look like `GET /up?t=TOKEN`, `/down`, `/off`, `/set?v=0..90`, `/status`. The token is shown on the control screen.

## Build (no Gradle)

Uses Android build-tools 34 and a JDK 17 (D8 does not run on newer JDKs):

    aapt2 compile --dir res -o build/res.zip
    aapt2 link -I android.jar --manifest AndroidManifest.xml -R build/res.zip --java build/gen --min-sdk-version 26 --target-sdk-version 31 -o build/base.apk
    javac --release 8 -classpath android.jar -d build/classes $(find src build/gen -name '*.java')
    d8 --min-api 26 --lib android.jar --output build/dex $(find build/classes -name '*.class')
    aapt add build/app.apk classes.dex; zipalign; apksigner sign ...

## Install on a phone (BugJaeger)

1. Download the APK from the latest release:

       https://github.com/886ppak/tv-dimmer/releases/download/v1.0/dimmer.apk

2. In BugJaeger, connect to the TV (wireless debugging on the TV, using its IP address and port).
3. Install `dimmer.apk` from the phone to the TV.
4. In BugJaeger's shell, allow the overlay permission (run once):

       appops set com.homelab.dimmer SYSTEM_ALERT_WINDOW allow

5. Open **Dimmer** on the TV with the remote. Open it again after each restart so the control server starts.

## Install on a TV

    adb install -r dimmer.apk
    adb shell appops set com.homelab.dimmer SYSTEM_ALERT_WINDOW allow
    adb shell am start -n com.homelab.dimmer/.MainActivity

Notes: on Android 12+ the service can only be started from the screen in front (not from ADB or in the background), so open the app once with the remote after a restart if the server is down.
