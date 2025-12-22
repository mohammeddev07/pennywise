import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:go_router/go_router.dart';
import '../providers/notifications_provider.dart';
import '../../../../core/models/notification.dart';
import '../../../../shared/widgets/skeletons/page_skeletons.dart';
import '../../../../shared/widgets/guest_mode_banner.dart';
import '../../../../features/auth/presentation/providers/auth_provider.dart';
import '../../../../l10n/app_localizations.dart';

class NotificationsPage extends ConsumerWidget {
  const NotificationsPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final notificationsAsync = ref.watch(notificationsProvider);
    final showUnreadOnly = ref.watch(showUnreadOnlyProvider);
    final authState = ref.watch(authProvider);
    final isAuthenticated = authState.isAuthenticated;
    
    return Scaffold(
      appBar: AppBar(
        title: Row(
          children: [
            Text(AppLocalizations.of(context).notifications),
            if (!isAuthenticated) ...[
              const SizedBox(width: 8),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                decoration: BoxDecoration(
                  color: Theme.of(context).colorScheme.primaryContainer,
                  borderRadius: BorderRadius.circular(8),
                ),
                child: Text(
                  AppLocalizations.of(context).demoData,
                  style: Theme.of(context).textTheme.labelSmall?.copyWith(
                    color: Theme.of(context).colorScheme.primary,
                    fontWeight: FontWeight.bold,
                  ),
                ),
              ),
            ],
          ],
        ),
        actions: [
          IconButton(
            icon: Icon(showUnreadOnly 
                ? Icons.mark_email_read 
                : Icons.mark_email_unread),
            onPressed: () {
              ref.read(showUnreadOnlyProvider.notifier).state = !showUnreadOnly;
            },
            tooltip: showUnreadOnly ? AppLocalizations.of(context).showAll : AppLocalizations.of(context).showUnreadOnly,
          ),
          PopupMenuButton<String>(
            onSelected: (value) async {
              if (value == 'mark_all_read') {
                if (isAuthenticated) {
                  await ref.read(notificationsProvider.notifier).markAllAsRead();
                  if (context.mounted) {
                    ScaffoldMessenger.of(context).showSnackBar(
                      SnackBar(content: Text(AppLocalizations.of(context).allNotificationsRead)),
                    );
                  }
                } else {
                  ScaffoldMessenger.of(context).showSnackBar(
                    SnackBar(content: Text(AppLocalizations.of(context).loginToManageNotifications)),
                  );
                }
              } else if (value == 'refresh') {
                ref.invalidate(notificationsProvider);
              }
            },
            itemBuilder: (context) => [
              PopupMenuItem(
                value: 'mark_all_read',
                enabled: isAuthenticated,
                child: Row(
                  children: [
                    Icon(Icons.done_all, 
                        color: isAuthenticated ? null : Theme.of(context).disabledColor),
                    const SizedBox(width: 8),
                    Text(AppLocalizations.of(context).markAllAsRead,
                        style: TextStyle(
                          color: isAuthenticated ? null : Theme.of(context).disabledColor,
                        )),
                  ],
                ),
              ),
              PopupMenuItem(
                value: 'refresh',
                child: Row(
                  children: [
                    const Icon(Icons.refresh),
                    const SizedBox(width: 8),
                    Text(AppLocalizations.of(context).refresh),
                  ],
                ),
              ),
            ],
          ),
        ],
      ),
      body: Column(
        children: [
          // Guest mode banner
          if (!isAuthenticated) GuestModeBanner(
            customMessage: AppLocalizations.of(context).demoNotificationsMode,
            loginBenefits: [
              AppLocalizations.of(context).receiveRealTimeNotifications,
              AppLocalizations.of(context).manageNotificationPreferences,
              AppLocalizations.of(context).markNotificationsReadUnread,
              AppLocalizations.of(context).getPersonalizedAlerts,
            ],
          ),
          
          Expanded(
            child: notificationsAsync.when(
              data: (notifications) {
          if (notifications.isEmpty) {
            return Center(
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Icon(
                    showUnreadOnly 
                        ? Icons.notifications_off 
                        : Icons.notifications_none,
                    size: 64,
                    color: Theme.of(context).colorScheme.onSurfaceVariant,
                  ),
                  const SizedBox(height: 16),
                  Text(
                    showUnreadOnly 
                        ? AppLocalizations.of(context).noUnreadNotifications
                        : AppLocalizations.of(context).noNotificationsYet,
                    style: Theme.of(context).textTheme.bodyLarge,
                  ),
                  if (showUnreadOnly) ...[
                    const SizedBox(height: 8),
                    TextButton(
                      onPressed: () {
                        ref.read(showUnreadOnlyProvider.notifier).state = false;
                      },
                      child: Text(AppLocalizations.of(context).showAllNotifications),
                    ),
                  ],
                ],
              ),
            );
          }
          
          // Group notifications by date
          final groupedNotifications = _groupNotificationsByDate(context, notifications);
          
          return RefreshIndicator(
            onRefresh: () async => ref.invalidate(notificationsProvider),
            child: ListView.builder(
              itemCount: groupedNotifications.length,
              itemBuilder: (context, index) {
                final group = groupedNotifications[index];
                return _NotificationGroup(
                  date: group.date,
                  notifications: group.notifications,
                  onNotificationTap: (notification) => 
                      _handleNotificationTap(context, ref, notification),
                );
              },
            ),
          );
              },
              loading: () => const NotificationListSkeleton(),
              error: (error, stack) => Center(
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    const Icon(Icons.error_outline, size: 64, color: Colors.red),
                    const SizedBox(height: 16),
                    Text('${AppLocalizations.of(context).error}: $error'),
                    const SizedBox(height: 16),
                    FilledButton(
                      onPressed: () => ref.invalidate(notificationsProvider),
                      child: Text(AppLocalizations.of(context).retry),
                    ),
                  ],
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
  
  List<_NotificationDateGroup> _groupNotificationsByDate(BuildContext context, List<AppNotification> notifications) {
    final now = DateTime.now();
    final today = DateTime(now.year, now.month, now.day);
    final yesterday = today.subtract(const Duration(days: 1));
    
    final Map<String, List<AppNotification>> grouped = {};
    
    for (final notification in notifications) {
      final notificationDate = DateTime(
        notification.createdAt.year,
        notification.createdAt.month,
        notification.createdAt.day,
      );
      
      String dateKey;
      if (notificationDate == today) {
        dateKey = AppLocalizations.of(context).today;
      } else if (notificationDate == yesterday) {
        dateKey = AppLocalizations.of(context).yesterday;
      } else {
        dateKey = DateFormat.yMMMd().format(notification.createdAt);
      }
      
      grouped.putIfAbsent(dateKey, () => []).add(notification);
    }
    
    return grouped.entries
        .map((e) => _NotificationDateGroup(date: e.key, notifications: e.value))
        .toList();
  }
  
  void _handleNotificationTap(
    BuildContext context, 
    WidgetRef ref, 
    AppNotification notification,
  ) async {
    final authState = ref.read(authProvider);
    
    if (!notification.isRead && authState.isAuthenticated) {
      await ref.read(notificationsProvider.notifier).markAsRead(notification.id);
    }
    
    if (notification.actionUrl != null && context.mounted) {
      context.go(notification.actionUrl!);
    }
  }
}

class _NotificationDateGroup {
  final String date;
  final List<AppNotification> notifications;
  
  _NotificationDateGroup({
    required this.date,
    required this.notifications,
  });
}

class _NotificationGroup extends StatelessWidget {
  final String date;
  final List<AppNotification> notifications;
  final Function(AppNotification) onNotificationTap;
  
  const _NotificationGroup({
    required this.date,
    required this.notifications,
    required this.onNotificationTap,
  });

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
          child: Text(
            date,
            style: Theme.of(context).textTheme.titleSmall?.copyWith(
              color: Theme.of(context).colorScheme.onSurfaceVariant,
              fontWeight: FontWeight.bold,
            ),
          ),
        ),
        ...notifications.asMap().entries.map((entry) {
          final index = entry.key;
          final notification = entry.value;
          return _NotificationTile(
            notification: notification,
            onTap: () => onNotificationTap(notification),
          ).animate(delay: (index * 50).ms)
            .fadeIn(duration: 300.ms)
            .slideX(begin: 0.1, end: 0);
        }),
      ],
    );
  }
}

