class FuelStation {
  final String nome;
  final double lat;
  final double lon;

  FuelStation({required this.nome, required this.lat, required this.lon});

  factory FuelStation.fromJson(Map<String, dynamic> json) {
    return FuelStation(
      nome: json['nome'] as String,
      lat: (json['lat'] as num).toDouble(),
      lon: (json['lon'] as num).toDouble(),
    );
  }

  Map<String, dynamic> toJson() => {'nome': nome, 'lat': lat, 'lon': lon};
}
