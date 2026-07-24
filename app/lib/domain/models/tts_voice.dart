/// Voz do motor de TTS do aparelho (Fase 12.2) — mesmo shape do mapa que
/// `FlutterTts().getVoices` já devolve nos plugins nativos (Android/Windows):
/// `{'name': ..., 'locale': ...}`.
class TtsVoice {
  final String name;
  final String locale;

  TtsVoice({required this.name, required this.locale});

  factory TtsVoice.fromMap(Map<dynamic, dynamic> map) {
    return TtsVoice(
      name: map['name'] as String,
      locale: map['locale'] as String,
    );
  }

  Map<String, String> toMap() => {'name': name, 'locale': locale};

  factory TtsVoice.fromJson(Map<String, dynamic> json) {
    return TtsVoice(name: json['name'] as String, locale: json['locale'] as String);
  }

  Map<String, dynamic> toJson() => {'name': name, 'locale': locale};
}
