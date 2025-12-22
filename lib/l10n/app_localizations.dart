import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:intl/intl.dart' as intl;

import 'app_localizations_en.dart';
import 'app_localizations_es.dart';
import 'app_localizations_fr.dart';

// ignore_for_file: type=lint

/// Callers can lookup localized strings with an instance of AppLocalizations
/// returned by `AppLocalizations.of(context)`.
///
/// Applications need to include `AppLocalizations.delegate()` in their app's
/// `localizationDelegates` list, and the locales they support in the app's
/// `supportedLocales` list. For example:
///
/// ```dart
/// import 'l10n/app_localizations.dart';
///
/// return MaterialApp(
///   localizationsDelegates: AppLocalizations.localizationsDelegates,
///   supportedLocales: AppLocalizations.supportedLocales,
///   home: MyApplicationHome(),
/// );
/// ```
///
/// ## Update pubspec.yaml
///
/// Please make sure to update your pubspec.yaml to include the following
/// packages:
///
/// ```yaml
/// dependencies:
///   # Internationalization support.
///   flutter_localizations:
///     sdk: flutter
///   intl: any # Use the pinned version from flutter_localizations
///
///   # Rest of dependencies
/// ```
///
/// ## iOS Applications
///
/// iOS applications define key application metadata, including supported
/// locales, in an Info.plist file that is built into the application bundle.
/// To configure the locales supported by your app, you’ll need to edit this
/// file.
///
/// First, open your project’s ios/Runner.xcworkspace Xcode workspace file.
/// Then, in the Project Navigator, open the Info.plist file under the Runner
/// project’s Runner folder.
///
/// Next, select the Information Property List item, select Add Item from the
/// Editor menu, then select Localizations from the pop-up menu.
///
/// Select and expand the newly-created Localizations item then, for each
/// locale your application supports, add a new item and select the locale
/// you wish to add from the pop-up menu in the Value field. This list should
/// be consistent with the languages listed in the AppLocalizations.supportedLocales
/// property.
abstract class AppLocalizations {
  AppLocalizations(String locale)
    : localeName = intl.Intl.canonicalizedLocale(locale.toString());

  final String localeName;

  static AppLocalizations of(BuildContext context) {
    return Localizations.of<AppLocalizations>(context, AppLocalizations)!;
  }

  static const LocalizationsDelegate<AppLocalizations> delegate =
      _AppLocalizationsDelegate();

  /// A list of this localizations delegate along with the default localizations
  /// delegates.
  ///
  /// Returns a list of localizations delegates containing this delegate along with
  /// GlobalMaterialLocalizations.delegate, GlobalCupertinoLocalizations.delegate,
  /// and GlobalWidgetsLocalizations.delegate.
  ///
  /// Additional delegates can be added by appending to this list in
  /// MaterialApp. This list does not have to be used at all if a custom list
  /// of delegates is preferred or required.
  static const List<LocalizationsDelegate<dynamic>> localizationsDelegates =
      <LocalizationsDelegate<dynamic>>[
        delegate,
        GlobalMaterialLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
      ];

  /// A list of this localizations delegate's supported locales.
  static const List<Locale> supportedLocales = <Locale>[
    Locale('en'),
    Locale('es'),
    Locale('fr'),
  ];

  /// The title of the application
  ///
  /// In en, this message translates to:
  /// **'Flutter Starter'**
  String get appTitle;

  /// Welcome message displayed on home page
  ///
  /// In en, this message translates to:
  /// **'Welcome to Flutter Starter'**
  String get welcomeMessage;

  /// Home navigation label
  ///
  /// In en, this message translates to:
  /// **'Home'**
  String get home;

  /// Dashboard navigation label
  ///
  /// In en, this message translates to:
  /// **'Dashboard'**
  String get dashboard;

  /// Users navigation label
  ///
  /// In en, this message translates to:
  /// **'Users'**
  String get users;

  /// Notifications navigation label
  ///
  /// In en, this message translates to:
  /// **'Notifications'**
  String get notifications;

  /// Settings navigation label
  ///
  /// In en, this message translates to:
  /// **'Settings'**
  String get settings;

  /// Forms navigation label
  ///
  /// In en, this message translates to:
  /// **'Forms'**
  String get forms;

  /// Login button text
  ///
  /// In en, this message translates to:
  /// **'Login'**
  String get login;

  /// Logout button text
  ///
  /// In en, this message translates to:
  /// **'Logout'**
  String get logout;

  /// Email field label
  ///
  /// In en, this message translates to:
  /// **'Email'**
  String get email;

  /// Password field label
  ///
  /// In en, this message translates to:
  /// **'Password'**
  String get password;

  /// Confirm password field label
  ///
  /// In en, this message translates to:
  /// **'Confirm Password'**
  String get confirmPassword;

  /// Register button text
  ///
  /// In en, this message translates to:
  /// **'Register'**
  String get register;

  /// Profile label
  ///
  /// In en, this message translates to:
  /// **'Profile'**
  String get profile;

  /// Theme settings label
  ///
  /// In en, this message translates to:
  /// **'Theme'**
  String get theme;

  /// Language settings label
  ///
  /// In en, this message translates to:
  /// **'Language'**
  String get language;

  /// Light theme mode label
  ///
  /// In en, this message translates to:
  /// **'Light Mode'**
  String get lightMode;

  /// Dark theme mode label
  ///
  /// In en, this message translates to:
  /// **'Dark Mode'**
  String get darkMode;

  /// System theme mode label
  ///
  /// In en, this message translates to:
  /// **'System'**
  String get systemMode;

  /// Search placeholder text
  ///
  /// In en, this message translates to:
  /// **'Search'**
  String get search;

  /// Save button text
  ///
  /// In en, this message translates to:
  /// **'Save'**
  String get save;

  /// Cancel button text
  ///
  /// In en, this message translates to:
  /// **'Cancel'**
  String get cancel;

  /// Delete button text
  ///
  /// In en, this message translates to:
  /// **'Delete'**
  String get delete;

  /// Edit button text
  ///
  /// In en, this message translates to:
  /// **'Edit'**
  String get edit;

  /// Add button text
  ///
  /// In en, this message translates to:
  /// **'Add'**
  String get add;

  /// Loading indicator text
  ///
  /// In en, this message translates to:
  /// **'Loading...'**
  String get loading;

