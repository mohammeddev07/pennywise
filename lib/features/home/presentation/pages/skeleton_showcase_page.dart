import 'package:flutter/material.dart';
import '../../../../shared/widgets/skeletons/skeleton_widget.dart';
import '../../../../shared/widgets/skeletons/page_skeletons.dart';
import '../../../../l10n/app_localizations.dart';

class SkeletonShowcasePage extends StatelessWidget {
  const SkeletonShowcasePage({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(AppLocalizations.of(context).loadingSkeletons),
      ),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          _buildSection(
            context,
            'Basic Skeleton Widgets',
            Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('Rectangle Skeleton', 
                  style: Theme.of(context).textTheme.titleSmall),
                const SizedBox(height: 8),
                const SkeletonWidget(
                  width: 200,
                  height: 20,
                ),
                const SizedBox(height: 16),
                
                Text('Circle Skeleton', 
                  style: Theme.of(context).textTheme.titleSmall),
                const SizedBox(height: 8),
                const SkeletonWidget(
                  width: 60,
                  height: 60,
                  shape: BoxShape.circle,
                ),
                const SizedBox(height: 16),
                
                Text('Text Skeleton', 
                  style: Theme.of(context).textTheme.titleSmall),
                const SizedBox(height: 8),
                const SkeletonText(
                  lines: 3,
                  width: 300,
                ),
                const SizedBox(height: 16),
                
                Text('Avatar Skeleton', 
                  style: Theme.of(context).textTheme.titleSmall),
                const SizedBox(height: 8),
                const Row(
                  children: [
                    SkeletonAvatar(size: 30),
                    SizedBox(width: 16),
                    SkeletonAvatar(size: 40),
                    SizedBox(width: 16),
                    SkeletonAvatar(size: 50),
                    SizedBox(width: 16),
                    SkeletonAvatar(size: 60, shape: BoxShape.rectangle),
                  ],
                ),
              ],
            ),
          ),
          const SizedBox(height: 32),
          
          _buildSection(
            context,
            'Component Skeletons',
            Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('Card Skeleton', 
                  style: Theme.of(context).textTheme.titleSmall),
                const SizedBox(height: 8),
                const SkeletonCard(
                  height: 200,
                  showAvatar: true,
                  showActions: true,
                ),
                const SizedBox(height: 16),
                
                Text('List Tile Skeleton', 
                  style: Theme.of(context).textTheme.titleSmall),
                const SizedBox(height: 8),
                const Card(
                  child: Column(
                    children: [
                      SkeletonListTile(
                        showTrailing: true,
                      ),
                      Divider(height: 1),
                      SkeletonListTile(
                        showSubtitle: false,
                      ),
                      Divider(height: 1),
                      SkeletonListTile(
                        showLeading: false,
                        showTrailing: true,
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 16),
                
                Text('Grid Skeleton', 
                  style: Theme.of(context).textTheme.titleSmall),
                const SizedBox(height: 8),
                const SkeletonGrid(
                  itemCount: 4,
                  crossAxisCount: 2,
                  childAspectRatio: 1.2,
                ),
              ],
            ),
          ),
          const SizedBox(height: 32),
          
          _buildSection(
            context,
            'Page Skeletons',
            Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('User List Skeleton', 
                  style: Theme.of(context).textTheme.titleSmall),
                const SizedBox(height: 8),
                Container(
                  height: 400,
                  decoration: BoxDecoration(
                    border: Border.all(color: Theme.of(context).dividerColor),
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: const UserListSkeleton(itemCount: 5),
                ),
                const SizedBox(height: 16),
                
                Text('Notification List Skeleton', 
                  style: Theme.of(context).textTheme.titleSmall),
                const SizedBox(height: 8),
                Container(
                  height: 400,
                  decoration: BoxDecoration(
                    border: Border.all(color: Theme.of(context).dividerColor),
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: const NotificationListSkeleton(itemCount: 3),
                ),
                const SizedBox(height: 16),
                
                Text('Data Table Skeleton', 
                  style: Theme.of(context).textTheme.titleSmall),
                const SizedBox(height: 8),
                const DataTableSkeleton(
                  columns: 4,
                  rows: 5,
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
  
  Widget _buildSection(BuildContext context, String title, Widget content) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          title,
          style: Theme.of(context).textTheme.headlineSmall?.copyWith(
            fontWeight: FontWeight.bold,
          ),
        ),
        const SizedBox(height: 16),
        content,
      ],
    );
  }
}