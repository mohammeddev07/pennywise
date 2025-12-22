import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../core/models/user.dart';
import '../../../../core/services/mock_api_service.dart';
import '../../../auth/presentation/providers/auth_provider.dart';

// Search query provider
final searchQueryProvider = StateProvider<String>((ref) => '');

// Current page provider for pagination
final currentPageProvider = StateProvider<int>((ref) => 1);

// Users list provider
final usersProvider = StateNotifierProvider<UsersNotifier, AsyncValue<List<User>>>((ref) {
  return UsersNotifier(ref);
});

class UsersNotifier extends StateNotifier<AsyncValue<List<User>>> {
  final Ref _ref;
  
  UsersNotifier(this._ref) : super(const AsyncValue.loading()) {
    loadUsers();
  }
  
  Future<void> loadUsers() async {
    try {
      state = const AsyncValue.loading();
      final apiService = _ref.read(mockApiServiceProvider);
      final searchQuery = _ref.read(searchQueryProvider);
      
      List<User> users;
      if (searchQuery.isNotEmpty) {
        users = await apiService.searchUsers(searchQuery);
      } else {
        final page = _ref.read(currentPageProvider);
        users = await apiService.getUsers(page: page, limit: 50);
      }
      
      state = AsyncValue.data(users);
    } catch (e, stack) {
      state = AsyncValue.error(e, stack);
    }
  }
  
  Future<void> createUser(User user) async {
    // Check authentication
    final authState = _ref.read(authProvider);
    if (!authState.isAuthenticated) {
      throw Exception('Authentication required to create users');
    }
    
    try {
      final apiService = _ref.read(mockApiServiceProvider);
      final newUser = await apiService.createUser(user);
      
      state = state.whenData((users) => [...users, newUser]);
    } catch (e, stack) {
      state = AsyncValue.error(e, stack);
    }
  }
  
  Future<void> updateUser(User user) async {
    // Check authentication
    final authState = _ref.read(authProvider);
    if (!authState.isAuthenticated) {
      throw Exception('Authentication required to update users');
    }
    
    try {
      final apiService = _ref.read(mockApiServiceProvider);
      await apiService.updateUser(user);
      
      state = state.whenData((users) {
        final index = users.indexWhere((u) => u.id == user.id);
        if (index != -1) {
          final updatedUsers = [...users];
          updatedUsers[index] = user;
          return updatedUsers;
        }
        return users;
      });
    } catch (e, stack) {
      state = AsyncValue.error(e, stack);
    }
  }
  
  Future<void> deleteUser(String userId) async {
    // Check authentication
    final authState = _ref.read(authProvider);
    if (!authState.isAuthenticated) {
      throw Exception('Authentication required to delete users');
    }
    
    try {
      final apiService = _ref.read(mockApiServiceProvider);
      await apiService.deleteUser(userId);
      
      state = state.whenData((users) => 
        users.where((u) => u.id != userId).toList()
      );
    } catch (e, stack) {
      state = AsyncValue.error(e, stack);
    }
  }
}

// Selected user provider for detail view
final selectedUserProvider = FutureProvider.family<User?, String>((ref, userId) async {
  final apiService = ref.watch(mockApiServiceProvider);
  return apiService.getUserById(userId);
});