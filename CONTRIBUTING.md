# Contributing to WToDo

First off, thank you for considering contributing to WToDo! It's people like you who make the open-source community such an amazing place to learn, inspire, and create.

## 🚀 How to Contribute

### 1. Reporting Bugs
- Use the **Bug Report** template when opening an issue.
- Provide a clear description and steps to reproduce.
- Include device information and Android version.

### 2. Suggesting Features
- Use the **Feature Request** template.
- Explain why the feature is needed and how it benefits users.

### 3. Pull Requests
- **Branch Naming**:
    - `feature/description` for new features.
    - `fix/description` for bug fixes.
    - `docs/description` for documentation.
- **Workflow**:
    1. Fork the repository.
    2. Create your branch from `main`.
    3. Ensure code follows existing style (Kotlin coding conventions).
    4. Run all tests and lint checks: `./gradlew lint test`.
    5. Submit a PR with a detailed description of changes.

## 🎨 Coding Style
- Follow [Official Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html).
- Use meaningful variable and function names.
- Document public APIs with KDoc.
- Keep functions small and focused.

## 🧪 Testing Requirements
- Unit tests are required for any new business logic in the `domain` or `data` layers.
- UI tests (Compose tests) are encouraged for new screens or complex components.

## 📝 Commit Conventions
We follow a simplified version of [Conventional Commits](https://www.conventionalcommits.org/):
- `feat:` for new features.
- `fix:` for bug fixes.
- `docs:` for documentation changes.
- `chore:` for build/dependency updates.

---

By contributing, you agree that your contributions will be licensed under the project's [MIT License](LICENSE).
