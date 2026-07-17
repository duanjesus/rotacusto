import 'dart:convert';

import 'package:shared_preferences/shared_preferences.dart';

import '../domain/models/address_suggestion.dart';

/// Destinos marcados como favoritos pelo usuário (Fase 9) — só no aparelho,
/// sem login, mesmo padrão de `recent_destinations.dart`. Cap de 20 é um
/// chute razoável só pra não crescer sem limite, fácil de ajustar depois.
const _kFavoriteDestinationsKey = 'favorite_destinations_v1';
const _kMaxFavoriteDestinations = 20;

Future<List<AddressSuggestion>> loadFavoriteDestinations() async {
  final prefs = await SharedPreferences.getInstance();
  final raw = prefs.getStringList(_kFavoriteDestinationsKey);
  if (raw == null) return [];
  try {
    return raw.map((s) => AddressSuggestion.fromJson(jsonDecode(s) as Map<String, dynamic>)).toList();
  } catch (_) {
    return [];
  }
}

bool isFavoriteDestination(List<AddressSuggestion> favoritos, AddressSuggestion candidato) {
  return favoritos.any((f) => f.displayName == candidato.displayName);
}

/// Adiciona [destino] se ainda não for favorito, remove se já for — mesmo
/// toque no botão de estrela alterna os dois estados. Retorna a lista
/// atualizada, pra quem chamou não precisar recarregar em seguida.
Future<List<AddressSuggestion>> toggleFavoriteDestination(AddressSuggestion destino) async {
  final prefs = await SharedPreferences.getInstance();
  final atuais = await loadFavoriteDestinations();
  final jaEra = isFavoriteDestination(atuais, destino);
  final atualizados = jaEra
      ? atuais.where((f) => f.displayName != destino.displayName).toList()
      : [...atuais, destino].take(_kMaxFavoriteDestinations).toList();
  await prefs.setStringList(
    _kFavoriteDestinationsKey,
    atualizados.map((d) => jsonEncode({'displayName': d.displayName, 'lat': d.lat, 'lon': d.lon})).toList(),
  );
  return atualizados;
}
