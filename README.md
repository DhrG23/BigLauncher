# Big Launcher 

Open this folder in Android Studio (File > Open). Let Gradle sync (needs internet the first time).

## Test first (emulator, or your phone)
Run it. Android asks "Select a Home app" -> pick Big Launcher.
Tap the clock 5 times -> PIN 1234 -> Admin.
Lockdown only really locks if the app is device owner (see below).

## setup order
1. Factory reset. SKIP adding any Google account.
2. Enable Developer options + USB debugging. Plug into PC.
3. Install: Android Studio Build > Build APK(s), then
   adb install app-debug.apk
4. Make it device owner:
   adb shell dpm set-device-owner com.grandpa.biglauncher/.AdminReceiver
   (Realme error? Developer options: turn on "Disable permission monitoring", retry.)
5. Grant notification access (no Settings needed):
   adb shell cmd notification allow_listener com.grandpa.biglauncher/com.grandpa.biglauncher.NotifService
6. Add the NEW Google account, install WhatsApp, YouTube, Facebook, Instagram, Brave from Play Store.
   Turn Private DNS etc. as you like. Allow phone/contacts permissions when asked.
7. Set up AdGuard DNS, autoplay off, flash-on-call etc. in normal Settings.
8. Open launcher > Admin: choose apps, notifications, add favorites.
9. Admin > ENABLE LOCKDOWN.

## Maintenance (updates, new apps, installing a new APK)
Admin > MAINTENANCE MODE. It lifts restrictions, unhides Play Store, opens Settings.
When done, open the launcher and press ENABLE LOCKDOWN again.

## If something is blocked in kiosk
Pop-up from an app does nothing? Add that app under "Extra helper apps".

## Undo everything
Admin > Remove device owner.

## Pages
Swipe right from Home -> Family numbers. Swipe left -> back Home.
Swipe left from Home -> Widgets (date, messages, SOS, flashlight, volume, info card). Swipe right -> back.
Bottom labels are tappable too.

## Customizing (tap clock 5x, PIN)
- "+ Add app" tile on Home asks for the PIN, then lists installed apps.
  (Apps must already be installed: use Maintenance mode + Play Store first.)
- Admin > Edit look: columns, text size, background, icons, order, rename, colors, widgets, info card.
- Admin > Notification center: per-app allow/block switches and a log of what was blocked.
- Admin > Set SOS contact: used by the SOS widget.
