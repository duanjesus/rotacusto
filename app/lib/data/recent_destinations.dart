import 'dart:convert';

import 'package:shared_preferences/shared_preferences.dart';

import '../domain/models/address_suggestion.dart';

/// Últimos destinos calculados com sucesso (Fase 9) — só no aparelho, sem
/// login, mesmo padrão de persistência local de `last_trip_cache.dart`. Só
/// guarda endereços vindos do autocomplete (têm lat/lon); texto livre nunca
/// resolvido no cliente não entra aqui.
const _kRecentDestinationsKey = 'recent_destinations_v1';
const _kMaxRecentDestinations = 5;

Future<List<AddressSuggestion>> loadRecentDestinations() async {
  final prefs = await SharedPreferences.getInstance();
  final raw = prefs.getStringList(_kRecentDestinationsKey);
  if (raw == null) return [];
  try {
    return raw.map((s) => AddressSuggestion.fromJson(jsonDecode(s) as Map<String, dynamic>)).toList();
  } catch (_) {
    return [];
  }
}

/// Coloca [destino] no topo da lista — se já existia (mesmo `displayName`),
/// move pro topo em vez de duplicar. Mantém no máximo
/// [_kMaxRecentDestinations].
Future<void> addRecentDestination(AddressSuggestion destino) async {
  final prefs = await SharedPreferences.getInstance();
  final atuais = await loadRecentDestinations();
  final semDuplicata = atuais.where((d) => d.displayName != destino.displayName).toList();
  final atualizados = [destino, ...semDuplicata].take(_kMaxRecentDestinations).toList();
  await prefs.setStringList(
    _kRecentDestinationsKey,
    atualizados.map((d) => jsonEncode({'displayName': d.displayName, 'lat': d.lat, 'lon': d.lon})).toList(),
  );
}
