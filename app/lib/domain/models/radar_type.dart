import 'package:flutter/material.dart';

/// Tipo de radar fixo (Fase 12.1) — infraestrutura permanente via OpenStreetMap,
/// mesmo padrão de [RoadAlertType] mas sem relato de usuário/expiração/voto.
enum RadarType {
  velocidade,
  avancoSinal;

  String get apiValue {
    switch (this) {
      case RadarType.velocidade:
        return 'VELOCIDADE';
      case RadarType.avancoSinal:
        return 'AVANCO_SINAL';
    }
  }

  static RadarType fromApiValue(String value) {
    return RadarType.values.firstWhere(
      (t) => t.apiValue == value,
      orElse: () => RadarType.velocidade,
    );
  }

  String get label {
    switch (this) {
      case RadarType.velocidade:
        return 'Radar de velocidade';
      case RadarType.avancoSinal:
        return 'Radar de avanço de sinal';
    }
  }

  /// Falado pelo TTS ao se aproximar do radar durante a navegação.
  String get vozTexto {
    switch (this) {
      case RadarType.velocidade:
        return 'Atenção: radar de velocidade à frente';
      case RadarType.avancoSinal:
        return 'Atenção: radar de avanço de sinal à frente';
    }
  }

  IconData get icon {
    switch (this) {
      case RadarType.velocidade:
        return Icons.camera_alt_rounded;
      case RadarType.avancoSinal:
        return Icons.traffic_rounded;
    }
  }
}