  /// Error message label
  ///
  /// In en, this message translates to:
  /// **'Error'**
  String get error;

  /// Success message label
  ///
  /// In en, this message translates to:
  /// **'Success'**
  String get success;

  /// Retry button text
  ///
  /// In en, this message translates to:
  /// **'Retry'**
  String get retry;

  /// Total users stat label
  ///
  /// In en, this message translates to:
  /// **'Total Users'**
  String get totalUsers;

  /// Active users stat label
  ///
  /// In en, this message translates to:
  /// **'Active Users'**
  String get activeUsers;

  /// Total revenue stat label
  ///
  /// In en, this message translates to:
  /// **'Total Revenue'**
  String get totalRevenue;

  /// Growth rate stat label
  ///
  /// In en, this message translates to:
  /// **'Growth Rate'**
  String get growthRate;

  /// Name field label
  ///
  /// In en, this message translates to:
  /// **'Name'**
  String get name;

  /// Full name field label
  ///
  /// In en, this message translates to:
  /// **'Full Name'**
  String get fullName;

  /// Phone number field label
  ///
  /// In en, this message translates to:
  /// **'Phone Number'**
  String get phoneNumber;

  /// Department field label
  ///
  /// In en, this message translates to:
  /// **'Department'**
  String get department;

  /// Role field label
  ///
  /// In en, this message translates to:
  /// **'Role'**
  String get role;

  /// Status field label
  ///
  /// In en, this message translates to:
  /// **'Status'**
  String get status;

  /// Active status label
  ///
  /// In en, this message translates to:
  /// **'Active'**
  String get active;

  /// Inactive status label
  ///
  /// In en, this message translates to:
  /// **'Inactive'**
  String get inactive;

  /// File upload feature label
  ///
  /// In en, this message translates to:
  /// **'File Upload'**
  String get fileUpload;

  /// File upload drop zone text
  ///
  /// In en, this message translates to:
  /// **'Drop files here'**
  String get dropFilesHere;

  /// Select files button text
  ///
  /// In en, this message translates to:
  /// **'Select Files'**
  String get selectFiles;

  /// File upload success message
  ///
  /// In en, this message translates to:
  /// **'Upload successful!'**
  String get uploadSuccess;

  /// File upload error message
  ///
  /// In en, this message translates to:
  /// **'Upload failed'**
  String get uploadError;

  /// Welcome title on home page
  ///
  /// In en, this message translates to:
  /// **'Welcome to Flutter Starter'**
  String get welcomeTitle;

  /// Welcome subtitle describing the app
  ///
  /// In en, this message translates to:
  /// **'A Material 3 starter template with authentication,\\ntheming, and responsive navigation'**
  String get welcomeSubtitle;

  /// UI Components showcase button
  ///
  /// In en, this message translates to:
  /// **'UI Components'**
  String get uiComponents;

  /// Loading skeletons showcase button
  ///
  /// In en, this message translates to:
  /// **'Loading Skeletons'**
  String get loadingSkeletons;

  /// Error handling showcase button
  ///
  /// In en, this message translates to:
  /// **'Error Handling'**
  String get errorHandling;

  /// Features section title
  ///
  /// In en, this message translates to:
  /// **'Features'**
  String get features;

  /// Material 3 design feature title
  ///
  /// In en, this message translates to:
  /// **'Material 3 Design'**
  String get material3Design;

  /// Material 3 design feature description
  ///
  /// In en, this message translates to:
  /// **'Built with the latest Material Design system'**
  String get material3Description;

  /// Dynamic theming feature title
  ///
  /// In en, this message translates to:
  /// **'Dynamic Theming'**
  String get dynamicTheming;

  /// Dynamic theming feature description
  ///
  /// In en, this message translates to:
  /// **'Choose from 9 color themes with dark mode support'**
  String get dynamicThemingDescription;

  /// Responsive layout feature title
  ///
  /// In en, this message translates to:
  /// **'Responsive Layout'**
  String get responsiveLayout;

  /// Responsive layout feature description
  ///
  /// In en, this message translates to:
  /// **'Adapts to mobile, tablet, and desktop screens'**
  String get responsiveLayoutDescription;

  /// Authentication ready feature title
  ///
  /// In en, this message translates to:
  /// **'Authentication Ready'**
  String get authenticationReady;

  /// Authentication ready feature description
  ///
  /// In en, this message translates to:
  /// **'Login and registration flows included'**
  String get authenticationDescription;

  /// Welcome back message on login page
  ///
  /// In en, this message translates to:
  /// **'Welcome Back'**
  String get welcomeBack;

  /// Create account title and button
  ///
  /// In en, this message translates to:
  /// **'Create Account'**
  String get createAccount;

  /// Email validation message
  ///
  /// In en, this message translates to:
  /// **'Please enter your email'**
  String get pleaseEnterEmail;

  /// Invalid email validation message
  ///
  /// In en, this message translates to:
  /// **'Please enter a valid email'**
  String get pleaseEnterValidEmail;

  /// Password validation message
  ///
  /// In en, this message translates to:
  /// **'Please enter your password'**
  String get pleaseEnterPassword;

  /// Password length validation message
  ///
  /// In en, this message translates to:
  /// **'Password must be at least 6 characters'**
  String get passwordMustBe6Chars;

  /// Confirm password validation message
  ///
  /// In en, this message translates to:
  /// **'Please confirm your password'**
  String get pleaseConfirmPassword;

  /// Password mismatch validation message
  ///
  /// In en, this message translates to:
  /// **'Passwords do not match'**
  String get passwordsDoNotMatch;

  /// Login link on register page
  ///
  /// In en, this message translates to:
  /// **'Already have an account? Login'**
  String get alreadyHaveAccount;

  /// Register link on login page
  ///
  /// In en, this message translates to:
  /// **'Create an account'**
  String get createAnAccount;

  /// Guest mode indicator
  ///
  /// In en, this message translates to:
  /// **'Guest Mode'**
  String get guestMode;

  /// Demo data indicator
  ///
  /// In en, this message translates to:
  /// **'Demo'**
  String get demoData;

  /// Demo data explanation
  ///
  /// In en, this message translates to:
  /// **'You\'re viewing demo data in guest mode. Login to see your real dashboard.'**
  String get viewingDemoData;

