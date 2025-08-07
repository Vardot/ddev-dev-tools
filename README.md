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

### CI/CD Environment

In CI environments, set the following variables:
- `IS_CI=TRUE`
- `PHP_CHANGED_FILES`: List of PHP files to check
- `JS_CHANGED_FILES`: List of JavaScript files to check
- `STYLE_CHANGED_FILES`: List of CSS files to check
- `TWIG_CHANGED_FILES`: List of Twig files to check
- `TEXT_CHANGED_FILES`: List of text files to check

Example:
```bash
IS_CI=TRUE PHP_CHANGED_FILES="file1.php,file2.module" ddev dev-lint
```