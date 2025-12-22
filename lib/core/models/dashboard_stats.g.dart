// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'dashboard_stats.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

_$DashboardStatsImpl _$$DashboardStatsImplFromJson(Map<String, dynamic> json) =>
    _$DashboardStatsImpl(
      totalUsers: (json['totalUsers'] as num).toInt(),
      activeUsers: (json['activeUsers'] as num).toInt(),
      totalRevenue: (json['totalRevenue'] as num).toInt(),
      growthRate: (json['growthRate'] as num).toDouble(),
      userGrowthData: (json['userGrowthData'] as List<dynamic>)
          .map((e) => ChartData.fromJson(e as Map<String, dynamic>))
          .toList(),
      revenueData: (json['revenueData'] as List<dynamic>)
          .map((e) => ChartData.fromJson(e as Map<String, dynamic>))
          .toList(),
      recentActivities: (json['recentActivities'] as List<dynamic>)
          .map((e) => ActivityItem.fromJson(e as Map<String, dynamic>))
          .toList(),
    );

Map<String, dynamic> _$$DashboardStatsImplToJson(
  _$DashboardStatsImpl instance,
) => <String, dynamic>{
  'totalUsers': instance.totalUsers,
  'activeUsers': instance.activeUsers,
  'totalRevenue': instance.totalRevenue,
  'growthRate': instance.growthRate,
  'userGrowthData': instance.userGrowthData,
  'revenueData': instance.revenueData,
  'recentActivities': instance.recentActivities,
};

_$ChartDataImpl _$$ChartDataImplFromJson(Map<String, dynamic> json) =>
    _$ChartDataImpl(
      label: json['label'] as String,
      value: (json['value'] as num).toDouble(),
      color: json['color'] as String?,
    );

Map<String, dynamic> _$$ChartDataImplToJson(_$ChartDataImpl instance) =>
    <String, dynamic>{
      'label': instance.label,
      'value': instance.value,
      'color': instance.color,
    };

_$ActivityItemImpl _$$ActivityItemImplFromJson(Map<String, dynamic> json) =>
    _$ActivityItemImpl(
      id: json['id'] as String,
      description: json['description'] as String,
      userId: json['userId'] as String,
      userName: json['userName'] as String,
      timestamp: DateTime.parse(json['timestamp'] as String),
      type: json['type'] as String,
    );

Map<String, dynamic> _$$ActivityItemImplToJson(_$ActivityItemImpl instance) =>
    <String, dynamic>{
      'id': instance.id,
      'description': instance.description,
      'userId': instance.userId,
      'userName': instance.userName,
      'timestamp': instance.timestamp.toIso8601String(),
      'type': instance.type,
    };
