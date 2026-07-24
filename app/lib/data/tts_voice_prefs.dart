import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../domain/models/tts_voice.dart';

/// Voz de TTS escolhida pra navegação — compartilhada pelo app inteiro, mesmo
/// padrão de `voice_mode_prefs.dart`. `null` = voz padrão do sistema
/// (comportamento de sempre, sem chamar `setVoice`).
final ValueNotifier<TtsVoice?> selectedTtsVoiceNotifier = ValueNotifier(null);

const _kTtsVoiceKey = 'navigation_tts_voice';

Future<void> loadSelectedTtsVoice() async {
  final prefs = await SharedPreferences.getInstance();
  final salvo = prefs.getString(_kTtsVoiceKey);
  if (salvo != null) {
    selectedTtsVoiceNotifier.value = TtsVoice.fromJson(jsonDecode(salvo) as Map<String, dynamic>);
  }
}

Future<void> saveSelectedTtsVoice(TtsVoice? voz) async {
  final prefs = await SharedPreferences.getInstance();
  if (voz == null) {
    await prefs.remove(_kTtsVoiceKey);
  } else {
    await prefs.setString(_kTtsVoiceKey, jsonEncode(voz.toJson()));
  }
  selectedTtsVoiceNotifier.value = voz;
}