class _NotificationTile extends StatelessWidget {
  final AppNotification notification;
  final VoidCallback onTap;
  
  const _NotificationTile({
    required this.notification,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final timeFormat = DateFormat.jm();
    
    return ListTile(
      onTap: onTap,
      tileColor: notification.isRead 
          ? null 
          : Theme.of(context).colorScheme.primaryContainer.withValues(alpha: 0.1),
      leading: CircleAvatar(
        backgroundColor: _getColorForType(notification.type).withValues(alpha: 0.1),
        child: Icon(
          _getIconForType(notification.type),
          color: _getColorForType(notification.type),
        ),
      ),
      title: Text(
        notification.title,
        style: TextStyle(
          fontWeight: notification.isRead ? FontWeight.normal : FontWeight.bold,
        ),
      ),
      subtitle: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            notification.message,
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
          ),
          const SizedBox(height: 4),
          Text(
            timeFormat.format(notification.createdAt),
            style: Theme.of(context).textTheme.bodySmall?.copyWith(
              color: Theme.of(context).colorScheme.onSurfaceVariant,
            ),
          ),
        ],
      ),
      trailing: notification.isRead 
          ? null 
          : Container(
              width: 8,
              height: 8,
              decoration: BoxDecoration(
                color: Theme.of(context).colorScheme.primary,
                shape: BoxShape.circle,
              ),
            ),
    );
  }
  
  IconData _getIconForType(NotificationType type) {
    switch (type) {
      case NotificationType.info:
        return Icons.info_outline;
      case NotificationType.warning:
        return Icons.warning_amber_outlined;
      case NotificationType.error:
        return Icons.error_outline;
      case NotificationType.success:
        return Icons.check_circle_outline;
    }
  }
  
  Color _getColorForType(NotificationType type) {
    switch (type) {
      case NotificationType.info:
        return Colors.blue;
      case NotificationType.warning:
        return Colors.orange;
      case NotificationType.error:
        return Colors.red;
      case NotificationType.success:
        return Colors.green;
    }
  }
}