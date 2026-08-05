class FuelStation {
  final String nome;
  final double lat;
  final double lon;
  // Preço médio (Fase 15) do município onde o posto foi localizado via
  // reverse geocoding — nulo pra postos da lista completa (nunca
  // precificados em massa) e pro posto sugerido quando não foi possível
  // descobrir um preço (Nominatim falhou, ou nenhum município bateu na
  // tabela ANP), caso em que a sugestão cai pro critério antigo
  // (mais próximo do meio da rota).
  final double? precoMedio;
  final String? municipio;

  FuelStation({
    required this.nome,
    required this.lat,
    required this.lon,
    this.precoMedio,
    this.municipio,
  });

  factory FuelStation.fromJson(Map<String, dynamic> json) {
    return FuelStation(
      nome: json['nome'] as String,
      lat: (json['lat'] as num).toDouble(),
      lon: (json['lon'] as num).toDouble(),
      precoMedio: (json['precoMedio'] as num?)?.toDouble(),
      municipio: json['municipio'] as String?,
    );
  }

  Map<String, dynamic> toJson() =>
      {'nome': nome, 'lat': lat, 'lon': lon, 'precoMedio': precoMedio, 'municipio': municipio};
}
