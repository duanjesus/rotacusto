import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../domain/navigation/voice_mode.dart';

/// Modo de voz atual da navegação — compartilhado pelo app inteiro, mesmo
/// padrão de `auth_controller.dart`. Default [NavigationVoiceMode.tudo]
/// preserva o comportamento de sempre pra quem nunca mexeu na opção.
final ValueNotifier<NavigationVoiceMode> navigationVoiceModeNotifier =
    ValueNotifier(NavigationVoiceMode.tudo);

const _kVoiceModeKey = 'navigation_voice_mode';

Future<void> loadNavigationVoiceMode() async {
  final prefs = await SharedPreferences.getInstance();
  final salvo = prefs.getString(_kVoiceModeKey);
  if (salvo != null) {
    navigationVoiceModeNotifier.value = NavigationVoiceMode.values.byName(salvo);
  }
}

Future<void> saveNavigationVoiceMode(NavigationVoiceMode modo) async {
  final prefs = await SharedPreferences.getInstance();
  await prefs.setString(_kVoiceModeKey, modo.name);
  navigationVoiceModeNotifier.value = modo;
}
