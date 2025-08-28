[![add-on registry](https://img.shields.io/badge/DDEV-Add--on_Registry-blue)](https://addons.ddev.com)
[![tests](https://github.com/Vardot/ddev-dev-tools/actions/workflows/tests.yml/badge.svg?branch=main)](https://github.com/Vardot/ddev-dev-tools/actions/workflows/tests.yml?query=branch%3Amain)
[![last commit](https://img.shields.io/github/last-commit/Vardot/ddev-dev-tools)](https://github.com/Vardot/ddev-dev-tools/commits)
[![release](https://img.shields.io/github/v/release/Vardot/ddev-dev-tools)](https://github.com/Vardot/ddev-dev-tools/releases/latest)

# DDEV Development Tools

This addon includes commonly used DDEV commands for Drupal development with focus on code quality and linting.

## Features

### Code Quality Tools 🧹

- **PHP Tools**
  - `phpcs` - PHP CodeSniffer with Drupal standards
  - `phpstan` - PHP Static Analysis Tool
  - `twigcs` - Twig CodeSniffer

- **JavaScript & CSS**
  - `eslint` - JavaScript/ECMAScript linter
  - `stylelint` - CSS/SCSS linter

- **General**
  - `cspell` - Spell Checker for Code

### Combined Commands 🚀

- `dev-lint` - Run all linters on changed files
- `dev-lint-all` - Run all linters on all files

## Installation

```bash
ddev add-on get Vardot/ddev-dev-tools
ddev restart
```

## Usage

### Individual Linting Tools

Each linting tool can be run in three modes:

**1. Check Git Changes (Default)**
```bash
ddev dev-phpcs        # Check PHP files in git diff
ddev dev-eslint       # Check JS files in git diff
ddev dev-stylelint    # Check CSS files in git diff
ddev dev-twigcs       # Check Twig files in git diff
ddev dev-cspell       # Check text files in git diff
```

**2. Check Specific Files**
```bash
ddev dev-phpcs --files=path/to/file1.php,path/to/file2.module
ddev dev-eslint --files=path/to/file1.js,path/to/file2.js
ddev dev-stylelint --files=path/to/file1.css,path/to/file2.css
ddev dev-twigcs --files=path/to/file1.twig,path/to/file2.twig
ddev dev-cspell --files=path/to/file1.txt,path/to/file2.md
```

**3. Check All Files**
```bash
ddev dev-phpcs --all       # Check all PHP files
ddev dev-eslint --all      # Check all JS files
ddev dev-stylelint --all   # Check all CSS files
ddev dev-twigcs --all      # Check all Twig files
ddev dev-cspell --all      # Check all text files
```

### Combined Linting Commands

**Check Changed Files**
```bash
ddev dev-lint    # Run all linters on git changes
```

**Check All Files**
```bash
ddev dev-lint-all    # Run all linters on all files
```

### Special Options

**PHPCS Extensions**
```bash
# Check specific file extensions
ddev dev-phpcs --ext=php,module,inc
```

### CI/CD Integration 🔄

This addon supports various CI/CD platforms including BitBucket Pipelines, GitHub Actions, Azure DevOps, and GitLab CI.

#### Option 1: Environment Variables

Set these variables in your CI environment:
```bash
IS_CI=TRUE
PHP_CHANGED_FILES="file1.php,file2.module"
JS_CHANGED_FILES="file1.js,file2.js"
STYLE_CHANGED_FILES="file1.css,file2.css"
TWIG_CHANGED_FILES="file1.twig,file2.twig"
TEXT_CHANGED_FILES="file1.md,file2.txt"
```

#### Option 2: Configuration File

1. Create a pipeline configuration file:
```bash
cp config.pipeline.yaml.example .ddev/config.pipeline.yaml
```

2. Or generate it dynamically in your pipeline:

**BitBucket Pipelines**
```yaml
steps:
  - step:
      script:
        - |
          cat > .ddev/config.pipeline.yaml << EOF
          web_environment:
            - IS_CI=TRUE
            - PHP_CHANGED_FILES=${PHP_CHANGED_FILES}
            - JS_CHANGED_FILES=${JS_CHANGED_FILES}
            - STYLE_CHANGED_FILES=${STYLE_CHANGED_FILES}
            - TWIG_CHANGED_FILES=${TWIG_CHANGED_FILES}
            - TEXT_CHANGED_FILES=${TEXT_CHANGED_FILES}
          EOF
```

**GitHub Actions**
```yaml
jobs:
  lint:
    steps:
      - run: |
          cat > .ddev/config.pipeline.yaml << EOF
          web_environment:
            - IS_CI=TRUE
            - PHP_CHANGED_FILES=${{ env.PHP_CHANGED_FILES }}
            - JS_CHANGED_FILES=${{ env.JS_CHANGED_FILES }}
            - STYLE_CHANGED_FILES=${{ env.STYLE_CHANGED_FILES }}
            - TWIG_CHANGED_FILES=${{ env.TWIG_CHANGED_FILES }}
            - TEXT_CHANGED_FILES=${{ env.TEXT_CHANGED_FILES }}
          EOF
```

**Azure DevOps**
```yaml
steps:
  - bash: |
      cat > .ddev/config.pipeline.yaml << EOF
      web_environment:
        - IS_CI=TRUE
        - PHP_CHANGED_FILES=$(PHP_CHANGED_FILES)
        - JS_CHANGED_FILES=$(JS_CHANGED_FILES)
        - STYLE_CHANGED_FILES=$(STYLE_CHANGED_FILES)
        - TWIG_CHANGED_FILES=$(TWIG_CHANGED_FILES)
        - TEXT_CHANGED_FILES=$(TEXT_CHANGED_FILES)
      EOF
```

3. Then run the linting command:
```bash
ddev dev-lint
```

### Testing Tools 🧪
- **Cypress**
  - `cypress-open` - Open interactive Cypress window
  - `cypress-run` - Run Cypress tests in headless mode
  - `cypress-install` - Install additional npm packages for Cucumber support

It's recommended to run ddev cypress-open first to create configuration and support files. This addon sets CYPRESS_baseUrl to DDEV's primary URL in the docker-compose.cypress.yaml.

**Note:** This addon uses the latest official Cypress Docker image to ensure up-to-date testing capabilities and compatibility.

#### Cypress with Cucumber Support

For Behavior-Driven Development (BDD) with Gherkin syntax:

```bash
# Install Cucumber preprocessor packages
ddev cypress-install

# Copy example configuration
cp examples/cypress.config.js.example-[cucumber] cypress.config.js
```

This enables writing tests in natural language:
```gherkin
Feature: User Login
  Scenario: Successful login
    Given I visit the login page
    When I enter valid credentials
    Then I should be logged in
```

#### Configuration Examples

The addon includes example configurations:
- `cypress.config.js.example-[cucumber]` - Configuration with Cucumber/Gherkin support

### Cypress Usage

To use Cypress, you'll need to configure your display settings based on your OS:

**macOS:**
```bash
brew install xquartz --cask
open -a XQuartz
# Check "Allow connections from network clients" in XQuartz preferences
# Restart your Mac
xhost + 127.0.0.1

# Add to .ddev/docker-compose.cypress_extra.yaml:
services:
  cypress:
    environment:
      - DISPLAY=host.docker.internal:0
```

**Linux:**
```bash
export DISPLAY=:0
xhost +
```

**Windows:**
Install [GWSL](https://www.microsoft.com/en-us/p/gwsl/9nl6kd1h33v3) or [VcXsrv](https://sourceforge.net/projects/vcxsrv/)
