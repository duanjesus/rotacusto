import 'package:shared_preferences/shared_preferences.dart';
import 'package:uuid/uuid.dart';

/// Identidade anônima do dispositivo (Fase 6.8) — usada só pra impedir voto
/// duplicado num mesmo alerta de trânsito (`RoadAlertVote`), sem exigir
/// conta nenhuma. Mesmo padrão de persistência local de
/// `auth_controller.dart`/`last_trip_cache.dart` (shared_preferences), só
/// que aqui o valor nunca muda depois de gerado uma vez.
const _kDeviceIdKey = 'device_id_v1';

Future<String> getOrCreateDeviceId() async {
  final prefs = await SharedPreferences.getInstance();
  final existente = prefs.getString(_kDeviceIdKey);
  if (existente != null) return existente;

  final novo = const Uuid().v4();
  await prefs.setString(_kDeviceIdKey, novo);
  return novo;
}
