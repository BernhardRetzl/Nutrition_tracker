# Food Log for Android

A small Android app with offline food logging for recurring foods and daily weights. Add **Milk** once, enter **250 g** today, and tomorrow the same food is ready with an empty entry showing **250** in grey as a reference. All valid changes save automatically in a local SQLite database.

## Use

1. Install `app/build/outputs/apk/debug/app-debug.apk` on an Android 8.0 or newer phone. Android may ask you to allow installation from the app used to open the APK.
2. Tap **+ Food**, then enter its total weight for the day in grams. Decimal points and commas are supported, up to three decimal places. Blank means not eaten; clearing a weight removes that day's entry.
3. Tap **Email** once to set up your Gmail address, recipient, and Google app password (see below).
4. Tap **Send** to email the log directly through Gmail. The CSV contains Date, Recipe, Food, and Grams for the selected day. Standalone foods have an empty Recipe column. Wait for the success or error message; all your logged data remains saved.
5. Tap the **date** to view, correct, or resend a previous day. Removing a food hides it immediately and deletes its weight for the selected date from the log and subsequent exports. Other dates remain unchanged.

The weight is a daily total per food. If you drink another 100 g of milk after entering 250 g, tap **+** beside Milk and enter 100; the total becomes 350 g. You can also edit the total directly. Logging needs no account or network. Direct sending requires a Gmail account and a Google app password. Logs remain on the phone; uninstalling the app removes them. CSV exports can be kept externally, but this version has no import/restore function.

## Automatic Android sync (version 1.8)

After signing in and completing one Sync to link the phone, Android syncs automatically when opened, about two seconds after edits, and every 15 seconds while the main screen is active. The status below the date shows progress, pending changes, or a request to sign in/review conflicting edits. The Sync button remains available for those actions. Closing/backgrounding the app pauses periodic requests; a request already in progress may finish. Offline changes remain saved locally and retry when the app is open again. This version does not schedule background sync while closed.

Automatic requests reuse the existing website session over HTTPS. Expired sessions require signing in again. Unrelated edits merge automatically; conflicting edits require review. Changes entered while an upload is running are retained and sent on the next pass. Tests cover upload/download, in-flight edits, conflicts, wrong accounts, offline retry, and waiting for first sign-in. Live session reuse and cross-device sync still require real-phone verification.

## Android sign-in fix (version 1.7.1)

The Sync window now keeps the website's login page open instead of immediately reporting “Sync incomplete”. Tap **Sign in with ChatGPT** and use the same account as the browser. Sync continues after sign-in; an authenticated API response is required before merging or uploading logs. Navigation during sign-in cancels stale response polling. Install the updated APK over the existing app to preserve logs.

Regression tests cover the login page at `/phone`, returning from sign-in, unauthenticated API responses, malformed responses, and successful authenticated responses. Actual identity-provider sign-in still needs verification on a phone.

## Browser + Android sync (version 1.7)

Website: https://food-log-br-sync-20261117.flutedhummingbird4.chatgpt.site
Web source: `/media/br/TB16/Nutrition_tracker/web/food-log-sync`
APK: `/media/br/TB16/Nutrition_tracker/app/build/outputs/apk/debug/app-debug.apk`

Install the APK over your existing installation. Open the private website on your computer and sign in with your ChatGPT account. On Android, tap **Sync** and sign in with the same account in the sync window. Your first successful sync uploads your existing foods, recipes, and all recorded dates. A different account cannot silently replace a previously linked phone's data.

The Android version keeps logging offline. In version 1.8 it syncs automatically while open after the first successful sign-in and Sync; older versions use manual Sync. The browser saves valid edits on this device immediately and syncs every 15 seconds while visible, or when you press Sync. Wait for “Synced” before switching devices. Loading the website itself initially needs internet; it is not an installable offline PWA. Gmail sending remains in Android; the website downloads a CSV.

Sync includes only food/recipe templates and logged weights. It does not upload Gmail addresses, app passwords, or email settings. Stable UUIDs avoid ID collisions. A three-way merge combines unrelated changes; competing edits and removal-versus-edit conflicts require choosing the phone/browser version for the conflicting records. The server uses revision checks to reject stale writes. Failed imports roll back in a SQLite transaction. Database version 3 adds sync IDs without removing existing logs.

Sign-in uses the private site's normal authentication in Android WebView; no API keys are embedded in the APK. Phone authentication and actual cross-device use still need verification on a real phone. Some identity providers may restrict embedded sign-in; if yours does, do not bypass its security warning—report it so the authentication flow can be adapted.

## Add another portion (version 1.6)

Every food and recipe ingredient row has a **+** button. Enter the extra grams in the dialog and tap Add: 125 g + 68 g becomes 193 g in the same row. Decimal commas and points work. The total saves automatically and is used in the email. Cancel leaves the current total unchanged. A blank entry starts from zero; grey previous-day references are never included. Each recipe ingredient keeps its own total, separate from standalone foods.

## Removal (version 1.5)

