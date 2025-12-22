// coverage:ignore-file
// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint
// ignore_for_file: unused_element, deprecated_member_use, deprecated_member_use_from_same_package, use_function_type_syntax_for_parameters, unnecessary_const, avoid_init_to_null, invalid_override_different_default_values_named, prefer_expression_function_bodies, annotate_overrides, invalid_annotation_target, unnecessary_question_mark

part of 'dashboard_stats.dart';

// **************************************************************************
// FreezedGenerator
// **************************************************************************

T _$identity<T>(T value) => value;

final _privateConstructorUsedError = UnsupportedError(
  'It seems like you constructed your class using `MyClass._()`. This constructor is only meant to be used by freezed and you are not supposed to need it nor use it.\nPlease check the documentation here for more information: https://github.com/rrousselGit/freezed#adding-getters-and-methods-to-our-models',
);

DashboardStats _$DashboardStatsFromJson(Map<String, dynamic> json) {
  return _DashboardStats.fromJson(json);
}

/// @nodoc
mixin _$DashboardStats {
  int get totalUsers => throw _privateConstructorUsedError;
  int get activeUsers => throw _privateConstructorUsedError;
  int get totalRevenue => throw _privateConstructorUsedError;
  double get growthRate => throw _privateConstructorUsedError;
  List<ChartData> get userGrowthData => throw _privateConstructorUsedError;
  List<ChartData> get revenueData => throw _privateConstructorUsedError;
  List<ActivityItem> get recentActivities => throw _privateConstructorUsedError;

  /// Serializes this DashboardStats to a JSON map.
  Map<String, dynamic> toJson() => throw _privateConstructorUsedError;

  /// Create a copy of DashboardStats
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  $DashboardStatsCopyWith<DashboardStats> get copyWith =>
      throw _privateConstructorUsedError;
}

/// @nodoc
abstract class $DashboardStatsCopyWith<$Res> {
  factory $DashboardStatsCopyWith(
    DashboardStats value,
    $Res Function(DashboardStats) then,
  ) = _$DashboardStatsCopyWithImpl<$Res, DashboardStats>;
  @useResult
  $Res call({
    int totalUsers,
    int activeUsers,
    int totalRevenue,
    double growthRate,
    List<ChartData> userGrowthData,
    List<ChartData> revenueData,
    List<ActivityItem> recentActivities,
  });
}

/// @nodoc
class _$DashboardStatsCopyWithImpl<$Res, $Val extends DashboardStats>
    implements $DashboardStatsCopyWith<$Res> {
  _$DashboardStatsCopyWithImpl(this._value, this._then);

  // ignore: unused_field
  final $Val _value;
  // ignore: unused_field
  final $Res Function($Val) _then;

  /// Create a copy of DashboardStats
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  @override
  $Res call({
    Object? totalUsers = null,
    Object? activeUsers = null,
    Object? totalRevenue = null,
    Object? growthRate = null,
    Object? userGrowthData = null,
    Object? revenueData = null,
    Object? recentActivities = null,
  }) {
    return _then(
      _value.copyWith(
            totalUsers: null == totalUsers
                ? _value.totalUsers
                : totalUsers // ignore: cast_nullable_to_non_nullable
                      as int,
            activeUsers: null == activeUsers
                ? _value.activeUsers
                : activeUsers // ignore: cast_nullable_to_non_nullable
                      as int,
            totalRevenue: null == totalRevenue
                ? _value.totalRevenue
                : totalRevenue // ignore: cast_nullable_to_non_nullable
                      as int,
            growthRate: null == growthRate
                ? _value.growthRate
                : growthRate // ignore: cast_nullable_to_non_nullable
                      as double,
            userGrowthData: null == userGrowthData
                ? _value.userGrowthData
                : userGrowthData // ignore: cast_nullable_to_non_nullable
                      as List<ChartData>,
            revenueData: null == revenueData
                ? _value.revenueData
                : revenueData // ignore: cast_nullable_to_non_nullable
                      as List<ChartData>,
            recentActivities: null == recentActivities
                ? _value.recentActivities
                : recentActivities // ignore: cast_nullable_to_non_nullable
                      as List<ActivityItem>,
          )
          as $Val,
    );
  }
}

