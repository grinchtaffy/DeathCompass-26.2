# No-install GitHub Actions build guide

This package is **source code, not a compiled JAR**. Its Java has **not been compile-tested**; this workflow runs the build and gives you the result or an error log. It does not repair code errors.

1. Create/sign in to your account at https://github.com/ .
2. Go to https://github.com/new and create a repository named `DeathCompass-26.2`, with **Public** selected if you want GitHub-hosted Actions without private-repo usage considerations. Create an empty repository **without** a README or .gitignore checkbox.
3. Extract `DeathCompass-26.2-GitHub-Actions-ready.zip` on your PC. The extracted contents should include `.github`, `src`, `build.gradle`, `settings.gradle`, `gradle.properties` and this guide. On Windows, enable **View > Show > Hidden items** if `.github` is hidden.
4. In your new empty GitHub repository, click **uploading an existing file** (or **Add file > Upload files**). Drag the extracted contents (including the **.github folder** and `src` folder) into the upload zone. Commit to the `main` branch. **Do not upload just the ZIP**: GitHub Actions needs the unzipped files and folder structure. If the GitHub browser refuses folders, use the github.dev editor (press `.` inside the repository), drag folders into its file explorer, commit and push.
5. Open your repository > **Actions**. If asked, enable workflows. Click **Build Death Compass JAR**, then **Run workflow** (branch `main`) > green **Run workflow**. The build also runs automatically after upload.
6. Click the newest run. Green check means compiled successfully. On the run page, scroll to **Artifacts** and download **DeathCompass-26.2-JAR**. Unzip the downloaded artifact ZIP: the `.jar` inside is your mod.
7. If it is red, open the **build** job to read errors or download **build-error-log** artifact. The underlying Java may need fixes before it can compile.
8. Install Minecraft Java 26.2 and the matching Fabric Loader/Fabric API. Place the compiled mod `.jar` (not the artifact ZIP, not a sources JAR) and matching Fabric API `.jar` in `%appdata%\.minecraft\mods` on Windows, then launch Fabric.

Notes: accurate all-structure detection is **not** implemented. The structure finder detects possible clue blocks inside loaded chunks, and block detection cannot inspect unloaded or obfuscated server chunks. The lava warning only detects lava immediately underneath the crosshair-targeted block.
