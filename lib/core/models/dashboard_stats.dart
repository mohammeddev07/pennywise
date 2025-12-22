import 'package:freezed_annotation/freezed_annotation.dart';

part 'dashboard_stats.freezed.dart';
part 'dashboard_stats.g.dart';

@freezed
class DashboardStats with _$DashboardStats {
  const factory DashboardStats({
    required int totalUsers,
    required int activeUsers,
    required int totalRevenue,
    required double growthRate,
    required List<ChartData> userGrowthData,
    required List<ChartData> revenueData,
    required List<ActivityItem> recentActivities,
  }) = _DashboardStats;

  factory DashboardStats.fromJson(Map<String, dynamic> json) => 
      _$DashboardStatsFromJson(json);
}

@freezed
class ChartData with _$ChartData {
  const factory ChartData({
    required String label,
    required double value,
    String? color,
  }) = _ChartData;

  factory ChartData.fromJson(Map<String, dynamic> json) => 
      _$ChartDataFromJson(json);
}

@freezed
class ActivityItem with _$ActivityItem {
  const factory ActivityItem({
    required String id,
    required String description,
    required String userId,
    required String userName,
    required DateTime timestamp,
    required String type,
  }) = _ActivityItem;

  factory ActivityItem.fromJson(Map<String, dynamic> json) => 
      _$ActivityItemFromJson(json);
}