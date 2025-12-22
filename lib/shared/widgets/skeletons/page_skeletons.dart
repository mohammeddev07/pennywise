import 'package:flutter/material.dart';
import 'skeleton_widget.dart';

// Dashboard Skeleton
class DashboardSkeleton extends StatelessWidget {
  const DashboardSkeleton({super.key});

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        if (constraints.maxWidth > 1200) {
          return _buildDesktopLayout();
        } else if (constraints.maxWidth > 600) {
          return _buildTabletLayout();
        } else {
          return _buildMobileLayout();
        }
      },
    );
  }
  
  Widget _buildDesktopLayout() {
    return Padding(
      padding: const EdgeInsets.all(24),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Stats Grid
          GridView.builder(
            shrinkWrap: true,
            physics: const NeverScrollableScrollPhysics(),
            gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
              crossAxisCount: 4,
              childAspectRatio: 1.5,
              crossAxisSpacing: 16,
              mainAxisSpacing: 16,
            ),
            itemCount: 4,
            itemBuilder: (context, index) => const SkeletonCard(height: 120),
          ),
          const SizedBox(height: 24),
          // Charts Row
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Expanded(
                flex: 2,
                child: SkeletonCard(height: 400),
              ),
              const SizedBox(width: 16),
              const Expanded(
                child: SkeletonCard(height: 400),
              ),
            ],
          ),
          const SizedBox(height: 24),
          // Activity Section
          const SkeletonCard(
            height: 300,
            width: double.infinity,
          ),
        ],
      ),
    );
  }
  
  Widget _buildTabletLayout() {
    return Padding(
      padding: const EdgeInsets.all(16),
      child: Column(
        children: [
          GridView.builder(
            shrinkWrap: true,
            physics: const NeverScrollableScrollPhysics(),
            gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
              crossAxisCount: 2,
              childAspectRatio: 2,
              crossAxisSpacing: 16,
              mainAxisSpacing: 16,
            ),
            itemCount: 4,
            itemBuilder: (context, index) => const SkeletonCard(height: 100),
          ),
          const SizedBox(height: 16),
          const SkeletonCard(height: 300),
          const SizedBox(height: 16),
          const SkeletonCard(height: 300),
        ],
      ),
    );
  }
  
  Widget _buildMobileLayout() {
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        ...List.generate(
          4,
          (index) => const Padding(
            padding: EdgeInsets.only(bottom: 16),
            child: SkeletonCard(height: 100),
          ),
        ),
        const SkeletonCard(height: 300),
        const SizedBox(height: 16),
        const SkeletonCard(height: 300),
      ],
    );
  }
}

// User List Skeleton
class UserListSkeleton extends StatelessWidget {
  final int itemCount;
  
  const UserListSkeleton({
    super.key,
    this.itemCount = 10,
  });

  @override
  Widget build(BuildContext context) {
    return ListView.builder(
      padding: const EdgeInsets.all(16),
      itemCount: itemCount,
      itemBuilder: (context, index) {
        return Card(
          margin: const EdgeInsets.only(bottom: 8),
          child: SkeletonListTile(
            showTrailing: true,
            contentPadding: const EdgeInsets.all(16),
          ),
        );
      },
    );
  }
}

// Notification List Skeleton
class NotificationListSkeleton extends StatelessWidget {
  final int itemCount;
  
  const NotificationListSkeleton({
    super.key,
    this.itemCount = 5,
  });

  @override
  Widget build(BuildContext context) {
    return ListView.builder(
      padding: const EdgeInsets.all(16),
      itemCount: itemCount,
      itemBuilder: (context, index) {
        return Card(
          margin: const EdgeInsets.only(bottom: 12),
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const SkeletonWidget(
                  width: 40,
                  height: 40,
                  shape: BoxShape.circle,
                ),
                const SizedBox(width: 16),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const SkeletonWidget(
                        width: 200,
                        height: 16,
                      ),
                      const SizedBox(height: 8),
                      const SkeletonText(
                        lines: 2,
                        height: 14,
                      ),
                      const SizedBox(height: 8),
                      const SkeletonWidget(
                        width: 100,
                        height: 12,
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
        );
      },
    );
  }
}

// Settings Page Skeleton
class SettingsSkeleton extends StatelessWidget {
  const SettingsSkeleton({super.key});

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        const _SettingsSectionSkeleton(
          title: true,
          itemCount: 3,
        ),
        const SizedBox(height: 24),
        const _SettingsSectionSkeleton(
          title: true,
          itemCount: 4,
        ),
        const SizedBox(height: 24),
        const _SettingsSectionSkeleton(
          title: true,
          itemCount: 2,
        ),
      ],
    );
  }
}