/// @nodoc
abstract class _$$DashboardStatsImplCopyWith<$Res>
    implements $DashboardStatsCopyWith<$Res> {
  factory _$$DashboardStatsImplCopyWith(
    _$DashboardStatsImpl value,
    $Res Function(_$DashboardStatsImpl) then,
  ) = __$$DashboardStatsImplCopyWithImpl<$Res>;
  @override
  @useResult
  $Res call({
    int totalUsers,
    int activeUsers,
    int totalRevenue,
    double growthRate,
    List<ChartData> userGrowthData,
    List<ChartData> revenueData,
    List<ActivityItem> recentActivities,
  });
}

/// @nodoc
class __$$DashboardStatsImplCopyWithImpl<$Res>
    extends _$DashboardStatsCopyWithImpl<$Res, _$DashboardStatsImpl>
    implements _$$DashboardStatsImplCopyWith<$Res> {
  __$$DashboardStatsImplCopyWithImpl(
    _$DashboardStatsImpl _value,
    $Res Function(_$DashboardStatsImpl) _then,
  ) : super(_value, _then);

  /// Create a copy of DashboardStats
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  @override
  $Res call({
    Object? totalUsers = null,
    Object? activeUsers = null,
    Object? totalRevenue = null,
    Object? growthRate = null,
    Object? userGrowthData = null,
    Object? revenueData = null,
    Object? recentActivities = null,
  }) {
    return _then(
      _$DashboardStatsImpl(
        totalUsers: null == totalUsers
            ? _value.totalUsers
            : totalUsers // ignore: cast_nullable_to_non_nullable
                  as int,
        activeUsers: null == activeUsers
            ? _value.activeUsers
            : activeUsers // ignore: cast_nullable_to_non_nullable
                  as int,
        totalRevenue: null == totalRevenue
            ? _value.totalRevenue
            : totalRevenue // ignore: cast_nullable_to_non_nullable
                  as int,
        growthRate: null == growthRate
            ? _value.growthRate
            : growthRate // ignore: cast_nullable_to_non_nullable
                  as double,
        userGrowthData: null == userGrowthData
            ? _value._userGrowthData
            : userGrowthData // ignore: cast_nullable_to_non_nullable
                  as List<ChartData>,
        revenueData: null == revenueData
            ? _value._revenueData
            : revenueData // ignore: cast_nullable_to_non_nullable
                  as List<ChartData>,
        recentActivities: null == recentActivities
            ? _value._recentActivities
            : recentActivities // ignore: cast_nullable_to_non_nullable
                  as List<ActivityItem>,
      ),
    );
  }
}

/// @nodoc
@JsonSerializable()
class _$DashboardStatsImpl implements _DashboardStats {
  const _$DashboardStatsImpl({
    required this.totalUsers,
    required this.activeUsers,
    required this.totalRevenue,
    required this.growthRate,
    required final List<ChartData> userGrowthData,
    required final List<ChartData> revenueData,
    required final List<ActivityItem> recentActivities,
  }) : _userGrowthData = userGrowthData,
       _revenueData = revenueData,
       _recentActivities = recentActivities;

  factory _$DashboardStatsImpl.fromJson(Map<String, dynamic> json) =>
      _$$DashboardStatsImplFromJson(json);

  @override
  final int totalUsers;
  @override
  final int activeUsers;
  @override
  final int totalRevenue;
  @override
  final double growthRate;
  final List<ChartData> _userGrowthData;
  @override
  List<ChartData> get userGrowthData {
    if (_userGrowthData is EqualUnmodifiableListView) return _userGrowthData;
    // ignore: implicit_dynamic_type
    return EqualUnmodifiableListView(_userGrowthData);
  }

