class AddressSuggestion {
  final String displayName;
  final double lat;
  final double lon;
  // Sigla UF (ex. "RJ") — só vem preenchida pra sugestões do autocomplete
  // (Photon), usada pra sugerir preço regional de combustível.
  final String? uf;
  // Nome do município (ex. "Rio de Janeiro", COM acento — vem do Photon) —
  // mesma origem/limitação do uf acima, promovido de "só texto de exibição"
  // pra campo estruturado na Fase 14 pra afinar o preço sugerido de
  // combustível além do nível de UF.
  final String? municipio;

  AddressSuggestion({
    required this.displayName,
    required this.lat,
    required this.lon,
    this.uf,
    this.municipio,
  });

  factory AddressSuggestion.fromJson(Map<String, dynamic> json) {
    return AddressSuggestion(
      displayName: json['displayName'] as String,
      lat: (json['lat'] as num).toDouble(),
      lon: (json['lon'] as num).toDouble(),
      uf: json['uf'] as String?,
      municipio: json['municipio'] as String?,
    );
  }
}