class _SettingsSectionSkeleton extends StatelessWidget {
  final bool title;
  final int itemCount;
  
  const _SettingsSectionSkeleton({
    this.title = false,
    this.itemCount = 3,
  });

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        if (title) ...[
          const SkeletonWidget(
            width: 150,
            height: 20,
          ),
          const SizedBox(height: 16),
        ],
        ...List.generate(
          itemCount,
          (index) => Padding(
            padding: const EdgeInsets.only(bottom: 12),
            child: Row(
              children: [
                const SkeletonWidget(
                  width: 24,
                  height: 24,
                ),
                const SizedBox(width: 16),
                const Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      SkeletonWidget(
                        width: 180,
                        height: 16,
                      ),
                      SizedBox(height: 4),
                      SkeletonWidget(
                        width: 120,
                        height: 14,
                      ),
                    ],
                  ),
                ),
                const SkeletonWidget(
                  width: 48,
                  height: 24,
                  borderRadius: 12,
                ),
              ],
            ),
          ),
        ),
      ],
    );
  }
}

// Profile Skeleton
class ProfileSkeleton extends StatelessWidget {
  const ProfileSkeleton({super.key});

  @override
  Widget build(BuildContext context) {
    return SingleChildScrollView(
      padding: const EdgeInsets.all(24),
      child: Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 600),
          child: Column(
            children: [
              const SkeletonAvatar(size: 120),
              const SizedBox(height: 24),
              const SkeletonWidget(
                width: 200,
                height: 24,
              ),
              const SizedBox(height: 8),
              const SkeletonWidget(
                width: 150,
                height: 16,
              ),
              const SizedBox(height: 32),
              _buildInfoSection(),
              const SizedBox(height: 24),
              _buildInfoSection(),
              const SizedBox(height: 32),
              Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  SkeletonWidget(
                    width: 120,
                    height: 40,
                    borderRadius: 20,
                  ),
                  const SizedBox(width: 16),
                  SkeletonWidget(
                    width: 120,
                    height: 40,
                    borderRadius: 20,
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
  
  Widget _buildInfoSection() {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          children: List.generate(
            3,
            (index) => Padding(
              padding: const EdgeInsets.symmetric(vertical: 8),
              child: Row(
                children: [
                  const SkeletonWidget(
                    width: 24,
                    height: 24,
                  ),
                  const SizedBox(width: 16),
                  const Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        SkeletonWidget(
                          width: 80,
                          height: 14,
                        ),
                        SizedBox(height: 4),
                        SkeletonWidget(
                          width: 150,
                          height: 16,
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

// Data Table Skeleton
class DataTableSkeleton extends StatelessWidget {
  final int columns;
  final int rows;
  
  const DataTableSkeleton({
    super.key,
    this.columns = 5,
    this.rows = 10,
  });

  @override
  Widget build(BuildContext context) {
    return Card(
      margin: const EdgeInsets.all(16),
      child: Column(
        children: [
          // Header
          Container(
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              border: Border(
                bottom: BorderSide(
                  color: Theme.of(context).dividerColor,
                ),
              ),
            ),
            child: Row(
              children: List.generate(
                columns,
                (index) => Expanded(
                  child: SkeletonWidget(
                    height: 16,
                    margin: EdgeInsets.only(right: index < columns - 1 ? 16 : 0),
                  ),
                ),
              ),
            ),
          ),
          // Rows
          ...List.generate(
            rows,
            (rowIndex) => Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                border: Border(
                  bottom: BorderSide(
                    color: Theme.of(context).dividerColor.withValues(alpha: 0.3),
                  ),
                ),
              ),
              child: Row(
                children: List.generate(
                  columns,
                  (colIndex) => Expanded(
                    child: SkeletonWidget(
                      height: 14,
                      margin: EdgeInsets.only(right: colIndex < columns - 1 ? 16 : 0),
                    ),
                  ),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}