  final List<ChartData> _revenueData;
  @override
  List<ChartData> get revenueData {
    if (_revenueData is EqualUnmodifiableListView) return _revenueData;
    // ignore: implicit_dynamic_type
    return EqualUnmodifiableListView(_revenueData);
  }

  final List<ActivityItem> _recentActivities;
  @override
  List<ActivityItem> get recentActivities {
    if (_recentActivities is EqualUnmodifiableListView)
      return _recentActivities;
    // ignore: implicit_dynamic_type
    return EqualUnmodifiableListView(_recentActivities);
  }

  @override
  String toString() {
    return 'DashboardStats(totalUsers: $totalUsers, activeUsers: $activeUsers, totalRevenue: $totalRevenue, growthRate: $growthRate, userGrowthData: $userGrowthData, revenueData: $revenueData, recentActivities: $recentActivities)';
  }

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is _$DashboardStatsImpl &&
            (identical(other.totalUsers, totalUsers) ||
                other.totalUsers == totalUsers) &&
            (identical(other.activeUsers, activeUsers) ||
                other.activeUsers == activeUsers) &&
            (identical(other.totalRevenue, totalRevenue) ||
                other.totalRevenue == totalRevenue) &&
            (identical(other.growthRate, growthRate) ||
                other.growthRate == growthRate) &&
            const DeepCollectionEquality().equals(
              other._userGrowthData,
              _userGrowthData,
            ) &&
            const DeepCollectionEquality().equals(
              other._revenueData,
              _revenueData,
            ) &&
            const DeepCollectionEquality().equals(
              other._recentActivities,
              _recentActivities,
            ));
  }

  @JsonKey(includeFromJson: false, includeToJson: false)
  @override
  int get hashCode => Object.hash(
    runtimeType,
    totalUsers,
    activeUsers,
    totalRevenue,
    growthRate,
    const DeepCollectionEquality().hash(_userGrowthData),
    const DeepCollectionEquality().hash(_revenueData),
    const DeepCollectionEquality().hash(_recentActivities),
  );

  /// Create a copy of DashboardStats
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  @override
  @pragma('vm:prefer-inline')
  _$$DashboardStatsImplCopyWith<_$DashboardStatsImpl> get copyWith =>
      __$$DashboardStatsImplCopyWithImpl<_$DashboardStatsImpl>(
        this,
        _$identity,
      );

  @override
  Map<String, dynamic> toJson() {
    return _$$DashboardStatsImplToJson(this);
  }
}

abstract class _DashboardStats implements DashboardStats {
  const factory _DashboardStats({
    required final int totalUsers,
    required final int activeUsers,
    required final int totalRevenue,
    required final double growthRate,
    required final List<ChartData> userGrowthData,
    required final List<ChartData> revenueData,
    required final List<ActivityItem> recentActivities,
  }) = _$DashboardStatsImpl;

  factory _DashboardStats.fromJson(Map<String, dynamic> json) =
      _$DashboardStatsImpl.fromJson;

  @override
  int get totalUsers;
  @override
  int get activeUsers;
  @override
  int get totalRevenue;
  @override
  double get growthRate;
  @override
  List<ChartData> get userGrowthData;
  @override
  List<ChartData> get revenueData;
  @override
  List<ActivityItem> get recentActivities;

  /// Create a copy of DashboardStats
  /// with the given fields replaced by the non-null parameter values.
  @override
  @JsonKey(includeFromJson: false, includeToJson: false)
  _$$DashboardStatsImplCopyWith<_$DashboardStatsImpl> get copyWith =>
      throw _privateConstructorUsedError;
}

ChartData _$ChartDataFromJson(Map<String, dynamic> json) {
  return _ChartData.fromJson(json);
}

/// @nodoc
mixin _$ChartData {
  String get label => throw _privateConstructorUsedError;
  double get value => throw _privateConstructorUsedError;
  String? get color => throw _privateConstructorUsedError;

  /// Serializes this ChartData to a JSON map.
  Map<String, dynamic> toJson() => throw _privateConstructorUsedError;

  /// Create a copy of ChartData
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  $ChartDataCopyWith<ChartData> get copyWith =>
      throw _privateConstructorUsedError;
}

