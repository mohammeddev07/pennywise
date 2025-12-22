import 'dart:async';
import 'dart:math';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../models/user.dart';
import '../models/notification.dart';
import '../models/dashboard_stats.dart';

class MockApiService {
  final Random _random = Random();
  
  // Mock data storage
  final List<User> _users = [];
  final List<AppNotification> _notifications = [];
  
  MockApiService() {
    _initializeMockData();
  }
  
  void _initializeMockData() {
    // Generate mock users
    for (int i = 1; i <= 50; i++) {
      _users.add(User(
        id: 'user_$i',
        email: 'user$i@example.com',
        name: _generateRandomName(),
        avatarUrl: _generateAvatarUrl(i),
        role: _getRandomRole(),
        createdAt: DateTime.now().subtract(Duration(days: _random.nextInt(365))),
        isActive: _random.nextBool(),
        department: _getRandomDepartment(),
        phone: '+1 555-${_random.nextInt(900) + 100}-${_random.nextInt(9000) + 1000}',
        lastLogin: DateTime.now().subtract(Duration(hours: _random.nextInt(72))),
      ));
    }
    
    // Generate mock notifications
    for (int i = 1; i <= 20; i++) {
      _notifications.add(AppNotification(
        id: 'notif_$i',
        title: _getRandomNotificationTitle(),
        message: _getRandomNotificationMessage(),
        type: NotificationType.values[_random.nextInt(NotificationType.values.length)],
        createdAt: DateTime.now().subtract(Duration(hours: _random.nextInt(168))),
        isRead: _random.nextBool(),
        actionUrl: _random.nextBool() ? '/users/user_${_random.nextInt(50) + 1}' : null,
      ));
    }
  }
  
  // User endpoints
  Future<List<User>> getUsers({int page = 1, int limit = 10}) async {
    await _simulateNetworkDelay();
    final start = (page - 1) * limit;
    final end = start + limit;
    return _users.sublist(
      start.clamp(0, _users.length),
      end.clamp(0, _users.length),
    );
  }
  
  Future<User?> getUserById(String id) async {
    await _simulateNetworkDelay();
    try {
      return _users.firstWhere((user) => user.id == id);
    } catch (_) {
      return null;
    }
  }
  
  Future<User> createUser(User user) async {
    await _simulateNetworkDelay();
    _users.add(user);
    return user;
  }
  
  Future<User> updateUser(User user) async {
    await _simulateNetworkDelay();
    final index = _users.indexWhere((u) => u.id == user.id);
    if (index != -1) {
      _users[index] = user;
      return user;
    }
    throw Exception('User not found');
  }
  
  Future<void> deleteUser(String id) async {
    await _simulateNetworkDelay();
    _users.removeWhere((user) => user.id == id);
  }
  
  // Notification endpoints
  Future<List<AppNotification>> getNotifications({bool unreadOnly = false}) async {
    await _simulateNetworkDelay();
    if (unreadOnly) {
      return _notifications.where((n) => !n.isRead).toList();
    }
    return List.from(_notifications);
  }
  
  Future<void> markNotificationAsRead(String id) async {
    await _simulateNetworkDelay();
    final index = _notifications.indexWhere((n) => n.id == id);
    if (index != -1) {
      _notifications[index] = _notifications[index].copyWith(isRead: true);
    }
  }
  
  Future<void> markAllNotificationsAsRead() async {
    await _simulateNetworkDelay();
    for (int i = 0; i < _notifications.length; i++) {
      _notifications[i] = _notifications[i].copyWith(isRead: true);
    }
  }
  
  // Dashboard endpoints
  Future<DashboardStats> getDashboardStats() async {
    await _simulateNetworkDelay();
    
    final activeUsers = _users.where((u) => u.isActive).length;
    final totalRevenue = _random.nextInt(1000000) + 500000;
    final growthRate = (_random.nextDouble() * 50) - 10; // -10% to +40%
    
    // Generate chart data
    final userGrowthData = List.generate(7, (index) {
      final day = DateTime.now().subtract(Duration(days: 6 - index));
      return ChartData(
        label: '${day.month}/${day.day}',
        value: (_random.nextInt(50) + 100).toDouble(),
      );
    });
    
    final revenueData = List.generate(7, (index) {
      final day = DateTime.now().subtract(Duration(days: 6 - index));
      return ChartData(
        label: '${day.month}/${day.day}',
        value: (_random.nextInt(50000) + 50000).toDouble(),
      );
    });
    
    // Generate recent activities
    final recentActivities = List.generate(10, (index) {
      final user = _users[_random.nextInt(_users.length)];
      return ActivityItem(
        id: 'activity_$index',
        description: _getRandomActivity(),
        userId: user.id,
        userName: user.name,
        timestamp: DateTime.now().subtract(Duration(minutes: _random.nextInt(1440))),
        type: _getRandomActivityType(),
      );
    });
    
    return DashboardStats(
      totalUsers: _users.length,
      activeUsers: activeUsers,
      totalRevenue: totalRevenue,
      growthRate: growthRate,
      userGrowthData: userGrowthData,
      revenueData: revenueData,
      recentActivities: recentActivities,
    );
  }
  
