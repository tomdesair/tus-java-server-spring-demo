# Agent Instructions

## GitHub CLI (`gh`) Usage
When running `gh` commands in this project via an automated agent environment, ensure you bypass the default `GITHUB_TOKEN` environment variable. The agent environment may have an invalid `GITHUB_TOKEN` set, which `gh` prioritizes over valid keyring credentials, resulting in an `HTTP 401: Bad credentials` error.

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