  /// Close button text
  ///
  /// In en, this message translates to:
  /// **'Close'**
  String get close;

  /// Dashboard demo data message
  ///
  /// In en, this message translates to:
  /// **'This dashboard shows demo data in guest mode.'**
  String get thisDashboardShowsDemo;

  /// Login benefit - analytics
  ///
  /// In en, this message translates to:
  /// **'View your real analytics data'**
  String get viewRealAnalytics;

  /// Login benefit - dashboards
  ///
  /// In en, this message translates to:
  /// **'Create custom dashboards'**
  String get createCustomDashboards;

  /// Login benefit - reports
  ///
  /// In en, this message translates to:
  /// **'Export reports and insights'**
  String get exportReports;

  /// Login benefit - alerts
  ///
  /// In en, this message translates to:
  /// **'Set up data alerts'**
  String get setupDataAlerts;

  /// User growth chart title
  ///
  /// In en, this message translates to:
  /// **'User Growth'**
  String get userGrowth;

  /// Revenue trend chart title
  ///
  /// In en, this message translates to:
  /// **'Revenue Trend'**
  String get revenueTrend;

  /// Recent activity section title
  ///
  /// In en, this message translates to:
  /// **'Recent Activity'**
  String get recentActivity;

  /// User management guest mode message
  ///
  /// In en, this message translates to:
  /// **'User management features are limited in guest mode.'**
  String get userManagementLimited;

  /// Login benefit - user management
  ///
  /// In en, this message translates to:
  /// **'Add, edit, and delete users'**
  String get addEditDeleteUsers;

  /// Login benefit - user profiles
  ///
  /// In en, this message translates to:
  /// **'Access detailed user profiles'**
  String get accessDetailedProfiles;

  /// Login benefit - user data export
  ///
  /// In en, this message translates to:
  /// **'Export user data'**
  String get exportUserData;

  /// Login benefit - user permissions
  ///
  /// In en, this message translates to:
  /// **'Manage user permissions'**
  String get manageUserPermissions;

  /// User search placeholder
  ///
  /// In en, this message translates to:
  /// **'Search users by name, email, or department...'**
  String get searchUsers;

  /// Add user button
  ///
  /// In en, this message translates to:
  /// **'Add User'**
  String get addUser;

  /// Login to add button for guests
  ///
  /// In en, this message translates to:
  /// **'Login to Add'**
  String get loginToAdd;

  /// No users found message
  ///
  /// In en, this message translates to:
  /// **'No users found matching \"{query}\"'**
  String noUsersFound(String query);

  /// No users message
  ///
  /// In en, this message translates to:
  /// **'No users yet'**
  String get noUsersYet;

  /// Undo button text
  ///
  /// In en, this message translates to:
  /// **'Undo'**
  String get undo;

  /// Error deleting user message
  ///
  /// In en, this message translates to:
  /// **'Error deleting user: {error}'**
  String errorDeletingUser(String error);

  /// Delete user dialog title
  ///
  /// In en, this message translates to:
  /// **'Delete User'**
  String get deleteUser;

  /// Delete user confirmation message
  ///
  /// In en, this message translates to:
  /// **'Are you sure you want to delete {name}?'**
  String confirmDeleteUser(String name);

  /// User details page title
  ///
  /// In en, this message translates to:
  /// **'User Details'**
  String get userDetails;

  /// User not found message
  ///
  /// In en, this message translates to:
  /// **'User not found'**
  String get userNotFound;

  /// Back to users button
  ///
  /// In en, this message translates to:
  /// **'Back to Users'**
  String get backToUsers;

  /// Contact information section
  ///
  /// In en, this message translates to:
  /// **'Contact Information'**
  String get contactInformation;

  /// Phone field label
  ///
  /// In en, this message translates to:
  /// **'Phone'**
  String get phone;

  /// Account information section
  ///
  /// In en, this message translates to:
  /// **'Account Information'**
  String get accountInformation;

  /// User ID field label
  ///
  /// In en, this message translates to:
  /// **'User ID'**
  String get userId;

  /// Created date field label
  ///
  /// In en, this message translates to:
  /// **'Created'**
  String get created;

  /// Activity section title
  ///
  /// In en, this message translates to:
  /// **'Activity'**
  String get activity;

  /// Last login field label
  ///
  /// In en, this message translates to:
  /// **'Last Login'**
  String get lastLogin;

  /// User never logged in message
  ///
  /// In en, this message translates to:
  /// **'User has not logged in yet'**
  String get userNotLoggedIn;

  /// User deleted success message
  ///
  /// In en, this message translates to:
  /// **'User {name} deleted'**
  String userDeleted(String name);

  /// Edit user button and title
  ///
  /// In en, this message translates to:
  /// **'Edit User'**
  String get editUser;

  /// Actions column header
  ///
  /// In en, this message translates to:
  /// **'Actions'**
  String get actions;

  /// View details button
  ///
  /// In en, this message translates to:
  /// **'View Details'**
  String get viewDetails;

  /// Login prompt for guest users
  ///
  /// In en, this message translates to:
  /// **'Login to Edit Users'**
  String get loginToEditUsers;

  /// Create new user dialog title
  ///
  /// In en, this message translates to:
  /// **'Create New User'**
  String get createNewUser;

  /// Name validation message
  ///
  /// In en, this message translates to:
  /// **'Please enter a name'**
  String get pleaseEnterName;

  /// Optional phone field label
  ///
  /// In en, this message translates to:
  /// **'Phone (optional)'**
  String get phoneOptional;

  /// Active user checkbox description
  ///
  /// In en, this message translates to:
  /// **'User can access the system'**
  String get userCanAccess;

  /// Create button text
  ///
  /// In en, this message translates to:
  /// **'Create'**
  String get create;

  /// User created success message
  ///
  /// In en, this message translates to:
  /// **'User {name} created successfully'**
  String userCreatedSuccess(String name);

  /// Error creating user message
  ///
  /// In en, this message translates to:
  /// **'Error creating user: {error}'**
  String errorCreatingUser(String error);

  /// Save changes button
  ///
  /// In en, this message translates to:
  /// **'Save Changes'**
  String get saveChanges;

