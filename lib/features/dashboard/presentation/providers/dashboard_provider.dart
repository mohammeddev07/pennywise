import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../core/models/dashboard_stats.dart';
import '../../../../core/services/mock_api_service.dart';

final dashboardStatsProvider = FutureProvider<DashboardStats>((ref) async {
  final apiService = ref.watch(mockApiServiceProvider);
  return apiService.getDashboardStats();
});