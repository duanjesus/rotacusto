import 'package:flutter_map_cache/flutter_map_cache.dart';
import 'package:http_cache_file_store/http_cache_file_store.dart';
import 'package:path_provider/path_provider.dart';

/// Cache de tiles do mapa em disco (Fase 6.5, modo offline) — deliberado,
/// diferente do cache embutido do flutter_map desde a v8.2
/// (`BuiltInMapCachingProvider`), que já vem ligado por padrão mas é
/// documentado como "sem garantia nenhuma, o SO pode limpar a qualquer
/// momento". Usa `getApplicationSupportDirectory()` (não `getTemporaryDirectory()`,
/// que é exatamente o tipo de pasta que o SO limpa) — o objetivo aqui é
/// resistir a ficar sem conexão numa área já visitada antes, não só evitar
/// requisições repetidas enquanto online.
Future<CachedTileProvider> buildTileProvider() async {
  final dir = await getApplicationSupportDirectory();
  return CachedTileProvider(store: FileCacheStore('${dir.path}/map_tiles'));
}