Removing a food or recipe deletes its logged weights for the selected day and stops it appearing on future days. Other dates remain intact. Removal and weight deletion happen together in a database transaction. Already sent emails or attachments created before removal cannot be changed; future exports use the corrected log.

## One-button Gmail sending (version 1.4)

1. Enable **2-Step Verification** in the Google account you want to send from.
2. Open [Google App passwords](https://myaccount.google.com/apppasswords), create one named Food Log, and copy it. Google documents eligibility and limitations in its [app-password guide](https://support.google.com/accounts/answer/185833). Some managed accounts, Advanced Protection, or security-key-only configurations may not offer app passwords.
3. In Food Log, tap **Email**. Enter your full Gmail/Google Workspace address, the recipient address (which may be your own), and the 16-character app password. Spaces in Google's displayed password are accepted. Use the app password, not your regular Google password.
4. Tap **Save**. Then **Send** emails the selected day's CSV without opening another app. Setup saves credentials but sends no test email.

The password is encrypted with an AES-GCM key held by Android Keystore, and is excluded from cloud backup/device transfer. Credentials are never embedded in the APK or logged. The app connects directly to Gmail's SMTP server over TLS on port 465 with certificate/hostname verification. No custom server is involved.

**Sending…** disables the button until the attempt finishes. Gmail acceptance is reported; it does not guarantee delivery to the recipient's inbox. Keep the app open until it reports a result. The in-progress attempt survives screen rotation within the app process. If the process is killed or the connection fails ambiguously, check Gmail Sent before retrying; the app never automatically retries. Your logs are never cleared after sending.

To change account settings, tap Email; a blank password keeps the saved password for the same sender. **Forget Gmail connection** removes the local credentials; revoke the app password in your Google account if desired. **Share CSV using another app** remains available inside Email as a manual fallback.

## Compact layout (version 1.3)

The main screen has a date picker and Email button at the top, one-line food/ingredient rows, and a single bottom bar for **+ Food**, **+ Recipe**, and **Send**. Send shows the number of logged foods/ingredients. Tap the date to access history, then Today to return. Recipe headers still expand/collapse. Long food names wrap to two lines; long-press a name to see its full text.

## Previous-day reference weights (version 1.2)

An empty food or ingredient field shows the previous calendar day's weight in grey, as a reference. Long-press the weight field for a reminder that grey weights are not logged. Type a weight to log it for the selected day. Clearing the field removes that day's entry and reveals the grey hint again. Grey references never count as logged foods and never appear in CSV exports; Send still requires at least one weight entered for that date.

References are separate for each standalone food and each recipe ingredient. If there was no entry on the previous calendar day, the field shows a dash; older weights are not substituted. History uses the day before the selected date as its reference. No data is copied or migrated for this feature.

## Recipes (version 1.1)

Tap **+ Recipe**, name it (for example **Smoothie**), then tap **Add ingredient** inside the expanded recipe. Type a new food name or pick an autocomplete suggestion from your existing foods. Tap the recipe name to expand or collapse its ingredients and enter each ingredient's daily grams. Recipe and ingredient lists persist; a new day starts with empty entries and grey reference weights from the previous calendar day.

Ingredient weights are independent: milk in Smoothie, milk in Porridge, and standalone milk each have their own entry. Enter only the amount used in that location to avoid counting it twice. There is no portion scaling or recipe total to enter. Empty ingredients are omitted from exports.

Use **×** on an ingredient to remove it from that recipe, or **×** beside the recipe name to delete the recipe. Deletion immediately removes the recipe and all its ingredient weights for the selected date from the screen and subsequent exports. Other dates retain their recorded weights and recipe names. Foods or recipes marked “removed” by older versions now have a delete button too, so their selected-day entries can be cleared. Recreating a recipe with the same name starts a fresh template.

Install this APK **over the existing app** to update it. Keep the existing installation: uninstalling erases local data. Database version 2 adds recipes without deleting or rewriting version 1 food logs. The package ID and signing key remain unchanged in this workspace.

## Build

Open this folder in Android Studio, or use JDK 17+, Android SDK platform 34, and the Gradle wrapper:

```sh
# Set sdk.dir in local.properties to your Android SDK path, or set ANDROID_HOME.
./gradlew assembleDebug testDebugUnitTest lintDebug
```

The debug APK is suitable for personal testing, not a Play Store release. SDK/target 34 matches the SDK installed in this workspace. Play Store publication would require current target requirements and a private release signing key.

Tests cover daily separation and persistence, deletion preserving history, clearing and replacing totals, duplicate food names, decimal validation, CSV escaping, version 1 database migration, recipe/standalone ingredient independence, recipe deletion, expand/collapse behavior, and both standalone and recipe add → log → share attachment flows. Robolectric exercises Android database and activity behavior on the JVM; MIME attachment and send-controller tests use simulated delivery, covering duplicate taps, authentication failure, connection failure and interrupted attempts. Live Gmail delivery and Android Keystore behavior still need real-phone verification with the user’s credentials.

Attachments use Android's [FileProvider sharing mechanism](https://developer.android.com/training/secure-file-sharing) with temporary read permission. Each send creates a separate snapshot so later edits do not change an attachment already open in an email draft.
