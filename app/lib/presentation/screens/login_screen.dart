import 'package:dio/dio.dart';
import 'package:flutter/material.dart';

import '../../data/api_client.dart';
import '../../theme/auth_controller.dart';
import '../widgets/section_card.dart';

/// Login é opcional pro app inteiro — essa tela só existe pra desbloquear o
/// histórico de viagens (Fase 6.4b). Alterna entre login e registro no
/// mesmo formulário em vez de duas telas separadas (é o mesmo par de campos).
class LoginScreen extends StatefulWidget {
  const LoginScreen({super.key});

  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  final ApiClient _apiClient = ApiClient();
  final _emailController = TextEditingController();
  final _senhaController = TextEditingController();

  bool _modoRegistro = false;
  bool _carregando = false;
  String? _errorMessage;

  @override
  void dispose() {
    _emailController.dispose();
    _senhaController.dispose();
    super.dispose();
  }

  Future<void> _enviar() async {
    final email = _emailController.text.trim();
    final senha = _senhaController.text;
    if (email.isEmpty || senha.isEmpty) {
      setState(() => _errorMessage = 'Preencha e-mail e senha.');
      return;
    }

    setState(() {
      _carregando = true;
      _errorMessage = null;
    });

    try {
      final sessao = _modoRegistro
          ? await _apiClient.register(email, senha)
          : await _apiClient.login(email, senha);
      await saveAuthSession(sessao);
      if (!mounted) return;
      Navigator.of(context).pop();
    } on DioException catch (e) {
      final data = e.response?.data;
      final message = (data is Map && data['message'] != null)
          ? data['message'].toString()
          : (_modoRegistro ? 'Não foi possível criar a conta.' : 'E-mail ou senha inválidos.');
      setState(() {
        _carregando = false;
        _errorMessage = message;
      });
    } catch (e) {
      setState(() {
        _carregando = false;
        _errorMessage = 'Erro inesperado: $e';
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text(_modoRegistro ? 'Criar conta' : 'Entrar')),
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(16),
          child: SectionCard(
            icon: Icons.person_outline_rounded,
            title: _modoRegistro ? 'Criar conta' : 'Entrar',
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Text(
                  'Login é opcional — o app funciona todo sem conta. Ter uma só '
                  'desbloqueia salvar e ver o histórico das suas viagens.',
                  style: Theme.of(context).textTheme.bodySmall,
                ),
                const SizedBox(height: 16),
                TextField(
                  controller: _emailController,
                  keyboardType: TextInputType.emailAddress,
                  decoration: const InputDecoration(labelText: 'E-mail'),
                ),
                const SizedBox(height: 12),
                TextField(
                  controller: _senhaController,
                  obscureText: true,
                  decoration: const InputDecoration(labelText: 'Senha'),
                  onSubmitted: (_) => _enviar(),
                ),
                if (_errorMessage != null) ...[
                  const SizedBox(height: 12),
                  Text(_errorMessage!, style: TextStyle(color: Theme.of(context).colorScheme.error)),
                ],
                const SizedBox(height: 16),
                FilledButton(
                  onPressed: _carregando ? null : _enviar,
                  child: _carregando
                      ? const SizedBox(
                          width: 18,
                          height: 18,
                          child: CircularProgressIndicator(strokeWidth: 2),
                        )
                      : Text(_modoRegistro ? 'Criar conta' : 'Entrar'),
                ),
                const SizedBox(height: 8),
                TextButton(
                  onPressed: _carregando ? null : () => setState(() => _modoRegistro = !_modoRegistro),
                  child: Text(_modoRegistro ? 'Já tenho conta — entrar' : 'Não tenho conta — criar uma'),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
