/// Par marca+modelo distinto, sem ano — resultado do passo 1 da escolha de
/// veículo (o passo 2, escolher o ano, usa [VehicleModel] normalmente).
class VehicleModelSummary {
  final String marca;
  final String modelo;

  VehicleModelSummary({required this.marca, required this.modelo});

  String get displayName => '$marca $modelo';

  factory VehicleModelSummary.fromJson(Map<String, dynamic> json) {
    return VehicleModelSummary(
      marca: json['marca'] as String,
      modelo: json['modelo'] as String,
    );
  }
}
