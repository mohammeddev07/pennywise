import 'package:freezed_annotation/freezed_annotation.dart';

part 'notification.freezed.dart';
part 'notification.g.dart';

enum NotificationType { info, warning, error, success }

@freezed
class AppNotification with _$AppNotification {
  const factory AppNotification({
    required String id,
    required String title,
    required String message,
    required NotificationType type,
    required DateTime createdAt,
    required bool isRead,
    String? actionUrl,
    Map<String, dynamic>? metadata,
  }) = _AppNotification;

  factory AppNotification.fromJson(Map<String, dynamic> json) => 
      _$AppNotificationFromJson(json);
}