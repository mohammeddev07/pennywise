import 'package:go_router/go_router.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';
import '../../features/auth/presentation/pages/login_page.dart';
import '../../features/auth/presentation/pages/register_page.dart';
import '../../features/auth/presentation/providers/auth_provider.dart';
import '../../features/home/presentation/pages/home_page.dart';
import '../../features/dashboard/presentation/pages/dashboard_page.dart';
import '../../features/users/presentation/pages/users_page.dart';
import '../../features/users/presentation/pages/user_detail_page.dart';
import '../../features/notifications/presentation/pages/notifications_page.dart';
import '../../features/settings/presentation/pages/settings_page.dart';
import '../../features/onboarding/presentation/pages/onboarding_page.dart';
import '../../features/home/presentation/pages/ui_showcase_page.dart';
import '../../features/home/presentation/pages/skeleton_showcase_page.dart';
import '../../features/home/presentation/pages/error_showcase_page.dart';
import '../../features/home/presentation/pages/file_upload_showcase_page.dart';
import '../../features/home/presentation/pages/language_showcase_page.dart';
import '../../features/forms/presentation/pages/forms_example_page.dart';
import '../../shared/widgets/responsive_scaffold.dart';

final onboardingCompletedProvider = StateProvider<bool>((ref) {
  // This will be updated when onboarding is completed
  return false;
});

final _onboardingInitProvider = FutureProvider<bool>((ref) async {
  final prefs = await SharedPreferences.getInstance();
  final completed = prefs.getBool('onboarding_completed') ?? false;
  
  // Update the state provider with the loaded value
  Future.microtask(() {
    ref.read(onboardingCompletedProvider.notifier).state = completed;
  });
  
  return completed;
});

final routerProvider = Provider<GoRouter>((ref) {
  final authState = ref.watch(authProvider);
  final onboardingCompleted = ref.watch(onboardingCompletedProvider);
  
  // Initialize onboarding state from preferences
  ref.watch(_onboardingInitProvider);
  
  return GoRouter(
    initialLocation: onboardingCompleted ? '/home' : '/onboarding',
    redirect: (context, state) {
      final isAuthenticated = authState.isAuthenticated;
      final isAuthRoute = state.matchedLocation == '/login' || 
                         state.matchedLocation == '/register';
      final isOnboardingRoute = state.matchedLocation == '/onboarding';
      
      // Check onboarding first for new users
      if (!onboardingCompleted && !isOnboardingRoute) {
        return '/onboarding';
      }
      
      // Redirect authenticated users away from auth pages
      if (isAuthenticated && isAuthRoute) {
        return '/home';
      }
      
      return null;
    },
    routes: [
      GoRoute(
        path: '/login',
        builder: (context, state) => const LoginPage(),
      ),
      GoRoute(
        path: '/register',
        builder: (context, state) => const RegisterPage(),
      ),
      GoRoute(
        path: '/onboarding',
        builder: (context, state) => const OnboardingPage(),
      ),
      ShellRoute(
        builder: (context, state, child) => ResponsiveScaffold(child: child),
        routes: [
          GoRoute(
            path: '/home',
            builder: (context, state) => const HomePage(),
          ),
          GoRoute(
            path: '/dashboard',
            builder: (context, state) => const DashboardPage(),
          ),
          GoRoute(
            path: '/users',
            builder: (context, state) => const UsersPage(),
            routes: [
              GoRoute(
                path: ':userId',
                builder: (context, state) {
                  final userId = state.pathParameters['userId']!;
                  return UserDetailPage(userId: userId);
                },
              ),
            ],
          ),
          GoRoute(
            path: '/notifications',
            builder: (context, state) => const NotificationsPage(),
          ),
          GoRoute(
            path: '/settings',
            builder: (context, state) => const SettingsPage(),
          ),
          GoRoute(
            path: '/showcase/ui',
            builder: (context, state) => const UIShowcasePage(),
          ),
          GoRoute(
            path: '/showcase/skeletons',
            builder: (context, state) => const SkeletonShowcasePage(),
          ),
          GoRoute(
            path: '/showcase/errors',
            builder: (context, state) => const ErrorShowcasePage(),
          ),
          GoRoute(
            path: '/showcase/upload',
            builder: (context, state) => const FileUploadShowcasePage(),
          ),
          GoRoute(
            path: '/showcase/language',
            builder: (context, state) => const LanguageShowcasePage(),
          ),
          GoRoute(
            path: '/forms',
            builder: (context, state) => const FormsExamplePage(),
          ),
        ],
      ),
    ],
  );
});