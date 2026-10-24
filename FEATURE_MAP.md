# FEATURE MAP

Index of domain packages, Spring Security endpoints, database entities, and test commands.

## Feature Modules

### AI Filter Proposals

- **Package**: `com.example.app.domain.transaction.query.ai`
- **Primary Classes**: `GeminiFilterClient`, `TransactionFilterController`
- **Prompts**: `src/main/resources/prompts/ai-filter-proposal.md`
- **Entities / Tables**: Read-only query over `transactions`
- **Verification**: `./gradlew test --tests "com.example.app.domain.transaction.query.ai.*"`

<!-- Append new feature modules here as they are added -->
