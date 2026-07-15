import 'package:flutter_test/flutter_test.dart';
import 'package:latlong2/latlong.dart';

import 'package:rotacusto_app/domain/models/fuel_station.dart';
import 'package:rotacusto_app/domain/models/toll_plaza.dart';
import 'package:rotacusto_app/domain/models/trip_cost_breakdown.dart';

TripCostBreakdown _breakdown({
  double distanciaKm = 0,
  double duracaoMin = 0,
  double custoCombustivel = 0,
  double custoDesgaste = 0,
  double custoPedagio = 0,
  double custoLanche = 0,
  double total = 0,
  List<LatLng> geometriaRota = const [],
  List<TollPlaza> pedagiosNaRota = const [],
  List<FuelStation> postosNaRota = const [],
  FuelStation? postoSugerido,
}) {
  return TripCostBreakdown(
    distanciaKm: distanciaKm,
    duracaoMin: duracaoMin,
    custoCombustivel: custoCombustivel,
    custoDesgaste: custoDesgaste,
    custoPedagio: custoPedagio,
    custoLanche: custoLanche,
    total: total,
    geometriaRota: geometriaRota,
    pedagiosNaRota: pedagiosNaRota,
    postosNaRota: postosNaRota,
    postoSugerido: postoSugerido,
  );
}

void main() {
  test('combine sums every numeric field from both legs', () {
    final ida = _breakdown(
      distanciaKm: 484.7,
      duracaoMin: 360,
      custoCombustivel: 100.0,
      custoDesgaste: 87.0,
      custoPedagio: 21.4,
      custoLanche: 0,
      total: 208.4,
    );
    final volta = _breakdown(
      distanciaKm: 490.2,
      duracaoMin: 370,
      custoCombustivel: 101.5,
      custoDesgaste: 88.0,
      custoPedagio: 10.4, // rota de volta cruzou só 1 pedágio (sentido único), não os 2 da ida
      custoLanche: 0,
      total: 199.9,
    );

    final combinado = TripCostBreakdown.combine(ida, volta);

    expect(combinado.distanciaKm, closeTo(974.9, 0.001));
    expect(combinado.duracaoMin, closeTo(730, 0.001));
    expect(combinado.custoCombustivel, closeTo(201.5, 0.001));
    expect(combinado.custoDesgaste, closeTo(175.0, 0.001));
    expect(combinado.custoPedagio, closeTo(31.8, 0.001));
    expect(combinado.total, closeTo(408.3, 0.001));
  });

  test('combine does not just double the outbound leg — asymmetric tolls stay asymmetric', () {
    // Regressão: pedágio de sentido único cobra só na ida, outro só na volta —
    // dobrar o custo de uma perna só daria um total errado.
    final ida = _breakdown(custoPedagio: 10.4); // só o pedágio "sentido ida"
    final volta = _breakdown(custoPedagio: 11.0); // só o pedágio "sentido volta", valor diferente

    final combinado = TripCostBreakdown.combine(ida, volta);

    expect(combinado.custoPedagio, closeTo(21.4, 0.001));
    expect(combinado.custoPedagio, isNot(closeTo(ida.custoPedagio * 2, 0.001)));
  });

  test('combine concatenates route geometry, tolls and stations from both legs', () {
    final pedagioIda = TollPlaza(nome: 'Pedágio A', rodovia: 'BR-101', concessionaria: 'X', lat: 1, lng: 1, valorCobrado: 5);
    final pedagioVolta = TollPlaza(nome: 'Pedágio B', rodovia: 'BR-101', concessionaria: 'X', lat: 2, lng: 2, valorCobrado: 6);
    final postoIda = FuelStation(nome: 'Posto Ida', lat: 1, lon: 1);
    final postoVolta = FuelStation(nome: 'Posto Volta', lat: 2, lon: 2);

    final ida = _breakdown(
      geometriaRota: [const LatLng(-22.9, -43.1), const LatLng(-20.6, -40.4)],
      pedagiosNaRota: [pedagioIda],
      postosNaRota: [postoIda],
      postoSugerido: postoIda,
    );
    final volta = _breakdown(
      geometriaRota: [const LatLng(-20.6, -40.4), const LatLng(-22.9, -43.1)],
      pedagiosNaRota: [pedagioVolta],
      postosNaRota: [postoVolta],
    );

    final combinado = TripCostBreakdown.combine(ida, volta);

    expect(combinado.geometriaRota.length, 4);
    expect(combinado.pedagiosNaRota, [pedagioIda, pedagioVolta]);
    expect(combinado.postosNaRota, [postoIda, postoVolta]);
    expect(combinado.postoSugerido, postoIda, reason: 'usa a sugestão da ida quando ela existe');
  });

  test('combine falls back to the return leg suggested stop when the outbound has none', () {
    final postoVolta = FuelStation(nome: 'Posto Volta', lat: 2, lon: 2);
    final ida = _breakdown();
    final volta = _breakdown(postoSugerido: postoVolta);

    final combinado = TripCostBreakdown.combine(ida, volta);

    expect(combinado.postoSugerido, postoVolta);
  });
}
