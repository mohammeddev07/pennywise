import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../core/constants/breakpoints.dart';
import '../../l10n/app_localizations.dart';

class ResponsiveScaffold extends ConsumerStatefulWidget {
  final Widget child;
  
  const ResponsiveScaffold({required this.child, super.key});

  @override
  ConsumerState<ResponsiveScaffold> createState() => _ResponsiveScaffoldState();
}

class _ResponsiveScaffoldState extends ConsumerState<ResponsiveScaffold> 
    with SingleTickerProviderStateMixin {
  late AnimationController _controller;
  late CurvedAnimation _railAnimation;
  late ReverseAnimation _barAnimation;
  bool showMediumSizeLayout = false;
  bool showLargeSizeLayout = false;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(
      duration: const Duration(milliseconds: 500),
      value: 0,
      vsync: this,
    );
    _railAnimation = CurvedAnimation(
      parent: _controller,
      curve: const Interval(0.5, 1.0),
    );
    _barAnimation = ReverseAnimation(
      CurvedAnimation(
        parent: _controller,
        curve: const Interval(0.0, 0.5),
      ),
    );
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    
    final width = MediaQuery.of(context).size.width;
    final AnimationStatus status = _controller.status;
    
    // Use mediumWidthBreakpoint (1000px) as the main breakpoint like material3-example
    if (width > mediumWidthBreakpoint) {
      if (width > largeWidthBreakpoint) {
        showMediumSizeLayout = false;
        showLargeSizeLayout = true;
      } else {
        showMediumSizeLayout = true;
        showLargeSizeLayout = false;
      }
      if (status != AnimationStatus.forward &&
          status != AnimationStatus.completed) {
        _controller.forward();
      }
    } else {
      // Show NavigationBar for screens narrower than mediumWidthBreakpoint
      showMediumSizeLayout = false;
      showLargeSizeLayout = false;
      if (status != AnimationStatus.reverse &&
          status != AnimationStatus.dismissed) {
        _controller.reverse();
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final currentRoute = GoRouterState.of(context).matchedLocation;
    
    return AnimatedBuilder(
      animation: _controller,
      builder: (context, _) {
        return Scaffold(
          body: Row(
            children: [
              if (_controller.value > 0)
                _AnimatedNavigationRail(
                  animation: _railAnimation,
                  extended: showLargeSizeLayout,
                  currentRoute: currentRoute,
                ),
              if (_controller.value > 0)
                const VerticalDivider(thickness: 1, width: 1),
              Expanded(child: widget.child),
            ],
          ),
          bottomNavigationBar: _AnimatedNavigationBar(
            animation: _barAnimation,
            currentRoute: currentRoute,
          ),
        );
      },
    );
  }
}

class _AnimatedNavigationRail extends StatelessWidget {
  final Animation<double> animation;
  final bool extended;
  final String currentRoute;

  const _AnimatedNavigationRail({
    required this.animation,
    required this.extended,
    required this.currentRoute,
  });

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: animation,
      builder: (context, child) {
        return SizeTransition(
          sizeFactor: animation,
          axis: Axis.horizontal,
          axisAlignment: -1,
          child: NavigationRail(
            extended: extended,
            selectedIndex: _getSelectedIndex(currentRoute),
            onDestinationSelected: (index) => _onItemTapped(context, index),
            destinations: [
              NavigationRailDestination(
                icon: const Icon(Icons.home_outlined),
                selectedIcon: const Icon(Icons.home),
                label: Text(AppLocalizations.of(context).home),
              ),
              NavigationRailDestination(
                icon: const Icon(Icons.dashboard_outlined),
                selectedIcon: const Icon(Icons.dashboard),
                label: Text(AppLocalizations.of(context).dashboard),
              ),
              NavigationRailDestination(
                icon: const Icon(Icons.people_outline),
                selectedIcon: const Icon(Icons.people),
                label: Text(AppLocalizations.of(context).users),
              ),
              NavigationRailDestination(
                icon: const Icon(Icons.notifications_outlined),
                selectedIcon: const Icon(Icons.notifications),
                label: Text(AppLocalizations.of(context).notifications),
              ),
              NavigationRailDestination(
                icon: const Icon(Icons.settings_outlined),
                selectedIcon: const Icon(Icons.settings),
                label: Text(AppLocalizations.of(context).settings),
              ),
            ],
          ),
        );
      },
    );
  }

  int _getSelectedIndex(String route) {
    if (route.startsWith('/home') || route.startsWith('/showcase') || route.startsWith('/forms')) return 0;
    if (route.startsWith('/dashboard')) return 1;
    if (route.startsWith('/users')) return 2;
    if (route.startsWith('/notifications')) return 3;
    if (route.startsWith('/settings')) return 4;
    return 0;
  }

  void _onItemTapped(BuildContext context, int index) {
    switch (index) {
      case 0:
        context.go('/home');
        break;
      case 1:
        context.go('/dashboard');
        break;
      case 2:
        context.go('/users');
        break;
      case 3:
        context.go('/notifications');
        break;
      case 4:
        context.go('/settings');
        break;
    }
  }
}

class _AnimatedNavigationBar extends StatelessWidget {
  final Animation<double> animation;
  final String currentRoute;

  const _AnimatedNavigationBar({
    required this.animation,
    required this.currentRoute,
  });

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: animation,
      builder: (context, child) {
        return SizeTransition(
          sizeFactor: animation,
          axisAlignment: 1,
          child: NavigationBar(
            selectedIndex: _getSelectedIndex(currentRoute),
            onDestinationSelected: (index) => _onItemTapped(context, index),
            destinations: [
              NavigationDestination(
                icon: const Icon(Icons.home_outlined),
                selectedIcon: const Icon(Icons.home),
                label: AppLocalizations.of(context).home,
              ),
              NavigationDestination(
                icon: const Icon(Icons.dashboard_outlined),
                selectedIcon: const Icon(Icons.dashboard),
                label: AppLocalizations.of(context).dashboard,
              ),
              NavigationDestination(
                icon: const Icon(Icons.people_outline),
                selectedIcon: const Icon(Icons.people),
                label: AppLocalizations.of(context).users,
              ),
              NavigationDestination(
                icon: const Icon(Icons.settings_outlined),
                selectedIcon: const Icon(Icons.settings),
                label: AppLocalizations.of(context).settings,
              ),
            ],
          ),
        );
      },
    );
  }

  int _getSelectedIndex(String route) {
    if (route.startsWith('/home') || route.startsWith('/showcase') || route.startsWith('/forms')) return 0;
    if (route.startsWith('/dashboard')) return 1;
    if (route.startsWith('/users')) return 2;
    if (route.startsWith('/settings')) return 3;
    return 0;
  }

  void _onItemTapped(BuildContext context, int index) {
    switch (index) {
      case 0:
        context.go('/home');
        break;
      case 1:
        context.go('/dashboard');
        break;
      case 2:
        context.go('/users');
        break;
      case 3:
        context.go('/settings');
        break;
    }
  }
}