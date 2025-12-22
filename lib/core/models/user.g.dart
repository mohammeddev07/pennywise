// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'user.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

_$UserImpl _$$UserImplFromJson(Map<String, dynamic> json) => _$UserImpl(
  id: json['id'] as String,
  email: json['email'] as String,
  name: json['name'] as String,
  avatarUrl: json['avatarUrl'] as String?,
  role: json['role'] as String,
  createdAt: DateTime.parse(json['createdAt'] as String),
  isActive: json['isActive'] as bool,
  department: json['department'] as String?,
  phone: json['phone'] as String?,
  lastLogin: json['lastLogin'] == null
      ? null
      : DateTime.parse(json['lastLogin'] as String),
);

Map<String, dynamic> _$$UserImplToJson(_$UserImpl instance) =>
    <String, dynamic>{
      'id': instance.id,
      'email': instance.email,
      'name': instance.name,
      'avatarUrl': instance.avatarUrl,
      'role': instance.role,
      'createdAt': instance.createdAt.toIso8601String(),
      'isActive': instance.isActive,
      'department': instance.department,
      'phone': instance.phone,
      'lastLogin': instance.lastLogin?.toIso8601String(),
    };
