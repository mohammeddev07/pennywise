import 'package:flutter/material.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:intl/intl.dart';
import '../../../../core/models/dashboard_stats.dart';
import '../../../../l10n/app_localizations.dart';

class ActivityList extends StatelessWidget {
  final List<ActivityItem> activities;
  
  const ActivityList({
    super.key,
    required this.activities,
  });

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Padding(
            padding: const EdgeInsets.all(16.0),
            child: Text(
              AppLocalizations.of(context).recentActivity,
              style: Theme.of(context).textTheme.titleMedium?.copyWith(
                fontWeight: FontWeight.bold,
              ),
            ),
          ),
          const Divider(height: 1),
          Expanded(
            child: ListView.builder(
              itemCount: activities.length,
              itemBuilder: (context, index) {
                final activity = activities[index];
                return _ActivityTile(
                  activity: activity,
                  index: index,
                );
              },
            ),
          ),
        ],
      ),
    ).animate()
      .fadeIn(duration: 600.ms)
      .slideY(begin: 0.1, end: 0, curve: Curves.easeOutCubic);
  }
}

class _ActivityTile extends StatelessWidget {
  final ActivityItem activity;
  final int index;
  
  const _ActivityTile({
    required this.activity,
    required this.index,
  });

  @override
  Widget build(BuildContext context) {
    final timeFormat = DateFormat.jm();
    final dateFormat = DateFormat.MMMd();
    final now = DateTime.now();
    final isToday = activity.timestamp.day == now.day &&
                   activity.timestamp.month == now.month &&
                   activity.timestamp.year == now.year;
    
    return ListTile(
      leading: CircleAvatar(
        backgroundColor: _getColorForType(activity.type).withValues(alpha: 0.1),
        child: Icon(
          _getIconForType(activity.type),
          color: _getColorForType(activity.type),
          size: 20,
        ),
      ),
      title: RichText(
        text: TextSpan(
          style: Theme.of(context).textTheme.bodyMedium,
          children: [
            TextSpan(
              text: activity.userName,
              style: const TextStyle(fontWeight: FontWeight.bold),
            ),
            TextSpan(text: ' ${activity.description}'),
          ],
        ),
      ),
      subtitle: Text(
        isToday 
            ? timeFormat.format(activity.timestamp)
            : dateFormat.format(activity.timestamp),
        style: Theme.of(context).textTheme.bodySmall?.copyWith(
          color: Theme.of(context).colorScheme.onSurfaceVariant,
        ),
      ),
      onTap: () {
        // Could navigate to user detail or activity detail
      },
    ).animate(delay: (index * 50).ms)
      .fadeIn(duration: 300.ms)
      .slideX(begin: 0.1, end: 0);
  }
  
  IconData _getIconForType(String type) {
    switch (type) {
      case 'login':
        return Icons.login;
      case 'update':
        return Icons.edit;
      case 'create':
        return Icons.add_circle_outline;
      case 'delete':
        return Icons.delete_outline;
      case 'message':
        return Icons.message_outlined;
      case 'system':
        return Icons.settings_outlined;
      default:
        return Icons.circle_outlined;
    }
  }
  
  Color _getColorForType(String type) {
    switch (type) {
      case 'login':
        return Colors.blue;
      case 'update':
        return Colors.orange;
      case 'create':
        return Colors.green;
      case 'delete':
        return Colors.red;
      case 'message':
        return Colors.purple;
      case 'system':
        return Colors.grey;
      default:
        return Colors.grey;
    }
  }
}