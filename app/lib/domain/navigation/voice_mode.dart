import 'package:flutter/material.dart';

/// Modo de voz durante a navegação (Fase 12) — controla quais dos pontos de
/// fala (`.speak`) tocam: instrução de virada só em [tudo]; alerta de
/// trânsito/RoadAlert/radar/"rota recalculada" em [tudo] e [apenasAlertas];
/// nada em [mudo]. Persistido localmente (`voice_mode_prefs.dart`) e
/// propagado ao vivo pro isolate Android via `sendDataToTask`/`onReceiveData`
/// quando trocado no meio de uma navegação já em andamento.
enum NavigationVoiceMode {
  tudo,
  apenasAlertas,
  mudo;

  String get label {
    switch (this) {
      case NavigationVoiceMode.tudo:
        return 'Toda navegação por voz';
      case NavigationVoiceMode.apenasAlertas:
        return 'Só alertas';
      case NavigationVoiceMode.mudo:
        return 'Mudo';
    }
  }

  String get descricao {
    switch (this) {
      case NavigationVoiceMode.tudo:
        return 'Instruções de virada e todos os alertas (trânsito, radar, blitz etc.)';
      case NavigationVoiceMode.apenasAlertas:
        return 'Silencia as instruções de virada — mantém os alertas por voz';
      case NavigationVoiceMode.mudo:
        return 'Nenhuma fala durante a navegação';
    }
  }

  IconData get icon {
    switch (this) {
      case NavigationVoiceMode.tudo:
        return Icons.volume_up_rounded;
      case NavigationVoiceMode.apenasAlertas:
        return Icons.campaign_rounded;
      case NavigationVoiceMode.mudo:
        return Icons.volume_off_rounded;
    }
  }

  /// Só o modo [tudo] fala a instrução de virada.
  bool get falaInstrucaoDeVirada => this == NavigationVoiceMode.tudo;

  /// [tudo] e [apenasAlertas] falam alertas (RoadAlert, trânsito lento, radar,
  /// "rota recalculada") — só [mudo] silencia tudo.
  bool get falaAlertas => this != NavigationVoiceMode.mudo;
}