  /// User updated success message
  ///
  /// In en, this message translates to:
  /// **'User {name} updated successfully'**
  String userUpdatedSuccess(String name);

  /// Error updating user message
  ///
  /// In en, this message translates to:
  /// **'Error updating user: {error}'**
  String errorUpdatingUser(String error);

  /// Show all notifications filter
  ///
  /// In en, this message translates to:
  /// **'Show all'**
  String get showAll;

  /// Show unread only notifications filter
  ///
  /// In en, this message translates to:
  /// **'Show unread only'**
  String get showUnreadOnly;

  /// Mark all notifications as read
  ///
  /// In en, this message translates to:
  /// **'Mark all as read'**
  String get markAllAsRead;

  /// All notifications marked as read success message
  ///
  /// In en, this message translates to:
  /// **'All notifications marked as read'**
  String get allNotificationsRead;

  /// Login required for notifications message
  ///
  /// In en, this message translates to:
  /// **'Please login to manage notifications'**
  String get loginToManageNotifications;

  /// Refresh button text
  ///
  /// In en, this message translates to:
  /// **'Refresh'**
  String get refresh;

  /// Demo notifications explanation
  ///
  /// In en, this message translates to:
  /// **'These are demo notifications in guest mode.'**
  String get demoNotificationsMode;

  /// Login benefit - real-time notifications
  ///
  /// In en, this message translates to:
  /// **'Receive real-time notifications'**
  String get receiveRealTimeNotifications;

  /// Login benefit - notification preferences
  ///
  /// In en, this message translates to:
  /// **'Manage notification preferences'**
  String get manageNotificationPreferences;

  /// Login benefit - mark notifications
  ///
  /// In en, this message translates to:
  /// **'Mark notifications as read/unread'**
  String get markNotificationsReadUnread;

  /// Login benefit - personalized alerts
  ///
  /// In en, this message translates to:
  /// **'Get personalized alerts'**
  String get getPersonalizedAlerts;

  /// No unread notifications message
  ///
  /// In en, this message translates to:
  /// **'No unread notifications'**
  String get noUnreadNotifications;

  /// No notifications message
  ///
  /// In en, this message translates to:
  /// **'No notifications yet'**
  String get noNotificationsYet;

  /// Show all notifications button
  ///
  /// In en, this message translates to:
  /// **'Show all notifications'**
  String get showAllNotifications;

  /// Today date label
  ///
  /// In en, this message translates to:
  /// **'Today'**
  String get today;

  /// Yesterday date label
  ///
  /// In en, this message translates to:
  /// **'Yesterday'**
  String get yesterday;

  /// Theme changes not saved in guest mode message
  ///
  /// In en, this message translates to:
  /// **'Theme changes won\'t be saved permanently in guest mode.'**
  String get themeChangeNotSaved;

  /// Login benefit - save theme preferences
  ///
  /// In en, this message translates to:
  /// **'Save theme preferences permanently'**
  String get saveThemePreferences;

  /// Login benefit - user management
  ///
  /// In en, this message translates to:
  /// **'Access user management features'**
  String get accessUserManagement;

  /// Login benefit - sync settings
  ///
  /// In en, this message translates to:
  /// **'Sync settings across devices'**
  String get syncSettingsAcrossDevices;

  /// Appearance settings section
  ///
  /// In en, this message translates to:
  /// **'Appearance'**
  String get appearance;

  /// Dark theme toggle description
  ///
  /// In en, this message translates to:
  /// **'Use dark theme'**
  String get useDarkTheme;

  /// Dark theme toggle description for guests
  ///
  /// In en, this message translates to:
  /// **'Use dark theme (not saved in guest mode)'**
  String get useDarkThemeNotSaved;

  /// Theme color section title
  ///
  /// In en, this message translates to:
  /// **'Theme Color'**
  String get themeColor;

  /// About section title
  ///
  /// In en, this message translates to:
  /// **'About'**
  String get about;

  /// App name in about section
  ///
  /// In en, this message translates to:
  /// **'Flutter Starter App'**
  String get flutterStarterApp;

  /// App version
  ///
  /// In en, this message translates to:
  /// **'Version 1.0.0'**
  String get version;

  /// App name
  ///
  /// In en, this message translates to:
  /// **'Flutter Starter'**
  String get flutterStarter;

  /// App description
  ///
  /// In en, this message translates to:
  /// **'A Material 3 starter template'**
  String get material3StarterTemplate;

  /// Detailed app features description
  ///
  /// In en, this message translates to:
  /// **'This is a production-ready Flutter starter template featuring:\\n\\n• Material 3 design system\\n• Dynamic theming with 9 color options\\n• Responsive navigation\\n• Authentication flows\\n• State management with Riverpod'**
  String get starterFeatures;

  /// Guest mode title in home page
  ///
  /// In en, this message translates to:
  /// **'Guest Mode'**
  String get guestModeInHome;

  /// Login prompt for personalization
  ///
  /// In en, this message translates to:
  /// **'Login to access theme customization and other personalized features.'**
  String get loginToAccessPersonalization;

  /// Forms example page title
  ///
  /// In en, this message translates to:
  /// **'Forms Example'**
  String get formsExample;

  /// User registration form title
  ///
  /// In en, this message translates to:
  /// **'User Registration Form'**
  String get userRegistrationForm;

  /// Complete form description
  ///
  /// In en, this message translates to:
  /// **'Complete form with various field types and validations'**
  String get completeFormDescription;

  /// Payment form title
  ///
  /// In en, this message translates to:
  /// **'Payment Form'**
  String get paymentForm;

  /// Credit card form description
  ///
  /// In en, this message translates to:
  /// **'Credit card form with formatting and validation'**
  String get creditCardFormDescription;

  /// Feedback form title
  ///
  /// In en, this message translates to:
  /// **'Feedback Form'**
  String get feedbackForm;

  /// Rating form description
  ///
  /// In en, this message translates to:
  /// **'Form with rating and multi-line text'**
  String get ratingFormDescription;

  /// Name length validation message
  ///
  /// In en, this message translates to:
  /// **'Name must be at least 3 characters'**
  String get nameMustBe3Chars;

  /// Phone number validation message
  ///
  /// In en, this message translates to:
  /// **'Please enter your phone number'**
  String get pleaseEnterPhoneNumber;

