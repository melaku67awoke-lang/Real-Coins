# RealCoin v30 — Cloud Build From Android Phone

This project includes a GitHub Actions workflow at `.github/workflows/android.yml`.
It builds and tests the Android project on a GitHub-hosted Ubuntu runner, so a PC is not required for the build itself.

## From an Android phone

1. Create a GitHub repository and upload this project.
2. Keep the repository private if the project contains private source or configuration.
3. Open the repository's **Actions** tab.
4. Select **RealCoin Android CI**.
5. Tap **Run workflow**.
6. Wait for the workflow to finish.
7. Open the completed workflow run.
8. Under **Artifacts**, download `RealCoin-v30-debug-apk`.
9. Extract the artifact ZIP and install the APK on the Android phone.

## What the workflow actually does

- Uses JDK 17, required by the Android Gradle Plugin used by this project.
- Sets up Gradle 9.3.1, matching the project's AGP 9.1.1 requirement.
- Installs required Android SDK packages available on the hosted runner.
- Generates the missing Gradle wrapper during the CI run.
- Runs `testDebugUnitTest`.
- Runs `assembleDebug`.
- Uploads the generated debug APK only when the build/test job succeeds.
- Uploads test reports even when tests fail, so failures can be inspected.

## UI protection

The workflow does not modify application UI source or resources. The v30 source changes remain limited to the previously fixed non-UI configuration and test code.
