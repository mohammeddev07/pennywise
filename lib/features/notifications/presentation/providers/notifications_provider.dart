import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../core/models/notification.dart';
import '../../../../core/services/mock_api_service.dart';
import '../../../auth/presentation/providers/auth_provider.dart';

// Show unread only filter
final showUnreadOnlyProvider = StateProvider<bool>((ref) => false);

// Notifications provider
final notificationsProvider = 
    StateNotifierProvider<NotificationsNotifier, AsyncValue<List<AppNotification>>>((ref) {
  return NotificationsNotifier(ref);
});

class NotificationsNotifier extends StateNotifier<AsyncValue<List<AppNotification>>> {
  final Ref _ref;
  
  NotificationsNotifier(this._ref) : super(const AsyncValue.loading()) {
    loadNotifications();
  }
  
  Future<void> loadNotifications() async {
    try {
      state = const AsyncValue.loading();
      final apiService = _ref.read(mockApiServiceProvider);
      final showUnreadOnly = _ref.read(showUnreadOnlyProvider);
      
      final notifications = await apiService.getNotifications(
        unreadOnly: showUnreadOnly,
      );
      
      state = AsyncValue.data(notifications);
    } catch (e, stack) {
      state = AsyncValue.error(e, stack);
    }
  }
  
  Future<void> markAsRead(String notificationId) async {
    // Check authentication
    final authState = _ref.read(authProvider);
    if (!authState.isAuthenticated) {
      throw Exception('Authentication required to mark notifications as read');
    }
    
    try {
      final apiService = _ref.read(mockApiServiceProvider);
      await apiService.markNotificationAsRead(notificationId);
      
      state = state.whenData((notifications) {
        return notifications.map((n) {
          if (n.id == notificationId) {
            return n.copyWith(isRead: true);
          }
          return n;
        }).toList();
      });
    } catch (e, stack) {
      state = AsyncValue.error(e, stack);
    }
  }
  
  Future<void> markAllAsRead() async {
    // Check authentication
    final authState = _ref.read(authProvider);
    if (!authState.isAuthenticated) {
      throw Exception('Authentication required to mark all notifications as read');
    }
    
    try {
      final apiService = _ref.read(mockApiServiceProvider);
      await apiService.markAllNotificationsAsRead();
      
      state = state.whenData((notifications) {
        return notifications.map((n) => n.copyWith(isRead: true)).toList();
      });
    } catch (e, stack) {
      state = AsyncValue.error(e, stack);
    }
  }
}

// Unread notifications count provider
final unreadNotificationsCountProvider = Provider<int>((ref) {
  final notificationsAsync = ref.watch(notificationsProvider);
  
  return notificationsAsync.maybeWhen(
    data: (notifications) => notifications.where((n) => !n.isRead).length,
    orElse: () => 0,
  );
});