/// @nodoc
abstract class $ChartDataCopyWith<$Res> {
  factory $ChartDataCopyWith(ChartData value, $Res Function(ChartData) then) =
      _$ChartDataCopyWithImpl<$Res, ChartData>;
  @useResult
  $Res call({String label, double value, String? color});
}

/// @nodoc
class _$ChartDataCopyWithImpl<$Res, $Val extends ChartData>
    implements $ChartDataCopyWith<$Res> {
  _$ChartDataCopyWithImpl(this._value, this._then);

  // ignore: unused_field
  final $Val _value;
  // ignore: unused_field
  final $Res Function($Val) _then;

  /// Create a copy of ChartData
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  @override
  $Res call({
    Object? label = null,
    Object? value = null,
    Object? color = freezed,
  }) {
    return _then(
      _value.copyWith(
            label: null == label
                ? _value.label
                : label // ignore: cast_nullable_to_non_nullable
                      as String,
            value: null == value
                ? _value.value
                : value // ignore: cast_nullable_to_non_nullable
                      as double,
            color: freezed == color
                ? _value.color
                : color // ignore: cast_nullable_to_non_nullable
                      as String?,
          )
          as $Val,
    );
  }
}

/// @nodoc
abstract class _$$ChartDataImplCopyWith<$Res>
    implements $ChartDataCopyWith<$Res> {
  factory _$$ChartDataImplCopyWith(
    _$ChartDataImpl value,
    $Res Function(_$ChartDataImpl) then,
  ) = __$$ChartDataImplCopyWithImpl<$Res>;
  @override
  @useResult
  $Res call({String label, double value, String? color});
}

/// @nodoc
class __$$ChartDataImplCopyWithImpl<$Res>
    extends _$ChartDataCopyWithImpl<$Res, _$ChartDataImpl>
    implements _$$ChartDataImplCopyWith<$Res> {
  __$$ChartDataImplCopyWithImpl(
    _$ChartDataImpl _value,
    $Res Function(_$ChartDataImpl) _then,
  ) : super(_value, _then);

  /// Create a copy of ChartData
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  @override
  $Res call({
    Object? label = null,
    Object? value = null,
    Object? color = freezed,
  }) {
    return _then(
      _$ChartDataImpl(
        label: null == label
            ? _value.label
            : label // ignore: cast_nullable_to_non_nullable
                  as String,
        value: null == value
            ? _value.value
            : value // ignore: cast_nullable_to_non_nullable
                  as double,
        color: freezed == color
            ? _value.color
            : color // ignore: cast_nullable_to_non_nullable
                  as String?,
      ),
    );
  }
}

/// @nodoc
@JsonSerializable()
class _$ChartDataImpl implements _ChartData {
  const _$ChartDataImpl({required this.label, required this.value, this.color});

  factory _$ChartDataImpl.fromJson(Map<String, dynamic> json) =>
      _$$ChartDataImplFromJson(json);

  @override
  final String label;
  @override
  final double value;
  @override
  final String? color;

  @override
  String toString() {
    return 'ChartData(label: $label, value: $value, color: $color)';
  }

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is _$ChartDataImpl &&
            (identical(other.label, label) || other.label == label) &&
            (identical(other.value, value) || other.value == value) &&
            (identical(other.color, color) || other.color == color));
  }

  @JsonKey(includeFromJson: false, includeToJson: false)
  @override
  int get hashCode => Object.hash(runtimeType, label, value, color);

  /// Create a copy of ChartData
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  @override
  @pragma('vm:prefer-inline')
  _$$ChartDataImplCopyWith<_$ChartDataImpl> get copyWith =>
      __$$ChartDataImplCopyWithImpl<_$ChartDataImpl>(this, _$identity);

  @override
  Map<String, dynamic> toJson() {
    return _$$ChartDataImplToJson(this);
  }
}

