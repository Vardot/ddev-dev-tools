[![add-on registry](https://img.shields.io/badge/DDEV-Add--on_Registry-blue)](https://addons.ddev.com)
[![tests](https://github.com/Vardot/ddev-dev-tools/actions/workflows/tests.yml/badge.svg?branch=main)](https://github.com/Vardot/ddev-dev-tools/actions/workflows/tests.yml?query=branch%3Amain)
[![last commit](https://img.shields.io/github/last-commit/Vardot/ddev-dev-tools)](https://github.com/Vardot/ddev-dev-tools/commits)
[![release](https://img.shields.io/github/v/release/Vardot/ddev-dev-tools)](https://github.com/Vardot/ddev-dev-tools/releases/latest)

# DDEV Dev Tools

## Overview

This add-on integrates Dev Tools into your [DDEV](https://ddev.com/) project.

## Installation

```bash
ddev add-on get Vardot/ddev-dev-tools
ddev restart
```

After installation, make sure to commit the `.ddev` directory to version control.

## Usage

| Command | Description |
| ------- | ----------- |
| `ddev describe` | View service status and used ports for Dev Tools |
| `ddev logs -s dev-tools` | Check Dev Tools logs |

## Advanced Customization

To change the Docker image:

```bash
ddev dotenv set .ddev/.env.dev-tools --dev-tools-docker-image="busybox:stable"
ddev add-on get Vardot/ddev-dev-tools
ddev restart
```

Make sure to commit the `.ddev/.env.dev-tools` file to version control.

All customization options (use with caution):

| Variable | Flag | Default |
| -------- | ---- | ------- |
| `DEV_TOOLS_DOCKER_IMAGE` | `--dev-tools-docker-image` | `busybox:stable` |

## Credits

**Contributed and maintained by [@Vardot](https://github.com/Vardot)**