  /// Invalid phone number validation message
  ///
  /// In en, this message translates to:
  /// **'Please enter a valid phone number'**
  String get pleaseEnterValidPhoneNumber;

  /// Gender field label
  ///
  /// In en, this message translates to:
  /// **'Gender'**
  String get gender;

  /// Password length validation for forms
  ///
  /// In en, this message translates to:
  /// **'Password must be at least 8 characters'**
  String get passwordMustBe8Chars;

  /// Password complexity validation message
  ///
  /// In en, this message translates to:
  /// **'Password must contain uppercase, lowercase, and number'**
  String get passwordComplexityRequired;

  /// Interests field label
  ///
  /// In en, this message translates to:
  /// **'Interests'**
  String get interests;

  /// Terms and conditions checkbox
  ///
  /// In en, this message translates to:
  /// **'I agree to the Terms and Conditions'**
  String get agreeToTerms;

  /// Card number field label
  ///
  /// In en, this message translates to:
  /// **'Card Number'**
  String get cardNumber;

  /// Card number validation message
  ///
  /// In en, this message translates to:
  /// **'Please enter card number'**
  String get pleaseEnterCardNumber;

  /// Card number length validation
  ///
  /// In en, this message translates to:
  /// **'Card number must be 16 digits'**
  String get cardNumberMustBe16Digits;

  /// Card holder name field label
  ///
  /// In en, this message translates to:
  /// **'Card Holder Name'**
  String get cardHolderName;

  /// Card holder name validation message
  ///
  /// In en, this message translates to:
  /// **'Please enter card holder name'**
  String get pleaseEnterCardHolderName;

  /// Expiry date field label
  ///
  /// In en, this message translates to:
  /// **'Expiry Date'**
  String get expiryDate;

  /// Expiry date format placeholder
  ///
  /// In en, this message translates to:
  /// **'MM/YY'**
  String get mmYy;

  /// Required field validation
  ///
  /// In en, this message translates to:
  /// **'Required'**
  String get required;

  /// Invalid field validation
  ///
  /// In en, this message translates to:
  /// **'Invalid'**
  String get invalid;

  /// CVV field label
  ///
  /// In en, this message translates to:
  /// **'CVV'**
  String get cvv;

  /// Process payment button
  ///
  /// In en, this message translates to:
  /// **'Process Payment'**
  String get processPayment;

  /// Subject field label
  ///
  /// In en, this message translates to:
  /// **'Subject'**
  String get subject;

  /// Subject validation message
  ///
  /// In en, this message translates to:
  /// **'Please enter a subject'**
  String get pleaseEnterSubject;

  /// Satisfaction rating field label
  ///
  /// In en, this message translates to:
  /// **'Satisfaction Rating'**
  String get satisfactionRating;

  /// Feedback text field label
  ///
  /// In en, this message translates to:
  /// **'Your Feedback'**
  String get yourFeedback;

  /// Feedback text field placeholder
  ///
  /// In en, this message translates to:
  /// **'Tell us what you think...'**
  String get tellUsWhatYouThink;

  /// Feedback validation message
  ///
  /// In en, this message translates to:
  /// **'Please enter your feedback'**
  String get pleaseEnterFeedback;

  /// Feedback length validation
  ///
  /// In en, this message translates to:
  /// **'Feedback must be at least 10 characters'**
  String get feedbackMustBe10Chars;

  /// Submit feedback button
  ///
  /// In en, this message translates to:
  /// **'Submit Feedback'**
  String get submitFeedback;

  /// UI components showcase page title
  ///
  /// In en, this message translates to:
  /// **'UI Components Showcase'**
  String get uiComponentsShowcase;

  /// Buttons section title
  ///
  /// In en, this message translates to:
  /// **'Buttons'**
  String get buttons;

  /// Primary button label
  ///
  /// In en, this message translates to:
  /// **'Primary'**
  String get primary;

  /// Secondary button label
  ///
  /// In en, this message translates to:
  /// **'Secondary'**
  String get secondary;

  /// Tertiary button label
  ///
  /// In en, this message translates to:
  /// **'Tertiary'**
  String get tertiary;

  /// Danger button label
  ///
  /// In en, this message translates to:
  /// **'Danger'**
  String get danger;

  /// Small button label
  ///
  /// In en, this message translates to:
  /// **'Small'**
  String get small;

  /// Medium button label
  ///
  /// In en, this message translates to:
  /// **'Medium'**
  String get medium;

  /// Large button label
  ///
  /// In en, this message translates to:
  /// **'Large'**
  String get large;

  /// Button with icon label
  ///
  /// In en, this message translates to:
  /// **'With Icon'**
  String get withIcon;

  /// Loading button label
  ///
  /// In en, this message translates to:
  /// **'Loading'**
  String get loadingButton;

  /// Disabled button label
  ///
  /// In en, this message translates to:
  /// **'Disabled'**
  String get disabled;

  /// Full width button label
  ///
  /// In en, this message translates to:
  /// **'Full Width'**
  String get fullWidth;

  /// Cards section title
  ///
  /// In en, this message translates to:
  /// **'Cards'**
  String get cards;

  /// Elevated card title
  ///
  /// In en, this message translates to:
  /// **'Elevated Card'**
  String get elevatedCard;

  /// Filled card title
  ///
  /// In en, this message translates to:
  /// **'Filled Card'**
  String get filledCard;

  /// Outlined card title
  ///
  /// In en, this message translates to:
  /// **'Outlined Card'**
  String get outlinedCard;

  /// Clickable card title
  ///
  /// In en, this message translates to:
  /// **'Clickable Card'**
  String get clickableCard;

  /// Text fields section title
  ///
  /// In en, this message translates to:
  /// **'Text Fields'**
  String get textFields;

  /// Chips section title
  ///
  /// In en, this message translates to:
  /// **'Chips'**
  String get chips;

  /// Avatars section title
  ///
  /// In en, this message translates to:
  /// **'Avatars'**
  String get avatars;

  /// Sample card content text
  ///
  /// In en, this message translates to:
  /// **'This is sample content inside the card.'**
  String get sampleCardContent;

  /// Default guest mode message
  ///
  /// In en, this message translates to:
  /// **'You\'re browsing as a guest with limited features.'**
  String get browsingAsGuest;

