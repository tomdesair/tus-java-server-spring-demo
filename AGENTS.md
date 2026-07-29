# Agent Instructions

## Project Context
When working on this project, always read the [`README.md`](README.md) file to obtain full context on project architecture, setup instructions, and features.

## GitHub CLI (`gh`) & Git Usage
- Always run `git` commands (e.g., `git status`, `git diff`, `git add`, `git commit`, `git push`) and `gh` commands unsandboxed (setting `BypassSandbox: true` when calling `run_command`) to ensure git hooks, local tools, and remote repository authentication work without sandbox errors.
- When running `gh` commands in this project via an automated agent environment, ensure you bypass the default `GITHUB_TOKEN` environment variable. The agent environment may have an invalid `GITHUB_TOKEN` set, which `gh` prioritizes over valid keyring credentials, resulting in an `HTTP 401: Bad credentials` error.

**Workaround:** Prefix `gh` commands with `env -u GITHUB_TOKEN` to force the CLI to use the valid keyring authentication.

Example:
```bash
env -u GITHUB_TOKEN gh pr create --title "..." --body "..."
```

## Developer Guidelines & Code Architecture

### 1. Spring Boot & Java Requirements
- **Java Version**: The project is configured for **Java 17** (or newer) to align with Spring Boot 3.x requirements.
- **Jakarta EE / Servlets**: Always use `jakarta.servlet.*` package imports instead of the legacy `javax.servlet.*` packages.

### 2. Frontend Configuration & Webpack 5
- **Node & NPM Version**: Managed by `frontend-maven-plugin` (version `1.15.0`). The project targets Node `v22.11.0` and NPM `10.9.0` to ensure build stability.
- **Webpack Bundle**: The bundle compiles using Webpack 5 syntax and extracts styles using `mini-css-extract-plugin` rather than `extract-text-webpack-plugin`.
- **Uppy File Uploader**:
  - Upgraded to Uppy v5.
  - Do **not** call `uppy.run()`. This method was deprecated/removed. Simply instantiating Uppy with `new Uppy()` and using plugins compiles and registers events automatically.
  - CSS styles must be imported from `@uppy/core/css/style.min.css` and `@uppy/dashboard/css/style.min.css` rather than the old `dist` structure or the legacy unified `uppy` css bundle.

### 3. String Comparisons & Avoiding Deprecated StringUtils
- Do not use deprecated `StringUtils` comparison methods such as `StringUtils.equals(...)` or `StringUtils.equalsIgnoreCase(...)`.
- Always use `org.apache.commons.lang3.Strings.CS` for case-sensitive operations (e.g., `Strings.CS.equals(...)`, `Strings.CS.startsWith(...)`) and `org.apache.commons.lang3.Strings.CI` for case-insensitive operations (e.g., `Strings.CI.equals(...)`, `Strings.CI.startsWith(...)`).

### 4. Maven Build Execution & Unsandboxed Mode
- Always run Maven build commands unsandboxed (e.g., setting `BypassSandbox: true` when calling `run_command`) to allow access to local Maven repository (`~/.m2`) and dependency resolution.

