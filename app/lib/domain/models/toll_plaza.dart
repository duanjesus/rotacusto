class TollPlaza {
  final String nome;
  final String rodovia;
  final String concessionaria;
  final double lat;
  final double lng;
  final double valorCobrado;

  TollPlaza({
    required this.nome,
    required this.rodovia,
    required this.concessionaria,
    required this.lat,
    required this.lng,
    required this.valorCobrado,
  });

  factory TollPlaza.fromJson(Map<String, dynamic> json) {
    return TollPlaza(
      nome: json['nome'] as String,
      rodovia: json['rodovia'] as String,
      concessionaria: json['concessionaria'] as String,
      lat: (json['lat'] as num).toDouble(),
      lng: (json['lng'] as num).toDouble(),
      valorCobrado: (json['valorCobrado'] as num).toDouble(),
    );
  }
}