  /// Login benefits header
  ///
  /// In en, this message translates to:
  /// **'Login to unlock:'**
  String get loginToUnlock;

  /// Guest chip label
  ///
  /// In en, this message translates to:
  /// **'Guest'**
  String get guest;

  /// Default search placeholder
  ///
  /// In en, this message translates to:
  /// **'Search...'**
  String get searchPlaceholder;

  /// Details button or section
  ///
  /// In en, this message translates to:
  /// **'Details'**
  String get details;

  /// Try again button
  ///
  /// In en, this message translates to:
  /// **'Try Again'**
  String get tryAgain;

  /// Go home button
  ///
  /// In en, this message translates to:
  /// **'Go Home'**
  String get goHome;

  /// Go back button
  ///
  /// In en, this message translates to:
  /// **'Go Back'**
  String get goBack;

  /// 404 error title
  ///
  /// In en, this message translates to:
  /// **'404 - Page Not Found'**
  String get pageNotFound;

  /// 404 error description
  ///
  /// In en, this message translates to:
  /// **'The page you\'re looking for doesn\'t exist or has been moved.'**
  String get pageNotFoundDescription;

  /// 500 error title
  ///
  /// In en, this message translates to:
  /// **'500 - Server Error'**
  String get serverError;

  /// 500 error description
  ///
  /// In en, this message translates to:
  /// **'Something went wrong on our end. Please try again later.'**
  String get serverErrorDescription;

  /// Network error title
  ///
  /// In en, this message translates to:
  /// **'Network Error'**
  String get networkError;

  /// Network error description
  ///
  /// In en, this message translates to:
  /// **'Please check your internet connection and try again.'**
  String get networkErrorDescription;

  /// 401 error title
  ///
  /// In en, this message translates to:
  /// **'401 - Unauthorized'**
  String get unauthorized;

  /// 401 error description
  ///
  /// In en, this message translates to:
  /// **'You don\'t have permission to access this resource.'**
  String get unauthorizedDescription;

  /// Generic error title
  ///
  /// In en, this message translates to:
  /// **'Something went wrong'**
  String get somethingWentWrong;

  /// Generic error description
  ///
  /// In en, this message translates to:
  /// **'An unexpected error occurred. Please try again.'**
  String get unexpectedError;

  /// File upload click to browse text
  ///
  /// In en, this message translates to:
  /// **'or click to browse'**
  String get orClickToBrowse;

  /// Selected files label
  ///
  /// In en, this message translates to:
  /// **'Selected Files:'**
  String get selectedFiles;

  /// Error selecting files message
  ///
  /// In en, this message translates to:
  /// **'Error selecting files: {error}'**
  String errorSelectingFiles(String error);

  /// Drop image files message
  ///
  /// In en, this message translates to:
  /// **'Drop image files here'**
  String get dropImageFiles;

  /// Drop document files message
  ///
  /// In en, this message translates to:
  /// **'Drop document files here'**
  String get dropDocumentFiles;

  /// Drop image files with supported formats
  ///
  /// In en, this message translates to:
  /// **'Drop image files here\\n(JPG, PNG, GIF, WebP)'**
  String get dropImageFilesWithFormats;

  /// Drop document files with supported formats
  ///
  /// In en, this message translates to:
  /// **'Drop document files here\\n(PDF, DOC, DOCX, TXT)'**
  String get dropDocumentFilesWithFormats;

  /// Skip button on onboarding
  ///
  /// In en, this message translates to:
  /// **'Skip'**
  String get skip;

  /// Previous button on onboarding
  ///
  /// In en, this message translates to:
  /// **'Previous'**
  String get previous;

  /// Get started button on onboarding
  ///
  /// In en, this message translates to:
  /// **'Get Started'**
  String get getStarted;

  /// Next button on onboarding
  ///
  /// In en, this message translates to:
  /// **'Next'**
  String get next;

  /// Onboarding welcome title
  ///
  /// In en, this message translates to:
  /// **'Welcome to Flutter Starter'**
  String get welcomeToFlutterStarter;

  /// Onboarding welcome description
  ///
  /// In en, this message translates to:
  /// **'A production-ready Material 3 starter template with authentication, theming, and responsive navigation.'**
  String get onboardingWelcomeDescription;

  /// Onboarding Material 3 title
  ///
  /// In en, this message translates to:
  /// **'Material 3 Design'**
  String get onboardingMaterial3Title;

  /// Onboarding Material 3 description
  ///
  /// In en, this message translates to:
  /// **'Built with the latest Material Design system featuring dynamic color themes and modern components.'**
  String get onboardingMaterial3Description;

  /// Onboarding responsive title
  ///
  /// In en, this message translates to:
  /// **'Responsive Navigation'**
  String get onboardingResponsiveTitle;

  /// Onboarding responsive description
  ///
  /// In en, this message translates to:
  /// **'Adaptive navigation that works seamlessly across mobile, tablet, and desktop devices.'**
  String get onboardingResponsiveDescription;

  /// Onboarding state management title
  ///
  /// In en, this message translates to:
  /// **'State Management'**
  String get onboardingStateTitle;

  /// Onboarding state management description
  ///
  /// In en, this message translates to:
  /// **'Powered by Riverpod for predictable and scalable state management throughout the application.'**
  String get onboardingStateDescription;

  /// Onboarding features title
  ///
  /// In en, this message translates to:
  /// **'Ready-to-Use Features'**
  String get onboardingFeaturesTitle;

  /// Onboarding features description
  ///
  /// In en, this message translates to:
  /// **'Authentication, forms, file uploads, error handling, and more - all ready to customize for your needs.'**
  String get onboardingFeaturesDescription;

  /// UI component variants section title
  ///
  /// In en, this message translates to:
  /// **'Variants'**
  String get variants;

  /// UI component sizes section title
  ///
  /// In en, this message translates to:
  /// **'Sizes'**
  String get sizes;

  /// UI component states section title
  ///
  /// In en, this message translates to:
  /// **'States'**
  String get states;

  /// Standard text field label
  ///
  /// In en, this message translates to:
  /// **'Standard Text Field'**
  String get standardTextField;

  /// Text field placeholder
  ///
  /// In en, this message translates to:
  /// **'Enter some text'**
  String get enterSomeText;

  /// Text field helper text
  ///
  /// In en, this message translates to:
  /// **'This is helper text'**
  String get thisIsHelperText;