  // Search functionality
  Future<List<User>> searchUsers(String query) async {
    await _simulateNetworkDelay();
    final lowercaseQuery = query.toLowerCase();
    return _users.where((user) {
      return user.name.toLowerCase().contains(lowercaseQuery) ||
             user.email.toLowerCase().contains(lowercaseQuery) ||
             (user.department?.toLowerCase().contains(lowercaseQuery) ?? false);
    }).toList();
  }
  
  // Helper methods
  Future<void> _simulateNetworkDelay() async {
    await Future.delayed(Duration(milliseconds: 500 + _random.nextInt(500)));
  }
  
  String _generateRandomName() {
    final firstNames = ['John', 'Jane', 'Michael', 'Emily', 'David', 'Sarah', 
                       'Robert', 'Lisa', 'James', 'Mary', 'William', 'Patricia',
                       'Richard', 'Jennifer', 'Thomas', 'Elizabeth'];
    final lastNames = ['Smith', 'Johnson', 'Williams', 'Brown', 'Jones', 
                      'Miller', 'Davis', 'Garcia', 'Rodriguez', 'Wilson',
                      'Martinez', 'Anderson', 'Taylor', 'Moore', 'Jackson'];
    
    return '${firstNames[_random.nextInt(firstNames.length)]} '
           '${lastNames[_random.nextInt(lastNames.length)]}';
  }
  
  String _getRandomRole() {
    final roles = ['Admin', 'Manager', 'Developer', 'Designer', 'Analyst', 
                  'Support', 'Sales', 'Marketing'];
    return roles[_random.nextInt(roles.length)];
  }
  
  String _getRandomDepartment() {
    final departments = ['Engineering', 'Sales', 'Marketing', 'HR', 
                        'Finance', 'Operations', 'Support', 'Product'];
    return departments[_random.nextInt(departments.length)];
  }
  
  String _getRandomNotificationTitle() {
    final titles = [
      'New user registered',
      'System update completed',
      'Monthly report available',
      'Security alert',
      'Payment received',
      'Task completed',
      'Meeting reminder',
      'New message',
    ];
    return titles[_random.nextInt(titles.length)];
  }
  
  String _getRandomNotificationMessage() {
    final messages = [
      'A new user has joined your organization.',
      'System has been successfully updated to the latest version.',
      'Your monthly analytics report is ready for review.',
      'Unusual login activity detected from a new location.',
      'Payment of \$1,250 has been processed successfully.',
      'Your assigned task has been marked as complete.',
      'You have a meeting scheduled in 30 minutes.',
      'You have received a new message in your inbox.',
    ];
    return messages[_random.nextInt(messages.length)];
  }
  
  String _getRandomActivity() {
    final activities = [
      'logged in',
      'updated profile',
      'created a new project',
      'completed a task',
      'uploaded a file',
      'sent a message',
      'joined a team',
      'updated settings',
    ];
    return activities[_random.nextInt(activities.length)];
  }
  
  String _getRandomActivityType() {
    final types = ['login', 'update', 'create', 'delete', 'message', 'system'];
    return types[_random.nextInt(types.length)];
  }
  
  String _generateAvatarUrl(int index) {
    // Use reliable services that return proper image formats (PNG/JPG)
    final services = [
      'https://ui-avatars.com/api/?name=User+$index&background=random&color=fff&size=150&format=png',
      'https://robohash.org/user$index?set=set4&size=150x150&format=png',
      'https://ui-avatars.com/api/?name=Person+$index&background=0D8ABC&color=fff&size=150&format=png',
    ];
    
    // Rotate through different services for variety
    return services[index % services.length];
  }
}

// Provider for the mock API service
final mockApiServiceProvider = Provider((ref) => MockApiService());