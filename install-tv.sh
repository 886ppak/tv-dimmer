#!/system/bin/sh
# TV install + clean-up script. Runs ON THE TV (BugJaeger shell, or: adb shell sh /sdcard/Download/install-tv.sh).
# Put these two files in the TV's /sdcard/Download first:
#   dimmer.apk   (this repo's release v1.0)
#   tvhome.apk   (clean launcher, release v1.0; built from github.com/Gathin23/cleanyourtv)
# Only disables packages in the SAFE list below (reversible: pm enable <pkg>). Never touches Wi-Fi setup.
D=/sdcard/Download

echo "== install apps"
pm install -r "$D/dimmer.apk"
pm install -r "$D/tvhome.apk"

echo "== dimmer: overlay permission + start"
appops set com.homelab.dimmer SYSTEM_ALERT_WINDOW allow
am start -n com.homelab.dimmer/.MainActivity >/dev/null 2>&1

echo "== disable safe bloat (reversible)"
for p in \
  com.google.android.feedback \
  com.google.android.partnersetup \
  com.google.android.onetimeinitializer \
  com.google.android.play.games \
  com.google.android.videos \
  com.google.android.youtube.tvmusic \
  com.google.android.tvrecommendations \
  fusion.android.tv.demo \
  com.Smartdevice.emanual \
  com.smartdevice.leanbacklauncher.customizer
do
  pm disable-user --user 0 "$p" >/dev/null 2>&1 && echo "disabled $p"
done

echo "== home screen"
STOCK=$(cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1 | cut -d/ -f1)
case "$STOCK" in
  com.google.android.tvlauncher|com.google.android.apps.tv.launcherx|com.google.android.leanbacklauncher)
    pm disable-user --user 0 "$STOCK" >/dev/null 2>&1 && echo "disabled stock launcher $STOCK" ;;
  *) echo "home is $STOCK (not disabled)" ;;
esac
cmd package set-home-activity --user 0 com.gathin.tvhome/.MainActivity
echo "home now: $(cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1)"
echo "== done. Open Dimmer once with the remote after each restart."