  /// Filled text field label
  ///
  /// In en, this message translates to:
  /// **'Filled Text Field'**
  String get filledTextField;

  /// Multi-line text field label
  ///
  /// In en, this message translates to:
  /// **'Multi-line Text Field'**
  String get multilineTextField;

  /// Multi-line text field placeholder
  ///
  /// In en, this message translates to:
  /// **'Enter multiple lines of text'**
  String get enterMultipleLinesOfText;

  /// Disabled text field label
  ///
  /// In en, this message translates to:
  /// **'Disabled Text Field'**
  String get disabledTextField;

  /// Filled variant label
  ///
  /// In en, this message translates to:
  /// **'Filled'**
  String get filled;

  /// Outlined variant label
  ///
  /// In en, this message translates to:
  /// **'Outlined'**
  String get outlined;

  /// Suggestion chip label
  ///
  /// In en, this message translates to:
  /// **'Suggestion'**
  String get suggestion;

  /// Filter chip label
  ///
  /// In en, this message translates to:
  /// **'Filter'**
  String get filter;

  /// Components with icons section title
  ///
  /// In en, this message translates to:
  /// **'With Icons'**
  String get withIcons;

  /// Chip with avatar label
  ///
  /// In en, this message translates to:
  /// **'With Avatar'**
  String get withAvatar;

  /// Deletable chip label
  ///
  /// In en, this message translates to:
  /// **'Deletable'**
  String get deletable;

  /// Component types section title
  ///
  /// In en, this message translates to:
  /// **'Types'**
  String get types;

  /// Avatar group section title
  ///
  /// In en, this message translates to:
  /// **'Avatar Group'**
  String get avatarGroup;

  /// File upload success message
  ///
  /// In en, this message translates to:
  /// **'File uploaded successfully!'**
  String get fileUploadedSuccessfully;

  /// Image upload success message
  ///
  /// In en, this message translates to:
  /// **'Image uploaded successfully!'**
  String get imageUploadedSuccessfully;

  /// Document upload success message
  ///
  /// In en, this message translates to:
  /// **'Document uploaded successfully!'**
  String get documentUploadedSuccessfully;

  /// Custom file upload success message
  ///
  /// In en, this message translates to:
  /// **'Custom file uploaded successfully!'**
  String get customFileUploadedSuccessfully;

  /// File upload success without preview
  ///
  /// In en, this message translates to:
  /// **'File uploaded (no preview)!'**
  String get fileUploadedNoPreview;

  /// Images section label
  ///
  /// In en, this message translates to:
  /// **'Images'**
  String get images;

  /// Documents section label
  ///
  /// In en, this message translates to:
  /// **'Documents'**
  String get documents;

  /// Image upload section title
  ///
  /// In en, this message translates to:
  /// **'Image Upload'**
  String get imageUpload;

  /// Image upload section description
  ///
  /// In en, this message translates to:
  /// **'Upload image files only (JPG, PNG, GIF, WebP)'**
  String get imageUploadDescription;

  /// Document upload section title
  ///
  /// In en, this message translates to:
  /// **'Document Upload'**
  String get documentUpload;

  /// Document upload section description
  ///
  /// In en, this message translates to:
  /// **'Upload document files (PDF, DOC, DOCX, TXT)'**
  String get documentUploadDescription;

  /// Multiple file upload section title
  ///
  /// In en, this message translates to:
  /// **'Multiple File Upload'**
  String get multipleFileUpload;

  /// Multiple file upload section description
  ///
  /// In en, this message translates to:
  /// **'Upload multiple files at once'**
  String get multipleFileUploadDescription;

  /// General file upload section title
  ///
  /// In en, this message translates to:
  /// **'General File Upload'**
  String get generalFileUpload;

  /// General file upload section description
  ///
  /// In en, this message translates to:
  /// **'Upload any type of file with size limit'**
  String get generalFileUploadDescription;

  /// No preview upload section title
  ///
  /// In en, this message translates to:
  /// **'No Preview Upload'**
  String get noPreviewUpload;

  /// No preview upload section description
  ///
  /// In en, this message translates to:
  /// **'File upload without showing selected files'**
  String get noPreviewUploadDescription;

  /// Custom extensions upload section title
  ///
  /// In en, this message translates to:
  /// **'Custom Extensions'**
  String get customExtensions;

  /// Custom extensions upload section description
  ///
  /// In en, this message translates to:
  /// **'Upload only specific file types'**
  String get customExtensionsDescription;

  /// General file drop zone hint
  ///
  /// In en, this message translates to:
  /// **'Drop any file here (max 5MB)'**
  String get dropAnyFileHere;

  /// Multiple file drop zone hint
  ///
  /// In en, this message translates to:
  /// **'Drop multiple files here (max 2MB each)'**
  String get dropMultipleFilesHere;

  /// Custom extension file drop zone hint
  ///
  /// In en, this message translates to:
  /// **'Drop JSON, XML, or CSV files here'**
  String get dropJSONXMLCSVFiles;

  /// No preview upload hint
  ///
  /// In en, this message translates to:
  /// **'Upload files without preview'**
  String get uploadFilesWithoutPreview;

  /// Upload statistics section title
  ///
  /// In en, this message translates to:
  /// **'Upload Statistics'**
  String get uploadStatistics;

  /// Total files statistic label
  ///
  /// In en, this message translates to:
  /// **'Total Files'**
  String get totalFiles;

  /// Total size statistic label
  ///
  /// In en, this message translates to:
  /// **'Total Size'**
  String get totalSize;

  /// Clear all files button label
  ///
  /// In en, this message translates to:
  /// **'Clear All Files'**
  String get clearAllFiles;

  /// All files cleared success message
  ///
  /// In en, this message translates to:
  /// **'All files cleared!'**
  String get allFilesCleared;

  /// Multiple files upload success message
  ///
  /// In en, this message translates to:
  /// **'{count, plural, =1{1 file uploaded successfully!} other{{count} files uploaded successfully!}}'**
  String multipleFilesUploadedSuccessfully(int count);

  /// Error showcase context name
  ///
  /// In en, this message translates to:
  /// **'Error Showcase'**
  String get errorShowcase;

  /// Error pages section title
  ///
  /// In en, this message translates to:
  /// **'Error Pages'**
  String get errorPages;

