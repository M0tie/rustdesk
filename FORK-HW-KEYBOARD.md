# Fork: Android physical-keyboard support (branch `android-hw-keyboard`)

Android client changes so a tablet keyboard (e.g. Galaxy Tab S9 Book Cover Keyboard)
behaves like a native keyboard when controlling a Windows PC. All additions are
self-contained; existing files only get one-line hooks.

| File | Change |
| --- | --- |
| `flutter/android/.../HwKeyboard.kt` | new: window keyboard capture, accessibility capture routing, remaps |
| `flutter/android/.../HwTrackpad.kt` | new: touchpad events re-tagged as mouse, two-finger swipe → wheel |
| `flutter/android/.../KeyCaptureService.kt` + `res/xml/key_capture_service_config.xml` | new: opt-in accessibility key filter |
| `flutter/lib/mobile/widgets/hw_keyboard.dart` | new: options, session hook, soft-keyboard guard, Settings section |
| `AndroidManifest.xml` | `CAPTURE_KEYBOARD` permission, `KeyCaptureService` declaration |
| `MainActivity.kt` | hook lines: method channel, `dispatchKeyEvent` remap, `dispatchTouchEvent` / `dispatchGenericMotionEvent` trackpad |
| `remote_page.dart` | session start/stop hook, unrequested soft-keyboard guard |
| `settings_page.dart` | "Physical keyboard" section |
| `.github/workflows/fork-android-arm64.yml` | arm64-only APK build |

## Rebase on upstream

    git fetch upstream --tags
    git rebase --onto <new-tag> 1.5.3 android-hw-keyboard

## Settings (Settings → Physical keyboard)

- **Use touchpad as mouse** (on): pointer moves the remote mouse, pad click = left,
  two-finger click = right, two-finger swipe = wheel. Pinch stays local zoom.
- **Send system shortcuts to remote** (on): during a session, Android 16 QPR2+ window
  keyboard capture; otherwise routes keys through the accessibility service if enabled.
- **Keyboard capture service**: opens Accessibility settings to enable
  "RustDesk Keyboard Capture". On sideloaded APKs Android first requires
  App info → ⋮ → *Allow restricted settings*.
- **Fallback key remaps** (off): Right Alt → Win, Alt+` → Alt+Tab.
- **Hide unrequested on-screen keyboard** (on).
