import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:shared_preferences/shared_preferences.dart';
import '../widgets/onboarding_step.dart';
import '../../../../core/router/app_router.dart';


class OnboardingPage extends ConsumerStatefulWidget {
  const OnboardingPage({super.key});

  @override
  ConsumerState<OnboardingPage> createState() => _OnboardingPageState();
}

class _OnboardingPageState extends ConsumerState<OnboardingPage> {
  final PageController _pageController = PageController();
  int _currentPage = 0;
  
  final List<OnboardingStepData> _steps = [
    OnboardingStepData(
      title: 'Welcome to Flutter Starter',
      description: 'A production-ready Flutter template with Material 3 design, '
                  'state management, and essential features to kickstart your next project.',
      image: Icons.rocket_launch,
      color: Colors.blue,
    ),
    OnboardingStepData(
      title: 'Material 3 Design',
      description: 'Beautiful, adaptive UI components that follow the latest '
                  'Material Design guidelines. Includes dynamic theming with '
                  '9 color options and dark mode support.',
      image: Icons.palette,
      color: Colors.purple,
    ),
    OnboardingStepData(
      title: 'Responsive Navigation',
      description: 'Seamlessly adapts between mobile and desktop layouts. '
                  'Bottom navigation for mobile, navigation rail for desktop, '
                  'all with smooth animations.',
      image: Icons.devices,
      color: Colors.teal,
    ),
    OnboardingStepData(
      title: 'State Management',
      description: 'Built with Riverpod for robust, type-safe state management. '
                  'Includes examples of providers, state notifiers, and async data handling.',
      image: Icons.settings_input_component,
      color: Colors.orange,
    ),
    OnboardingStepData(
      title: 'Ready-to-Use Features',
      description: 'Authentication flows, user management, dashboard with charts, '
                  'notifications, search functionality, and more. Everything you need '
                  'to start building immediately.',
      image: Icons.star,
      color: Colors.green,
    ),
  ];
  
  @override
  void dispose() {
    _pageController.dispose();
    super.dispose();
  }
  
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Stack(
        children: [
          PageView.builder(
            controller: _pageController,
            onPageChanged: (index) {
              setState(() {
                _currentPage = index;
              });
            },
            itemCount: _steps.length,
            itemBuilder: (context, index) {
              return OnboardingStep(
                data: _steps[index],
                index: index,
              );
            },
          ),
          Positioned(
            top: MediaQuery.of(context).padding.top + 8,
            right: 16,
            child: TextButton(
              onPressed: _completeOnboarding,
              child: const Text('Skip'),
            ),
          ),
          Positioned(
            bottom: 48,
            left: 0,
            right: 0,
            child: Column(
              children: [
                // Page indicators
                Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: List.generate(
                    _steps.length,
                    (index) => AnimatedContainer(
                      duration: const Duration(milliseconds: 300),
                      margin: const EdgeInsets.symmetric(horizontal: 4),
                      height: 8,
                      width: _currentPage == index ? 24 : 8,
                      decoration: BoxDecoration(
                        color: _currentPage == index
                            ? Theme.of(context).colorScheme.primary
                            : Theme.of(context).colorScheme.onSurface.withValues(alpha: 0.3),
                        borderRadius: BorderRadius.circular(4),
                      ),
                    ),
                  ),
                ),
                const SizedBox(height: 32),
                // Navigation buttons
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 32),
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      if (_currentPage > 0)
                        OutlinedButton(
                          onPressed: _previousPage,
                          child: const Text('Previous'),
                        )
                      else
                        const SizedBox(width: 100),
                      FilledButton(
                        onPressed: _currentPage == _steps.length - 1
                            ? _completeOnboarding
                            : _nextPage,
                        child: Text(
                          _currentPage == _steps.length - 1
                              ? 'Get Started'
                              : 'Next',
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
  
  void _nextPage() {
    _pageController.nextPage(
      duration: const Duration(milliseconds: 300),
      curve: Curves.easeInOut,
    );
  }
  
  void _previousPage() {
    _pageController.previousPage(
      duration: const Duration(milliseconds: 300),
      curve: Curves.easeInOut,
    );
  }
  
  Future<void> _completeOnboarding() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.setBool('onboarding_completed', true);
      
      if (mounted) {
        // Update the onboarding state provider immediately
        ref.read(onboardingCompletedProvider.notifier).state = true;
        
        // Debug log to verify the flow
        print('Onboarding completed, navigating to /home');
        
        // Navigate to home
        context.go('/home');
      }
    } catch (e) {
      print('Error completing onboarding: $e');
      // Fallback navigation even if preference saving fails
      if (mounted) {
        context.go('/home');
      }
    }
  }
}

class OnboardingStepData {
  final String title;
  final String description;
  final IconData image;
  final Color color;
  
  const OnboardingStepData({
    required this.title,
    required this.description,
    required this.image,
    required this.color,
  });
}