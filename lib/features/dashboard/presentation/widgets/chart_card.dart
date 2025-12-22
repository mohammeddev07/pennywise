import 'package:flutter/material.dart';
import 'package:flutter_animate/flutter_animate.dart';
import '../../../../core/models/dashboard_stats.dart';

class ChartCard extends StatelessWidget {
  final String title;
  final List<ChartData> data;
  final Color color;
  final bool showCurrency;
  
  const ChartCard({
    super.key,
    required this.title,
    required this.data,
    required this.color,
    this.showCurrency = false,
  });

  @override
  Widget build(BuildContext context) {
    final maxValue = data.map((e) => e.value).reduce((a, b) => a > b ? a : b);
    
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              title,
              style: Theme.of(context).textTheme.titleMedium?.copyWith(
                fontWeight: FontWeight.bold,
              ),
            ),
            const SizedBox(height: 16),
            Expanded(
              child: LayoutBuilder(
                builder: (context, constraints) {
                  return Row(
                    crossAxisAlignment: CrossAxisAlignment.end,
                    children: data.asMap().entries.map((entry) {
                      final index = entry.key;
                      final item = entry.value;
                      final barHeight = (item.value / maxValue) * constraints.maxHeight * 0.8;
                      
                      return Expanded(
                        child: Padding(
                          padding: const EdgeInsets.symmetric(horizontal: 4.0),
                          child: Column(
                            mainAxisAlignment: MainAxisAlignment.end,
                            children: [
                              Tooltip(
                                message: showCurrency 
                                    ? '\$${item.value.toStringAsFixed(0)}' 
                                    : item.value.toStringAsFixed(0),
                                child: Container(
                                  height: barHeight,
                                  decoration: BoxDecoration(
                                    color: color.withValues(alpha: 0.8),
                                    borderRadius: const BorderRadius.vertical(
                                      top: Radius.circular(4),
                                    ),
                                  ),
                                ).animate(delay: (index * 100).ms)
                                  .scaleY(
                                    begin: 0,
                                    end: 1,
                                    duration: 600.ms,
                                    curve: Curves.easeOutCubic,
                                    alignment: Alignment.bottomCenter,
                                  ),
                              ),
                              const SizedBox(height: 8),
                              Text(
                                item.label,
                                style: Theme.of(context).textTheme.bodySmall,
                                overflow: TextOverflow.ellipsis,
                              ),
                            ],
                          ),
                        ),
                      );
                    }).toList(),
                  );
                },
              ),
            ),
          ],
        ),
      ),
    ).animate()
      .fadeIn(duration: 600.ms)
      .slideY(begin: 0.1, end: 0, curve: Curves.easeOutCubic);
  }
}