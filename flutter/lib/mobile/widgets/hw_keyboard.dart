import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:settings_ui/settings_ui.dart';

import '../../common.dart';
import '../../consts.dart';
import '../../models/platform_model.dart';

// Android client: physical keyboard support while controlling a remote peer.
// Native side: flutter/android/.../HwKeyboard.kt and KeyCaptureService.kt.

const kOptionHwKeyboardCapture = 'android-hw-keyboard-capture';
const kOptionHwKeyboardRemap = 'android-hw-keyboard-remap';
const kOptionHwKeyboardHideSoft = 'android-hw-keyboard-hide-soft';

bool _hwKeyboardOption(String key, bool defaultValue) {
  final v = bind.mainGetLocalOption(key: key);
  return v.isEmpty ? defaultValue : v == 'Y';
}

bool get hwKeyboardCaptureEnabled =>
    _hwKeyboardOption(kOptionHwKeyboardCapture, true);
bool get hwKeyboardRemapEnabled =>
    _hwKeyboardOption(kOptionHwKeyboardRemap, false);
bool get hwKeyboardHideSoftEnabled =>
    _hwKeyboardOption(kOptionHwKeyboardHideSoft, true);

void hwKeyboardSessionChanged(bool active) {
  if (!isAndroid) return;
  gFFI.invokeMethod('hw_keyboard_session', {
    'active': active,
    'capture': hwKeyboardCaptureEnabled,
    'remap': hwKeyboardRemapEnabled,
  });
}

/// True if an input method became visible although no text field has focus,
/// e.g. Gboard's toolbar reacting to a physical keyboard.
bool hwKeyboardIsUnrequestedSoftKeyboard() {
  if (!isAndroid || !hwKeyboardHideSoftEnabled) return false;
  final context = FocusManager.instance.primaryFocus?.context;
  return context == null ||
      (context.widget is! EditableText &&
          context.findAncestorWidgetOfExactType<EditableText>() == null);
}

void hwKeyboardHideSoftKeyboard() {
  SystemChannels.textInput.invokeMethod('TextInput.hide');
  gFFI.invokeMethod("enable_soft_keyboard", false);
  SystemChrome.setEnabledSystemUIMode(SystemUiMode.manual, overlays: []);
}

class HwKeyboardSettings extends StatefulWidget {
  const HwKeyboardSettings({Key? key}) : super(key: key);

  @override
  State<HwKeyboardSettings> createState() => _HwKeyboardSettingsState();
}

class _HwKeyboardSettingsState extends State<HwKeyboardSettings>
    with WidgetsBindingObserver {
  bool _serviceEnabled = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _refreshService();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) _refreshService();
  }

  Future<void> _refreshService() async {
    final enabled = await gFFI
        .invokeMethodWithResult<bool>('hw_keyboard_capture_service_enabled');
    if (mounted) setState(() => _serviceEnabled = enabled == true);
  }

  Future<void> _set(String key, bool value) async {
    await bind.mainSetLocalOption(key: key, value: value ? 'Y' : 'N');
    setState(() {});
  }

  Widget _title(String title, String tip) =>
      Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Text(translate(title)),
        Text('* ${translate(tip)}',
            style: Theme.of(context).textTheme.bodySmall),
      ]);

  @override
  Widget build(BuildContext context) {
    return SettingsSection(
      title: Text(translate('Physical keyboard')),
      tiles: [
        SettingsTile.switchTile(
          initialValue: hwKeyboardCaptureEnabled,
          title: _title('Send system shortcuts to remote',
              'Win, Alt+Tab, Ctrl+Esc etc. go to the remote computer during a session. Works natively on Android 16 QPR2+; otherwise also enable the accessibility service below.'),
          onToggle: (v) => _set(kOptionHwKeyboardCapture, v),
        ),
        SettingsTile(
          title: _title('Keyboard capture service',
              _serviceEnabled ? 'Enabled' : 'Disabled. Tap to open Accessibility settings and enable "RustDesk Keyboard Capture".'),
          leading: Icon(_serviceEnabled
              ? Icons.check_circle_outline
              : Icons.keyboard_alt_outlined),
          onPressed: (context) => gFFI.invokeMethod(
              AndroidChannel.kStartAction,
              'android.settings.ACCESSIBILITY_SETTINGS'),
        ),
        SettingsTile.switchTile(
          initialValue: hwKeyboardRemapEnabled,
          title: _title('Fallback key remaps',
              'Right Alt sends Win, Alt+` sends Alt+Tab. For keys the system still swallows.'),
          onToggle: (v) => _set(kOptionHwKeyboardRemap, v),
        ),
        SettingsTile.switchTile(
          initialValue: hwKeyboardHideSoftEnabled,
          title: _title('Hide unrequested on-screen keyboard',
              'During a session, close the on-screen keyboard or input method toolbar when it appears without a text field in focus.'),
          onToggle: (v) => _set(kOptionHwKeyboardHideSoft, v),
        ),
      ],
    );
  }
}
