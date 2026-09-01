# Expense Notebook

A native Android expense tracker built with Kotlin, Jetpack Compose, a ViewModel, and Android SQLite. It is fully offline and requests no permissions.

## Project structure

```text
ExpenseNotebook/
├── .github/workflows/build-apk.yml
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/shivam/expensenotebook/
│       │   ├── MainActivity.kt
│       │   ├── ExpenseViewModel.kt
│       │   ├── data/ExpenseData.kt
│       │   ├── ui/ExpenseNotebookApp.kt
│       │   └── ui/theme/ExpenseNotebookTheme.kt
│       └── res/
│           ├── drawable/ic_launcher_foreground.xml
│           ├── mipmap-anydpi-v26/ic_launcher.xml
│           ├── mipmap-anydpi-v26/ic_launcher_round.xml
│           ├── values/colors.xml
│           ├── values/strings.xml
│           ├── values/themes.xml
│           └── values-night/themes.xml
├── gradle/wrapper/gradle-wrapper.properties
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradlew
├── gradlew.bat
└── .gitignore
```
2. Open the repository's **Actions** tab.
3. Select **Build Android APK**.
4. Select **Run workflow** and wait for the build to finish.
5. Open the completed workflow run and download **ExpenseNotebook-debug-apk** from the **Artifacts** section.

The workflow also builds automatically whenever code is pushed to the `main` branch.
