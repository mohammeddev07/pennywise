import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../providers/dashboard_provider.dart';
import '../widgets/stat_card.dart';
import '../widgets/chart_card.dart';
import '../widgets/activity_list.dart';
import '../../../../shared/widgets/skeletons/page_skeletons.dart';
import '../../../../shared/widgets/guest_mode_banner.dart';
import '../../../../features/auth/presentation/providers/auth_provider.dart';
import '../../../../l10n/app_localizations.dart';

class DashboardPage extends ConsumerWidget {
  const DashboardPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final dashboardState = ref.watch(dashboardStatsProvider);
    final authState = ref.watch(authProvider);
    final isAuthenticated = authState.isAuthenticated;
    
    return Scaffold(
      appBar: AppBar(
        title: Row(
          children: [
            Text(AppLocalizations.of(context).dashboard),
            if (!isAuthenticated) ...[
              const SizedBox(width: 8),
              GestureDetector(
                onTap: () => showDialog(
                  context: context,
                  builder: (context) => AlertDialog(
                    title: Text(AppLocalizations.of(context).guestMode),
                    content: Text(AppLocalizations.of(context).viewingDemoData),
                    actions: [
                      TextButton(
                        onPressed: () => Navigator.pop(context),
                        child: Text(AppLocalizations.of(context).close),
                      ),
                    ],
                  ),
                ),
                child: Container(
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
              ),
            ],
          ],
        ),
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh),
            onPressed: () => ref.invalidate(dashboardStatsProvider),
          ),
        ],
      ),
      body: dashboardState.when(
        data: (stats) => RefreshIndicator(
          onRefresh: () async => ref.invalidate(dashboardStatsProvider),
          child: Column(
            children: [
              // Guest mode banner
              if (!isAuthenticated) GuestModeBanner(
                customMessage: AppLocalizations.of(context).thisDashboardShowsDemo,
                loginBenefits: [
                  AppLocalizations.of(context).viewRealAnalytics,
                  AppLocalizations.of(context).createCustomDashboards,
                  AppLocalizations.of(context).exportReports,
                  AppLocalizations.of(context).setupDataAlerts,
                ],
              ),
              
              Expanded(
                child: LayoutBuilder(
                  builder: (context, constraints) {
                    if (constraints.maxWidth >= 1200) {
                      return _buildDesktopLayout(context, stats);
                    } else if (constraints.maxWidth >= 600) {
                      return _buildTabletLayout(context, stats);
                    } else {
                      return _buildMobileLayout(context, stats);
                    }
                  },
                ),
              ),
            ],
          ),
        ),
        loading: () => const DashboardSkeleton(),
        error: (error, stack) => Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              const Icon(Icons.error_outline, size: 64, color: Colors.red),
              const SizedBox(height: 16),
              Text('${AppLocalizations.of(context).error}: $error'),
              const SizedBox(height: 16),
              FilledButton(
                onPressed: () => ref.invalidate(dashboardStatsProvider),
                child: Text(AppLocalizations.of(context).retry),
              ),
            ],
          ),
        ),
      ),
    );
  }
  
  Widget _buildDesktopLayout(BuildContext context, stats) {
    return Padding(
      padding: const EdgeInsets.all(16.0),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Stats Cards Row
          Row(
            children: [
              Expanded(
                child: StatCard(
                  title: AppLocalizations.of(context).totalUsers,
                  value: stats.totalUsers.toString(),
                  icon: Icons.people,
                  color: Theme.of(context).colorScheme.primary,
                ),
              ),
              const SizedBox(width: 16),
              Expanded(
                child: StatCard(
                  title: AppLocalizations.of(context).activeUsers,
                  value: stats.activeUsers.toString(),
                  icon: Icons.verified_user,
                  color: Colors.green,
                ),
              ),
              const SizedBox(width: 16),
              Expanded(
                child: StatCard(
                  title: AppLocalizations.of(context).totalRevenue,
                  value: '\$${(stats.totalRevenue / 1000).toStringAsFixed(1)}K',
                  icon: Icons.attach_money,
                  color: Colors.blue,
                ),
              ),
              const SizedBox(width: 16),
              Expanded(
                child: StatCard(
                  title: AppLocalizations.of(context).growthRate,
                  value: '${stats.growthRate.toStringAsFixed(1)}%',
                  icon: stats.growthRate >= 0 ? Icons.trending_up : Icons.trending_down,
                  color: stats.growthRate >= 0 ? Colors.green : Colors.red,
                ),
              ),
            ],
          ),
          const SizedBox(height: 24),
          // Charts Row
          Expanded(
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Expanded(
                  flex: 2,
                  child: Column(
                    children: [
                      Expanded(
                        child: ChartCard(
                          title: AppLocalizations.of(context).userGrowth,
                          data: stats.userGrowthData,
                          color: Theme.of(context).colorScheme.primary,
                        ),
                      ),
                      const SizedBox(height: 16),
                      Expanded(
                        child: ChartCard(
                          title: AppLocalizations.of(context).revenueTrend,
                          data: stats.revenueData,
                          color: Colors.green,
                          showCurrency: true,
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(width: 16),
                Expanded(
                  child: ActivityList(activities: stats.recentActivities),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
  
  Widget _buildTabletLayout(BuildContext context, stats) {
    return SingleChildScrollView(
      padding: const EdgeInsets.all(16.0),
      child: Column(
        children: [
          // Stats Cards Grid
          GridView.count(
            crossAxisCount: 2,
            shrinkWrap: true,
            physics: const NeverScrollableScrollPhysics(),
            mainAxisSpacing: 16,
            crossAxisSpacing: 16,
            childAspectRatio: 2.5,
            children: [
              StatCard(
                title: AppLocalizations.of(context).totalUsers,
                value: stats.totalUsers.toString(),
                icon: Icons.people,
                color: Theme.of(context).colorScheme.primary,
              ),
              StatCard(
                title: AppLocalizations.of(context).activeUsers,
                value: stats.activeUsers.toString(),
                icon: Icons.verified_user,
                color: Colors.green,
              ),
              StatCard(
                title: AppLocalizations.of(context).totalRevenue,
                value: '\$${(stats.totalRevenue / 1000).toStringAsFixed(1)}K',
                icon: Icons.attach_money,
                color: Colors.blue,
              ),
              StatCard(
                title: AppLocalizations.of(context).growthRate,
                value: '${stats.growthRate.toStringAsFixed(1)}%',
                icon: stats.growthRate >= 0 ? Icons.trending_up : Icons.trending_down,
                color: stats.growthRate >= 0 ? Colors.green : Colors.red,
              ),
            ],
          ),
          const SizedBox(height: 24),
          // Charts
          SizedBox(
            height: 300,
            child: ChartCard(
              title: AppLocalizations.of(context).userGrowth,
              data: stats.userGrowthData,
              color: Theme.of(context).colorScheme.primary,
            ),
          ),
          const SizedBox(height: 16),
          SizedBox(
            height: 300,
            child: ChartCard(
              title: AppLocalizations.of(context).revenueTrend,
              data: stats.revenueData,
              color: Colors.green,
              showCurrency: true,
            ),
          ),
          const SizedBox(height: 16),
          SizedBox(
            height: 400,
            child: ActivityList(activities: stats.recentActivities),
          ),
        ],
      ),
    );
  }
  
  Widget _buildMobileLayout(BuildContext context, stats) {
    return SingleChildScrollView(
      padding: const EdgeInsets.all(16.0),
      child: Column(
        children: [
          // Stats Cards - Single Column
          StatCard(
            title: AppLocalizations.of(context).totalUsers,
            value: stats.totalUsers.toString(),
            icon: Icons.people,
            color: Theme.of(context).colorScheme.primary,
          ),
          const SizedBox(height: 12),
          StatCard(
            title: AppLocalizations.of(context).activeUsers,
            value: stats.activeUsers.toString(),
            icon: Icons.verified_user,
            color: Colors.green,
          ),
          const SizedBox(height: 12),
          StatCard(
            title: AppLocalizations.of(context).totalRevenue,
            value: '\$${(stats.totalRevenue / 1000).toStringAsFixed(1)}K',
            icon: Icons.attach_money,
            color: Colors.blue,
          ),
          const SizedBox(height: 12),
          StatCard(
            title: AppLocalizations.of(context).growthRate,
            value: '${stats.growthRate.toStringAsFixed(1)}%',
            icon: stats.growthRate >= 0 ? Icons.trending_up : Icons.trending_down,
            color: stats.growthRate >= 0 ? Colors.green : Colors.red,
          ),
          const SizedBox(height: 24),
          // Charts
          SizedBox(
            height: 250,
            child: ChartCard(
              title: AppLocalizations.of(context).userGrowth,
              data: stats.userGrowthData,
              color: Theme.of(context).colorScheme.primary,
            ),
          ),
          const SizedBox(height: 16),
          SizedBox(
            height: 250,
            child: ChartCard(
              title: AppLocalizations.of(context).revenueTrend,
              data: stats.revenueData,
              color: Colors.green,
              showCurrency: true,
            ),
          ),
          const SizedBox(height: 16),
          SizedBox(
            height: 400,
            child: ActivityList(activities: stats.recentActivities),
          ),
        ],
      ),
    );
  }
}