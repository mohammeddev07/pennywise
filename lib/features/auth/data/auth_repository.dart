import 'package:flutter_riverpod/flutter_riverpod.dart';

class AuthRepository {
  Future<bool> login(String email, String password) async {
    // Mock implementation
    await Future.delayed(const Duration(seconds: 1));
    return email.isNotEmpty && password.isNotEmpty;
  }
  
  Future<bool> register(String email, String password) async {
    // Mock implementation
    await Future.delayed(const Duration(seconds: 1));
    return email.isNotEmpty && password.isNotEmpty;
  }
  
  Future<void> logout() async {
    await Future.delayed(const Duration(milliseconds: 500));
  }
}

final authRepositoryProvider = Provider((ref) => AuthRepository());