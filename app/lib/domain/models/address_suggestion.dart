class AddressSuggestion {
  final String displayName;
  final double lat;
  final double lon;
  // Sigla UF (ex. "RJ") — só vem preenchida pra sugestões do autocomplete
  // (Photon), usada pra sugerir preço regional de combustível.
  final String? uf;

  AddressSuggestion({required this.displayName, required this.lat, required this.lon, this.uf});

  factory AddressSuggestion.fromJson(Map<String, dynamic> json) {
    return AddressSuggestion(
      displayName: json['displayName'] as String,
      lat: (json['lat'] as num).toDouble(),
      lon: (json['lon'] as num).toDouble(),
      uf: json['uf'] as String?,
    );
  }
}
