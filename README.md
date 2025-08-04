[![add-on registry](https://img.shields.io/badge/DDEV-Add--on_Registry-blue)](https://addons.ddev.com)
[![tests](https://github.com/Vardot/ddev-dev-tools/actions/workflows/tests.yml/badge.svg?branch=main)](https://github.com/Vardot/ddev-dev-tools/actions/workflows/tests.yml?query=branch%3Amain)
[![last commit](https://img.shields.io/github/last-commit/Vardot/ddev-dev-tools)](https://github.com/Vardot/ddev-dev-tools/commits)
[![release](https://img.shields.io/github/v/release/Vardot/ddev-dev-tools)](https://github.com/Vardot/ddev-dev-tools/releases/latest)

This addon includes commonly used DDEV commands for Drupal development:

- 🧹 Linting: `eslint`, `stylelint`, `phpcs`, `phpstan`, `cspell`, `twigcs`

## Installation

```bash
ddev add-on get Vardot/ddev-dev-tools
ddev restart
```

## Usage

```bash
ddev dev-lint
ddev dev-phpcs <file-name> <file-name>
ddev dev-stylelint <file-name> <file-name>
```
