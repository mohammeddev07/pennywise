# Working agreement for this repository

Conventions this project actually follows. Read before branching, committing, or touching CI/Render config.

## Stack

- Java 21, Spring Boot 3.5.9, Gradle.
- Postgres (Flyway migrations), Spring Data JPA.
- OAuth2 resource server + Spring Security.
- Deployed on Render's free tier.

## Branching and commits

- Branch off `origin/develop` - always fetch first and branch fresh, don't stack a new feature on top of an old, possibly-already-merged feature branch.
- **Every branch is `feature/<kebab-case-description>`, no exceptions.** Not `fix/`,
  not `chore/`, not `ci/`, not `feat/` - `feature/` is the only branch prefix this
  repository uses, regardless of what kind of change it is. The commit message
  prefix (see below) is where the change type actually goes.
- Conventional commit prefixes on the commit message itself: `feat`, `fix`, `chore`, `docs`, `ci`, `test`, `refactor`. Imperative mood. Explain _why_ in the body when it's not obvious from the diff.
- Open a PR into `develop`, not `main`.
- Even a one-line CI fix goes through a branch + PR, no direct pushes to `develop`.

## Before opening a PR

- `./gradlew build` must be clean (compiles + runs the full test suite).
- google-java-format 1.36.1 style is enforced in CI (Super-Linter's
  `GOOGLE_JAVA_FORMAT` check), not by any Gradle task - `./gradlew build`
  passing does not mean formatting is clean. Run the formatter locally before
  pushing if you touched Java files (JDK 16+ needs the `--add-exports`/
  `--add-opens` flags below, since google-java-format reaches into javac
  internals):

  ```sh
  java \
    --add-exports jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED \
    --add-exports jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED \
    --add-exports jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED \
    --add-exports jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED \
    --add-exports jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED \
    --add-opens jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED \
    -jar google-java-format-1.36.1-all-deps.jar --replace <files>
  ```

- Don't add a dependency if the standard library, an already-installed package, or a native platform feature already covers it.
- Non-trivial logic (a branch, a loop, a parser, a money/security path, a prompt-schema change) gets a test. A bugfix without a regression test isn't done.

## Code style

- Smallest diff that fixes the root cause. If a bug lives in a shared function, fix it there once - not in every caller that happens to trip over it.
- No unrequested abstractions, no speculative config, no scaffolding "for later."
- AI prompts live under `src/main/resources/prompts/*.md`, never as a hardcoded Java string literal. Load once via `ClassPathResource`, fill `{{PLACEHOLDER}}` tokens per request.
- For a Gemini structured-output schema: a prose instruction in the prompt is a suggestion the model can ignore. `required` and `minItems` on the schema are enforced deterministically as part of generation - prefer the schema constraint over prompt wording whenever the two could disagree.

## CI (Super-Linter)

Runs on every PR into `main`/`develop`: `GITHUB_ACTIONS`, `GITLEAKS`, `GIT_MERGE_CONFLICT_MARKERS`, `GOOGLE_JAVA_FORMAT`, `JAVA` (Checkstyle, `google_checks.xml`), `MARKDOWN`, `MARKDOWN_PRETTIER`, `NATURAL_LANGUAGE` (textlint), `YAML`, `YAML_PRETTIER`, plus `SQLFLUFF` on Flyway migrations (`.github/linters/.sqlfluff`, postgres dialect).

- A file that shouldn't be graded as prose (an LLM prompt `.md`, generated
  output) gets excluded via **each tool's own ignore file** -
  `.prettierignore`, `.markdownlintignore` - not a
  `<LINTER>_FILTER_REGEX_EXCLUDE` env var in the workflow. That's a
  MegaLinter convention; super-linter/super-linter has no working per-linter
  regular-expression exclude, only a global `FILTER_REGEX_EXCLUDE` (which
  would also blind `GITLEAKS`, so avoid it for anything narrower than
  "exclude everywhere").
- `NATURAL_LANGUAGE` (textlint) does not reliably honor `.textlintignore` under super-linter (known upstream issue) - if it flags a prompt file, fix the flagged wording directly rather than fighting the ignore mechanism.
- `MARKDOWN` caps line length at 400 chars (`MD013`) - wrap a long bullet across
  indented continuation lines rather than one long line.
- `NATURAL_LANGUAGE`'s terminology rule rejects casual phrasing in any `.md`
  outside `resources/prompts/`: write `repository` not `repo`, `bugfix` not
  `bug fix`, `regular expression` not `regex`, in prose. Inline code/fences
  (like an actual `FILTER_REGEX_EXCLUDE` env var name) are exempt - only
  plain prose gets flagged.

## Deploying to Render

- `.github/workflows/deploy-render.yml` (`workflow_dispatch`, manual) is the supported path: builds + tests first, then calls Render's deploy hook with the resolved commit SHA as the hook's `ref` query param - not the linked branch's current HEAD, which could differ from what was actually just built if that branch moved mid-run or `ref` named something else.
- Needs the repository secret `RENDER_DEPLOY_HOOK_URL` (Render dashboard -> service -> Settings -> Deploy Hook).
- Backend config (`DB_PASSWORD`, `JDBC_URL`, `GEMINI_API_KEY`, `AI_MODEL`, JWT secrets, etc.) lives in **Render's Environment tab**, never in a GitHub Actions secret unless a workflow specifically needs to call the DB or a third-party API directly.
- Render's free tier spins the backend down after ~15 min idle; the next request cold-starts in 30-60s. This is expected given the hosting tier, not a bug to "fix" in application code.

## Gemini / AI filter proposals (`domain/transaction/query/ai/`)

- Gemini model IDs get deprecated over time. If `Describe your filter` starts
  404ing or a previously-working model name stops working, check Render's
  logs for `Gemini call failed: model=..., httpStatus=...` before assuming
  it's a code bug - update the code-level default (`application.properties` +
  `GeminiFilterClient`'s `@Value` fallback) and `docs/ai-filter-proposals.md`
  together with Render's `AI_MODEL` env var so all three stay in sync.
- The response schema, not the prompt text, is the actual contract enforcement point - see "Code style" above.
