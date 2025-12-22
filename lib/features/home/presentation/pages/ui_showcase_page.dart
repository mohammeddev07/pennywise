import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../shared/widgets/ui_components/ui_components.dart';
import '../../../../l10n/app_localizations.dart';

class UIShowcasePage extends ConsumerStatefulWidget {
  const UIShowcasePage({super.key});

  @override
  ConsumerState<UIShowcasePage> createState() => _UIShowcasePageState();
}

class _UIShowcasePageState extends ConsumerState<UIShowcasePage> {
  final _emailController = TextEditingController();
  final _passwordController = TextEditingController();
  bool _chipSelected = false;
  
  @override
  void dispose() {
    _emailController.dispose();
    _passwordController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(AppLocalizations.of(context).uiComponentsShowcase),
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            _buildSection(
              AppLocalizations.of(context).buttons,
              Column(
                children: [
                  _buildSubsection(
                    AppLocalizations.of(context).variants,
                    Wrap(
                      spacing: 8,
                      runSpacing: 8,
                      children: [
                        CustomButton(
                          label: AppLocalizations.of(context).primary,
                          onPressed: () {},
                          variant: ButtonVariant.primary,
                        ),
                        CustomButton(
                          label: AppLocalizations.of(context).secondary,
                          onPressed: () {},
                          variant: ButtonVariant.secondary,
                        ),
                        CustomButton(
                          label: AppLocalizations.of(context).tertiary,
                          onPressed: () {},
                          variant: ButtonVariant.tertiary,
                        ),
                        CustomButton(
                          label: AppLocalizations.of(context).danger,
                          onPressed: () {},
                          variant: ButtonVariant.danger,
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 16),
                  _buildSubsection(
                    AppLocalizations.of(context).sizes,
                    Wrap(
                      spacing: 8,
                      runSpacing: 8,
                      children: [
                        CustomButton(
                          label: AppLocalizations.of(context).small,
                          onPressed: () {},
                          size: ButtonSize.small,
                        ),
                        CustomButton(
                          label: AppLocalizations.of(context).medium,
                          onPressed: () {},
                          size: ButtonSize.medium,
                        ),
                        CustomButton(
                          label: AppLocalizations.of(context).large,
                          onPressed: () {},
                          size: ButtonSize.large,
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 16),
                  _buildSubsection(
                    AppLocalizations.of(context).states,
                    Wrap(
                      spacing: 8,
                      runSpacing: 8,
                      children: [
                        CustomButton(
                          label: AppLocalizations.of(context).withIcon,
                          icon: Icons.add,
                          onPressed: () {},
                        ),
                        CustomButton(
                          label: AppLocalizations.of(context).loadingButton,
                          isLoading: true,
                        ),
                        CustomButton(
                          label: AppLocalizations.of(context).disabled,
                          onPressed: null,
                        ),
                        CustomButton(
                          label: AppLocalizations.of(context).fullWidth,
                          onPressed: () {},
                          fullWidth: true,
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
            const Divider(height: 48),
            
            _buildSection(
              AppLocalizations.of(context).cards,
              Wrap(
                spacing: 16,
                runSpacing: 16,
                children: [
                  CustomCard(
                    variant: CardVariant.elevated,
                    child: _buildCardContent(AppLocalizations.of(context).elevatedCard),
                  ),
                  CustomCard(
                    variant: CardVariant.filled,
                    child: _buildCardContent(AppLocalizations.of(context).filledCard),
                  ),
                  CustomCard(
                    variant: CardVariant.outlined,
                    child: _buildCardContent(AppLocalizations.of(context).outlinedCard),
                  ),
                  CustomCard(
                    variant: CardVariant.elevated,
                    onTap: () {},
                    child: _buildCardContent(AppLocalizations.of(context).clickableCard),
                  ),
                ],
              ),
            ),
            const Divider(height: 48),
            
            _buildSection(
              AppLocalizations.of(context).textFields,
              Column(
                children: [
                  CustomTextField(
                    label: AppLocalizations.of(context).standardTextField,
                    hint: AppLocalizations.of(context).enterSomeText,
                    helperText: AppLocalizations.of(context).thisIsHelperText,
                  ),
                  const SizedBox(height: 16),
                  CustomTextField(
                    label: AppLocalizations.of(context).filledTextField,
                    variant: TextFieldVariant.filled,
                    prefixIcon: const Icon(Icons.search),
                  ),
                  const SizedBox(height: 16),
                  EmailTextField(
                    controller: _emailController,
                  ),
                  const SizedBox(height: 16),
                  PasswordTextField(
                    controller: _passwordController,
                  ),
                  const SizedBox(height: 16),
                  CustomTextField(
                    label: AppLocalizations.of(context).multilineTextField,
                    hint: AppLocalizations.of(context).enterMultipleLinesOfText,
                    maxLines: 4,
                  ),
                  const SizedBox(height: 16),
                  CustomTextField(
                    label: AppLocalizations.of(context).disabledTextField,
                    enabled: false,
                  ),
                ],
              ),
            ),
            const Divider(height: 48),
            
            _buildSection(
              AppLocalizations.of(context).chips,
              Column(
                children: [
                  _buildSubsection(
                    AppLocalizations.of(context).variants,
                    ChipGroup(
                      chips: [
                        CustomChip(
                          label: AppLocalizations.of(context).filled,
                          variant: ChipVariant.filled,
                          onPressed: () {},
                        ),
                        CustomChip(
                          label: AppLocalizations.of(context).outlined,
                          variant: ChipVariant.outlined,
                          onPressed: () {},
                        ),
                        CustomChip(
                          label: AppLocalizations.of(context).suggestion,
                          variant: ChipVariant.suggestion,
                          onPressed: () {},
                        ),
                        CustomChip(
                          label: AppLocalizations.of(context).filter,
                          variant: ChipVariant.filter,
                          selected: _chipSelected,
                          onPressed: () {
                            setState(() {
                              _chipSelected = !_chipSelected;
                            });
                          },
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 16),
                  _buildSubsection(
                    AppLocalizations.of(context).sizes,
                    ChipGroup(
                      chips: [
                        CustomChip(
                          label: AppLocalizations.of(context).small,
                          size: ChipSize.small,
                        ),
                        CustomChip(
                          label: AppLocalizations.of(context).medium,
                          size: ChipSize.medium,
                        ),
                        CustomChip(
                          label: AppLocalizations.of(context).large,
                          size: ChipSize.large,
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 16),
                  _buildSubsection(
                    AppLocalizations.of(context).withIcons,
                    ChipGroup(
                      chips: [
                        CustomChip(
                          label: AppLocalizations.of(context).withAvatar,
                          avatar: const Icon(Icons.person, size: 18),
                          onPressed: () {},
                        ),
                        CustomChip(
                          label: AppLocalizations.of(context).deletable,
                          variant: ChipVariant.filter,
                          onDeleted: () {},
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
            const Divider(height: 48),
            
            _buildSection(
              AppLocalizations.of(context).avatars,
              Column(
                children: [
                  _buildSubsection(
                    AppLocalizations.of(context).sizes,
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                      children: [
                        const CustomAvatar(
                          name: 'John Doe',
                          size: AvatarSize.small,
                        ),
                        const CustomAvatar(
                          name: 'Jane Smith',
                          size: AvatarSize.medium,
                        ),
                        const CustomAvatar(
                          name: 'Bob Johnson',
                          size: AvatarSize.large,
                        ),
                        const CustomAvatar(
                          name: 'Alice Brown',
                          size: AvatarSize.extraLarge,
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 24),
                  _buildSubsection(
                    AppLocalizations.of(context).types,
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                      children: [
                        const CustomAvatar(
                          imageUrl: 'https://ui-avatars.com/api/?name=Showcase+User&background=random&color=fff&size=150&format=png',
                        ),
                        const CustomAvatar(
                          name: 'John Doe',
                        ),
                        const CustomAvatar(
                          icon: Icons.person,
                        ),
                        CustomAvatar(
                          name: 'Jane',
                          badge: Container(
                            width: 12,
                            height: 12,
                            decoration: const BoxDecoration(
                              color: Colors.green,
                              shape: BoxShape.circle,
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 24),
                  _buildSubsection(
                    AppLocalizations.of(context).avatarGroup,
                    AvatarGroup(
                      avatars: List.generate(
                        8,
                        (index) => CustomAvatar(
                          imageUrl: 'https://ui-avatars.com/api/?name=User+${index + 1}&background=random&color=fff&size=150&format=png',
                        ),
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
  
  Widget _buildSection(String title, Widget child) {
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
        child,
      ],
    );
  }
  
  Widget _buildSubsection(String title, Widget child) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          title,
          style: Theme.of(context).textTheme.titleMedium?.copyWith(
            color: Theme.of(context).colorScheme.onSurfaceVariant,
          ),
        ),
        const SizedBox(height: 8),
        child,
      ],
    );
  }
  
  Widget _buildCardContent(String title) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          title,
          style: Theme.of(context).textTheme.titleMedium,
        ),
        const SizedBox(height: 8),
        Text(
          AppLocalizations.of(context).sampleCardContent,
          style: Theme.of(context).textTheme.bodyMedium,
        ),
      ],
    );
  }
}