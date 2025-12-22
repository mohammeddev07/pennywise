// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'notification.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

_$AppNotificationImpl _$$AppNotificationImplFromJson(
  Map<String, dynamic> json,
) => _$AppNotificationImpl(
  id: json['id'] as String,
  title: json['title'] as String,
  message: json['message'] as String,
  type: $enumDecode(_$NotificationTypeEnumMap, json['type']),
  createdAt: DateTime.parse(json['createdAt'] as String),
  isRead: json['isRead'] as bool,
  actionUrl: json['actionUrl'] as String?,
  metadata: json['metadata'] as Map<String, dynamic>?,
);

Map<String, dynamic> _$$AppNotificationImplToJson(
  _$AppNotificationImpl instance,
) => <String, dynamic>{
  'id': instance.id,
  'title': instance.title,
  'message': instance.message,
  'type': _$NotificationTypeEnumMap[instance.type]!,
  'createdAt': instance.createdAt.toIso8601String(),
  'isRead': instance.isRead,
  'actionUrl': instance.actionUrl,
  'metadata': instance.metadata,
};

const _$NotificationTypeEnumMap = {
  NotificationType.info: 'info',
  NotificationType.warning: 'warning',
  NotificationType.error: 'error',
  NotificationType.success: 'success',
};
