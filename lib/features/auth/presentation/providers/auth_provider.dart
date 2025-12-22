import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:freezed_annotation/freezed_annotation.dart';
import '../../data/auth_repository.dart';

part 'auth_provider.freezed.dart';

@freezed
class AuthState with _$AuthState {
  const factory AuthState({
    @Default(false) bool isAuthenticated,
    @Default(false) bool isLoading,
    String? error,
  }) = _AuthState;
}

class AuthNotifier extends StateNotifier<AuthState> {
  AuthNotifier(this._repository) : super(const AuthState());

  final AuthRepository _repository;

  Future<void> login(String email, String password) async {
    state = const AuthState(isLoading: true);
    
    try {
      final success = await _repository.login(email, password);
      if (success) {
        state = const AuthState(isAuthenticated: true);
      } else {
        state = const AuthState(error: 'Invalid credentials');
      }
    } catch (e) {
      state = AuthState(error: e.toString());
    }
  }
  
  Future<void> register(String email, String password) async {
    state = const AuthState(isLoading: true);
    
    try {
      final success = await _repository.register(email, password);
      if (success) {
        state = const AuthState(isAuthenticated: true);
      } else {
        state = const AuthState(error: 'Registration failed');
      }
    } catch (e) {
      state = AuthState(error: e.toString());
    }
  }
  
  Future<void> logout() async {
    await _repository.logout();
    state = const AuthState(isAuthenticated: false);
  }
}

final authProvider = StateNotifierProvider<AuthNotifier, AuthState>((ref) {
  final repository = ref.watch(authRepositoryProvider);
  return AuthNotifier(repository);
});