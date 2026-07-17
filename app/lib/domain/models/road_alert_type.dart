import 'package:flutter/material.dart';

/// Alerta de trânsito reportado por qualquer usuário, sem login (Fase 6.6).
enum RoadAlertType {
  buraco,
  blitz,
  neblina,
  carroQuebrado,
  acidente,
  obraNaVia;

  String get apiValue {
    switch (this) {
      case RoadAlertType.buraco:
        return 'BURACO';
      case RoadAlertType.blitz:
        return 'BLITZ';
      case RoadAlertType.neblina:
        return 'NEBLINA';
      case RoadAlertType.carroQuebrado:
        return 'CARRO_QUEBRADO';
      case RoadAlertType.acidente:
        return 'ACIDENTE';
      case RoadAlertType.obraNaVia:
        return 'OBRA_NA_VIA';
    }
  }

  static RoadAlertType fromApiValue(String value) {
    return RoadAlertType.values.firstWhere(
      (t) => t.apiValue == value,
      orElse: () => RoadAlertType.buraco,
    );
  }

  String get label {
    switch (this) {
      case RoadAlertType.buraco:
        return 'Buraco na pista';
      case RoadAlertType.blitz:
        return 'Blitz policial';
      case RoadAlertType.neblina:
        return 'Neblina';
      case RoadAlertType.carroQuebrado:
        return 'Carro quebrado na pista';
      case RoadAlertType.acidente:
        return 'Acidente';
      case RoadAlertType.obraNaVia:
        return 'Obra na via';
    }
  }

  /// Falado pelo TTS ao se aproximar do alerta durante a navegação — frase
  /// própria em vez do label cru, soa mais natural em voz.
  String get vozTexto {
    switch (this) {
      case RoadAlertType.buraco:
        return 'Atenção: buraco na pista à frente';
      case RoadAlertType.blitz:
        return 'Atenção: possível blitz policial à frente';
      case RoadAlertType.neblina:
        return 'Atenção: neblina reportada à frente';
      case RoadAlertType.carroQuebrado:
        return 'Atenção: carro quebrado na pista à frente';
      case RoadAlertType.acidente:
        return 'Atenção: acidente reportado à frente';
      case RoadAlertType.obraNaVia:
        return 'Atenção: obra na via à frente';
    }
  }

  IconData get icon {
    switch (this) {
      case RoadAlertType.buraco:
        return Icons.warning_amber_rounded;
      case RoadAlertType.blitz:
        return Icons.local_police_rounded;
      case RoadAlertType.neblina:
        return Icons.foggy;
      case RoadAlertType.carroQuebrado:
        return Icons.car_crash_outlined;
      case RoadAlertType.acidente:
        return Icons.car_crash_rounded;
      case RoadAlertType.obraNaVia:
        return Icons.construction_rounded;
    }
  }
}