abstract class _ChartData implements ChartData {
  const factory _ChartData({
    required final String label,
    required final double value,
    final String? color,
  }) = _$ChartDataImpl;

  factory _ChartData.fromJson(Map<String, dynamic> json) =
      _$ChartDataImpl.fromJson;

  @override
  String get label;
  @override
  double get value;
  @override
  String? get color;

  /// Create a copy of ChartData
  /// with the given fields replaced by the non-null parameter values.
  @override
  @JsonKey(includeFromJson: false, includeToJson: false)
  _$$ChartDataImplCopyWith<_$ChartDataImpl> get copyWith =>
      throw _privateConstructorUsedError;
}

ActivityItem _$ActivityItemFromJson(Map<String, dynamic> json) {
  return _ActivityItem.fromJson(json);
}

/// @nodoc
mixin _$ActivityItem {
  String get id => throw _privateConstructorUsedError;
  String get description => throw _privateConstructorUsedError;
  String get userId => throw _privateConstructorUsedError;
  String get userName => throw _privateConstructorUsedError;
  DateTime get timestamp => throw _privateConstructorUsedError;
  String get type => throw _privateConstructorUsedError;

  /// Serializes this ActivityItem to a JSON map.
  Map<String, dynamic> toJson() => throw _privateConstructorUsedError;

  /// Create a copy of ActivityItem
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  $ActivityItemCopyWith<ActivityItem> get copyWith =>
      throw _privateConstructorUsedError;
}

/// @nodoc
abstract class $ActivityItemCopyWith<$Res> {
  factory $ActivityItemCopyWith(
    ActivityItem value,
    $Res Function(ActivityItem) then,
  ) = _$ActivityItemCopyWithImpl<$Res, ActivityItem>;
  @useResult
  $Res call({
    String id,
    String description,
    String userId,
    String userName,
    DateTime timestamp,
    String type,
  });
}

/// @nodoc
class _$ActivityItemCopyWithImpl<$Res, $Val extends ActivityItem>
    implements $ActivityItemCopyWith<$Res> {
  _$ActivityItemCopyWithImpl(this._value, this._then);

  // ignore: unused_field
  final $Val _value;
  // ignore: unused_field
  final $Res Function($Val) _then;

  /// Create a copy of ActivityItem
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  @override
  $Res call({
    Object? id = null,
    Object? description = null,
    Object? userId = null,
    Object? userName = null,
    Object? timestamp = null,
    Object? type = null,
  }) {
    return _then(
      _value.copyWith(
            id: null == id
                ? _value.id
                : id // ignore: cast_nullable_to_non_nullable
                      as String,
            description: null == description
                ? _value.description
                : description // ignore: cast_nullable_to_non_nullable
                      as String,
            userId: null == userId
                ? _value.userId
                : userId // ignore: cast_nullable_to_non_nullable
                      as String,
            userName: null == userName
                ? _value.userName
                : userName // ignore: cast_nullable_to_non_nullable
                      as String,
            timestamp: null == timestamp
                ? _value.timestamp
                : timestamp // ignore: cast_nullable_to_non_nullable
                      as DateTime,
            type: null == type
                ? _value.type
                : type // ignore: cast_nullable_to_non_nullable
                      as String,
          )
          as $Val,
    );
  }
}

/// @nodoc
abstract class _$$ActivityItemImplCopyWith<$Res>
    implements $ActivityItemCopyWith<$Res> {
  factory _$$ActivityItemImplCopyWith(
    _$ActivityItemImpl value,
    $Res Function(_$ActivityItemImpl) then,
  ) = __$$ActivityItemImplCopyWithImpl<$Res>;
  @override
  @useResult
  $Res call({
    String id,
    String description,
    String userId,
    String userName,
    DateTime timestamp,
    String type,
  });
}

