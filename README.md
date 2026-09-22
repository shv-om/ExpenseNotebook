# Expense Notebook

A native Android expense tracker built with Kotlin, Jetpack Compose, a ViewModel, and Android SQLite. It is fully offline and requests no permissions. Transaction expenses can be imported from text-based PDF and `.xlsx` statements after an on-device review.

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
│       │   ├── data/TransactionImportParser.kt
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

## Open and build

1. Open Android Studio.
2. Select **Open** and choose the `ExpenseNotebook` folder.
3. Allow Gradle sync to finish and install Android SDK 35 if prompted.
4. Select **Build > Build Bundle(s) / APK(s) > Build APK(s)**.

Terminal command:

```bash
./gradlew assembleDebug
```

Windows PowerShell or Command Prompt:

```bat
gradlew.bat assembleDebug
```

Generated APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Build on GitHub without Android Studio

1. Create a GitHub repository and upload the contents of this `ExpenseNotebook` folder.
2. Open the repository's **Actions** tab.
3. Select **Build Android APK**.
4. Select **Run workflow** and wait for the build to finish.
5. Open the completed workflow run and download **ExpenseNotebook-debug-apk** from the **Artifacts** section.

The workflow also builds automatically whenever code is pushed to the `main` branch.

## Statement import

Open **Overview > Import PDF / Excel statement**, enter your name and/or account last four digits, then choose a text-based PDF or `.xlsx` file. Exclusion checks only the statement's Receiver Address/Receiver Name column: values such as `****1234@upi` match the configured last four digits, and values such as `Name/Name` match the configured name. The app then suggests categories and shows a review list before writing anything to SQLite. Duplicate imports are skipped. Scanned PDFs and legacy `.xls` files are not supported.