  /// Error dialogs and snackbars section title
  ///
  /// In en, this message translates to:
  /// **'Error Dialogs & Snackbars'**
  String get errorDialogsSnackbars;

  /// Async error handling section title
  ///
  /// In en, this message translates to:
  /// **'Async Error Handling'**
  String get asyncErrorHandling;

  /// Error boundaries section title
  ///
  /// In en, this message translates to:
  /// **'Error Boundaries'**
  String get errorBoundaries;

  /// Network error message demo title
  ///
  /// In en, this message translates to:
  /// **'Network Error Message'**
  String get networkErrorMessage;

  /// Network error message demo description
  ///
  /// In en, this message translates to:
  /// **'Shows network error handling'**
  String get networkErrorMessageDescription;

  /// Unauthorized error demo title
  ///
  /// In en, this message translates to:
  /// **'Unauthorized Error'**
  String get unauthorizedError;

  /// Unauthorized error demo description
  ///
  /// In en, this message translates to:
  /// **'Shows an authentication error'**
  String get unauthorizedErrorDescription;

  /// API error message demo title
  ///
  /// In en, this message translates to:
  /// **'API Error Message'**
  String get apiErrorMessage;

  /// API error message demo description
  ///
  /// In en, this message translates to:
  /// **'Shows API error handling'**
  String get apiErrorMessageDescription;

  /// Show error dialog demo title
  ///
  /// In en, this message translates to:
  /// **'Show Error Dialog'**
  String get showErrorDialog;

  /// Show error dialog demo description
  ///
  /// In en, this message translates to:
  /// **'Displays a modal error dialog'**
  String get showErrorDialogDescription;

  /// Show error snackbar demo title
  ///
  /// In en, this message translates to:
  /// **'Show Error SnackBar'**
  String get showErrorSnackbar;

  /// Show error snackbar demo description
  ///
  /// In en, this message translates to:
  /// **'Shows an error message at the bottom'**
  String get showErrorSnackbarDescription;

  /// Operation failed error title
  ///
  /// In en, this message translates to:
  /// **'Operation Failed'**
  String get operationFailed;

  /// Operation failed error message
  ///
  /// In en, this message translates to:
  /// **'The requested operation could not be completed. Please try again.'**
  String get operationFailedMessage;

  /// Changes saved success message
  ///
  /// In en, this message translates to:
  /// **'Changes saved successfully!'**
  String get changesSavedSuccessfully;

  /// Failed to save changes error message
  ///
  /// In en, this message translates to:
  /// **'Failed to save changes'**
  String get failedToSaveChanges;

  /// Async error demo dialog title
  ///
  /// In en, this message translates to:
  /// **'Async Error Demo'**
  String get asyncErrorDemo;

  /// Async error handling demo description
  ///
  /// In en, this message translates to:
  /// **'Handles errors in async operations'**
  String get asyncErrorHandlingDescription;

  /// Retrying async operation message
  ///
  /// In en, this message translates to:
  /// **'Retrying async operation...'**
  String get retryingAsyncOperation;

  /// Retrying operation message
  ///
  /// In en, this message translates to:
  /// **'Retrying operation...'**
  String get retryingOperation;

  /// Feature error boundary demo title
  ///
  /// In en, this message translates to:
  /// **'Feature Error Boundary'**
  String get featureErrorBoundary;

  /// Feature error boundary demo description
  ///
  /// In en, this message translates to:
  /// **'Wraps a feature with error handling'**
  String get featureErrorBoundaryDescription;

  /// Demo feature name
  ///
  /// In en, this message translates to:
  /// **'Demo Feature'**
  String get demoFeature;

  /// Feature error demo dialog title
  ///
  /// In en, this message translates to:
  /// **'Feature Error Demo'**
  String get featureErrorDemo;

  /// Error reporting section title
  ///
  /// In en, this message translates to:
  /// **'Error Reporting'**
  String get errorReporting;

  /// Report custom error demo title
  ///
  /// In en, this message translates to:
  /// **'Report Custom Error'**
  String get reportCustomError;

  /// Report custom error demo description
  ///
  /// In en, this message translates to:
  /// **'Demonstrates error reporting'**
  String get reportCustomErrorDescription;

  /// Error reported success message
  ///
  /// In en, this message translates to:
  /// **'Error reported! Check debug console.'**
  String get errorReportedCheckConsole;

  /// Custom error exception message
  ///
  /// In en, this message translates to:
  /// **'Custom error for demonstration'**
  String get customErrorForDemo;

  /// Simulated widget error exception message
  ///
  /// In en, this message translates to:
  /// **'Simulated widget error for demonstration'**
  String get simulatedWidgetErrorForDemo;

  /// Socket exception error message
  ///
  /// In en, this message translates to:
  /// **'SocketException: Failed host lookup'**
  String get socketExceptionFailedHostLookup;

  /// Redirecting to login message
  ///
  /// In en, this message translates to:
  /// **'Redirecting to login...'**
  String get redirectingToLogin;

  /// Widget error demo text
  ///
  /// In en, this message translates to:
  /// **'This widget will throw an error...'**
  String get thisWidgetWillThrowError;
}

class _AppLocalizationsDelegate
    extends LocalizationsDelegate<AppLocalizations> {
  const _AppLocalizationsDelegate();

  @override
  Future<AppLocalizations> load(Locale locale) {
    return SynchronousFuture<AppLocalizations>(lookupAppLocalizations(locale));
  }

  @override
  bool isSupported(Locale locale) =>
      <String>['en', 'es', 'fr'].contains(locale.languageCode);

  @override
  bool shouldReload(_AppLocalizationsDelegate old) => false;
}

AppLocalizations lookupAppLocalizations(Locale locale) {
  // Lookup logic when only language code is specified.
  switch (locale.languageCode) {
    case 'en':
      return AppLocalizationsEn();
    case 'es':
      return AppLocalizationsEs();
    case 'fr':
      return AppLocalizationsFr();
  }

  throw FlutterError(
    'AppLocalizations.delegate failed to load unsupported locale "$locale". This is likely '
    'an issue with the localizations generation tool. Please file an issue '
    'on GitHub with a reproducible sample app and the gen-l10n configuration '
    'that was used.',
  );
}