/// @nodoc
class __$$ActivityItemImplCopyWithImpl<$Res>
    extends _$ActivityItemCopyWithImpl<$Res, _$ActivityItemImpl>
    implements _$$ActivityItemImplCopyWith<$Res> {
  __$$ActivityItemImplCopyWithImpl(
    _$ActivityItemImpl _value,
    $Res Function(_$ActivityItemImpl) _then,
  ) : super(_value, _then);

  /// Create a copy of ActivityItem
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  @override
  $Res call({
    Object? id = null,
    Object? description = null,
    Object? userId = null,
    Object? userName = null,
    Object? timestamp = null,
    Object? type = null,
  }) {
    return _then(
      _$ActivityItemImpl(
        id: null == id
            ? _value.id
            : id // ignore: cast_nullable_to_non_nullable
                  as String,
        description: null == description
            ? _value.description
            : description // ignore: cast_nullable_to_non_nullable
                  as String,
        userId: null == userId
            ? _value.userId
            : userId // ignore: cast_nullable_to_non_nullable
                  as String,
        userName: null == userName
            ? _value.userName
            : userName // ignore: cast_nullable_to_non_nullable
                  as String,
        timestamp: null == timestamp
            ? _value.timestamp
            : timestamp // ignore: cast_nullable_to_non_nullable
                  as DateTime,
        type: null == type
            ? _value.type
            : type // ignore: cast_nullable_to_non_nullable
                  as String,
      ),
    );
  }
}

/// @nodoc
@JsonSerializable()
class _$ActivityItemImpl implements _ActivityItem {
  const _$ActivityItemImpl({
    required this.id,
    required this.description,
    required this.userId,
    required this.userName,
    required this.timestamp,
    required this.type,
  });

  factory _$ActivityItemImpl.fromJson(Map<String, dynamic> json) =>
      _$$ActivityItemImplFromJson(json);

  @override
  final String id;
  @override
  final String description;
  @override
  final String userId;
  @override
  final String userName;
  @override
  final DateTime timestamp;
  @override
  final String type;

  @override
  String toString() {
    return 'ActivityItem(id: $id, description: $description, userId: $userId, userName: $userName, timestamp: $timestamp, type: $type)';
  }

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is _$ActivityItemImpl &&
            (identical(other.id, id) || other.id == id) &&
            (identical(other.description, description) ||
                other.description == description) &&
            (identical(other.userId, userId) || other.userId == userId) &&
            (identical(other.userName, userName) ||
                other.userName == userName) &&
            (identical(other.timestamp, timestamp) ||
                other.timestamp == timestamp) &&
            (identical(other.type, type) || other.type == type));
  }

  @JsonKey(includeFromJson: false, includeToJson: false)
  @override
  int get hashCode => Object.hash(
    runtimeType,
    id,
    description,
    userId,
    userName,
    timestamp,
    type,
  );

  /// Create a copy of ActivityItem
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  @override
  @pragma('vm:prefer-inline')
  _$$ActivityItemImplCopyWith<_$ActivityItemImpl> get copyWith =>
      __$$ActivityItemImplCopyWithImpl<_$ActivityItemImpl>(this, _$identity);

  @override
  Map<String, dynamic> toJson() {
    return _$$ActivityItemImplToJson(this);
  }
}

abstract class _ActivityItem implements ActivityItem {
  const factory _ActivityItem({
    required final String id,
    required final String description,
    required final String userId,
    required final String userName,
    required final DateTime timestamp,
    required final String type,
  }) = _$ActivityItemImpl;

  factory _ActivityItem.fromJson(Map<String, dynamic> json) =
      _$ActivityItemImpl.fromJson;

  @override
  String get id;
  @override
  String get description;
  @override
  String get userId;
  @override
  String get userName;
  @override
  DateTime get timestamp;
  @override
  String get type;

  /// Create a copy of ActivityItem
  /// with the given fields replaced by the non-null parameter values.
  @override
  @JsonKey(includeFromJson: false, includeToJson: false)
  _$$ActivityItemImplCopyWith<_$ActivityItemImpl> get copyWith =>
      throw _privateConstructorUsedError;
}
