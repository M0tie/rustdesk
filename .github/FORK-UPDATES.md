# Keeping RustDesk Keeb up to date (no coding needed)

RustDesk Keeb is the official RustDesk app plus the tablet keyboard and trackpad fixes.
It updates itself through GitHub. You only tap "Update" on the tablet.

## What happens automatically

1. Once a day, GitHub checks whether RustDesk has released a new version.
2. If there is one, GitHub adds the tablet fixes to it, builds the app (about an hour),
   and puts it on this page: **https://github.com/M0tie/rustdesk/releases**
3. Obtainium on the tablet notices the new release and offers the update.

This costs nothing: GitHub builds public projects for free.

## One-time setup on the tablet

1. Install **Obtainium** (a free app that installs updates from GitHub):
   - get it from https://github.com/ImranR98/Obtainium/releases (download
     `app-arm64-v8a-release.apk`) or from F-Droid / IzzyOnDroid.
2. Open Obtainium → **Add app** → paste `https://github.com/M0tie/rustdesk` → **Add**.
3. It finds "RustDesk Keeb". If it says an update is available, tap **Install** once
   so Obtainium is linked to the app. Your saved computers and settings stay.
4. In Obtainium → **Settings**: turn on background update checks and allow notifications.

From then on: when you get an Obtainium notification, open it and tap **Update**.

**Without Obtainium:** open https://github.com/M0tie/rustdesk/releases/latest on the
tablet, download the `.apk` file and open it. It installs over the old version.

## If an update fails

GitHub opens an issue named "Auto-update to X.Y.Z failed" on the fork and emails you.

- **Nothing breaks.** The app on your tablet keeps working on the previous version.
  The official RustDesk app from the Play Store is always a fallback.
- **"The build failed"**: often a temporary GitHub problem. Close the issue; the next
  daily check tries again. Or retry now: on github.com/M0tie/rustdesk open **Actions**
  → **Fork auto-update APK** → **Run workflow** → **Run workflow**.
- **"Could not be applied automatically"**: RustDesk changed the same code the fixes
  change. Someone has to edit the code on branch `android-hw-keyboard`. Until then, stay
  on the current version. The next RustDesk release is tried automatically anyway.

## Things to keep

- **The signing key folder** (`signing` next to the project on the PC): back it up.
  GitHub has a copy as a secret, but if you ever need to set up the fork again without
  it, the new app can't update the old one and you'd have to uninstall/reinstall.
- **Your GitHub account and this fork.** Don't delete the fork, the branch
  `android-hw-keyboard`, or the secrets under Settings → Secrets and variables → Actions.
- If GitHub emails that a scheduled workflow was **disabled for inactivity**, open
  Actions → Fork auto-update APK → **Enable workflow**. (The workflow tries to prevent this.)

## Turning it off

Actions → Fork auto-update APK → **⋯** → **Disable workflow**.
