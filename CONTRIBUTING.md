# Contributing to Nku

Thank you for your interest in contributing to Nku! This project aims to bring offline medical AI to underserved communities in Pan-Africa.

## 🎯 Priority Areas

We especially welcome contributions in these areas:

### 1. Language Improvements
- **Low-resource African languages**: Improving translation quality for languages like Twi, Ewe, Ga, Wolof, Lingala
- **Medical terminology**: Building specialized medical glossaries for African dialects
- **TTS voices**: Expanding Android System TTS language support and voice quality

### 2. Performance Optimization
- **ARM GPU acceleration**: Vulkan/OpenCL kernels for Mali/Adreno GPUs
- **Memory optimization**: Further reducing peak RAM usage below 1.5GB
- **Inference speed**: Optimizing token generation speed on budget CPUs

### 3. Clinical Validation
- **Field testing**: Partnering with CHWs for real-world validation
- **Calibration data**: Expanding the African clinical triage calibration dataset (used for imatrix quantization experiments)
- **Safety guardrails**: Improving uncertainty quantification and abstention logic

### 4. UI/UX Improvements
- **Low-literacy design**: Icons, visual aids, and audio-first interactions
- **Accessibility**: Screen reader support, high contrast modes
- **Offline-first UX**: Better feedback for users without connectivity

## 🛠️ Development Setup

### Prerequisites
- Android Studio Ladybug (2024.2.1+)
- Android SDK 35
- NDK 29.0.13113456
- Kotlin 2.1.0
- ~8GB free disk space

### Build Steps

```bash
# Clone with submodules
git clone --recursive https://github.com/Elormyevu/nku-medgemma-conversion.git
cd nku-medgemma-conversion

# Open in Android Studio
open mobile/android -a "Android Studio"

# Or build from command line
cd mobile/android
./gradlew assembleDebug
```

### Running Tests

```bash
# Unit tests
./gradlew test

# Instrumented tests (requires emulator/device)
./gradlew connectedAndroidTest
```

## 📝 Coding Standards

### Kotlin
- Follow [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use explicit types for public APIs
- Document all public functions with KDoc

### Python
- Follow PEP 8
- Use type hints
- Document with docstrings

### Commits
- Use [Conventional Commits](https://www.conventionalcommits.org/)
- Prefix: `feat:`, `fix:`, `docs:`, `refactor:`, `test:`, `chore:`
- Example: `feat(inference): add KleidiAI acceleration for Cortex-A76`

## 🔄 Pull Request Process

1. **Fork** the repository
2. **Create a branch** from `main`: `git checkout -b feat/my-feature`
3. **Make changes** following coding standards
4. **Test** your changes locally
5. **Commit** with a descriptive message
6. **Push** to your fork
7. **Open a PR** against `main`

### PR Checklist
- [ ] Code follows project style guidelines
- [ ] Self-reviewed the code
- [ ] Added/updated documentation
- [ ] Added/updated tests (if applicable)
- [ ] All tests pass locally
- [ ] No new warnings

## 🐛 Bug Reports

Use the [GitHub Issues](https://github.com/Elormyevu/nku-medgemma-conversion/issues) template:

1. **Describe the bug**: Clear, concise description
2. **To reproduce**: Step-by-step instructions
3. **Expected behavior**: What should happen
4. **Device info**: Phone model, Android version, RAM
5. **Logs**: Relevant logcat output

## 💡 Feature Requests

Open an issue with the `enhancement` label:

1. **Problem statement**: What problem does this solve?
2. **Proposed solution**: How would you implement it?
3. **Alternatives**: What other approaches did you consider?
4. **Impact**: Who benefits from this feature?

## 📧 Contact

- **GitHub Issues**: For bugs and features
- **Discussions**: For questions and ideas
- **Email**: [project maintainer email]

## 📄 License

By contributing, you agree that your contributions will be licensed under the Apache License 2.0.

---

Thank you for helping bring medical AI to those who need it most! 🌍
