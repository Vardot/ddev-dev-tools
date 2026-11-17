#!/usr/bin/env bats

# Bats is a testing framework for Bash
# Documentation https://bats-core.readthedocs.io/en/stable/
# Bats libraries documentation https://github.com/ztombol/bats-docs

# For local tests, install bats-core, bats-assert, bats-file, bats-support
# And run this in the add-on root directory:
#   bats ./tests/test.bats
# To exclude release tests:
#   bats ./tests/test.bats --filter-tags '!release'
# For debugging:
#   bats ./tests/test.bats --show-output-of-passing-tests --verbose-run --print-output-on-failure

setup() {
  set -eu -o pipefail

  # Override this variable for your add-on:
  export GITHUB_REPO=Vardot/ddev-dev-tools

  TEST_BREW_PREFIX="$(brew --prefix 2>/dev/null || true)"
  export BATS_LIB_PATH="${BATS_LIB_PATH}:${TEST_BREW_PREFIX}/lib:/usr/lib/bats"
  bats_load_library bats-assert
  bats_load_library bats-file
  bats_load_library bats-support

  export DIR="$(cd "$(dirname "${BATS_TEST_FILENAME}")/.." >/dev/null 2>&1 && pwd)"
  export PROJNAME="test-$(basename "${GITHUB_REPO}")"
  mkdir -p ~/tmp
  export TESTDIR=$(mktemp -d ~/tmp/${PROJNAME}.XXXXXX)
  export DDEV_NONINTERACTIVE=true
  export DDEV_NO_INSTRUMENTATION=true
  ddev delete -Oy "${PROJNAME}" >/dev/null 2>&1 || true
  cd "${TESTDIR}"
  run ddev config --project-name="${PROJNAME}" --project-tld=ddev.site
  assert_success
  run ddev start -y
  assert_success
}

health_checks() {
  # Do something useful here that verifies the add-on

  # You can check for specific information in headers:
  # run curl -sfI https://${PROJNAME}.ddev.site
  # assert_output --partial "HTTP/2 200"
  # assert_output --partial "test_header"

  # Or check if some command gives expected output:
  DDEV_DEBUG=true run ddev launch
  assert_success
  assert_output --partial "FULLURL https://${PROJNAME}.ddev.site"
}

teardown() {
  set -eu -o pipefail
  ddev delete -Oy ${PROJNAME} >/dev/null 2>&1
  [ "${TESTDIR}" != "" ] && rm -rf ${TESTDIR}
}

@test "install from directory" {
  set -eu -o pipefail
  echo "# ddev add-on get ${DIR} with project ${PROJNAME} in $(pwd)" >&3
  run ddev add-on get "${DIR}"
  assert_success
  run ddev restart -y
  assert_success
  health_checks
}

@test "linters skip generated js paths" {
  set -eu -o pipefail
  source "${DIR}/commands/web/dev-utils.sh"

  run bash -c 'source "'"${DIR}"'/commands/web/dev-utils.sh"; printf "%s\n" \
    "docroot/modules/custom/mod/js/dist/bundle.js" \
    "docroot/modules/custom/mod/js/admin/build/dist/vendor.js" \
    "docroot/modules/custom/mod/js/src/main.js" \
    "js/dist/bare.js" \
    "docroot/modules/custom/mod/js/app.js" \
    "docroot/themes/custom/thm/js/global.js" \
    "docroot/modules/custom/mod/dist/outside-js.js" | filter_generated_paths'
  assert_success
  refute_output --partial "js/dist/bundle.js"
  refute_output --partial "build/dist/vendor.js"
  refute_output --partial "js/src/main.js"
  refute_output --partial "js/dist/bare.js"
  assert_output --partial "docroot/modules/custom/mod/js/app.js"
  assert_output --partial "docroot/themes/custom/thm/js/global.js"
  assert_output --partial "docroot/modules/custom/mod/dist/outside-js.js"

  # Filtering everything out is "nothing to lint", not a failure: the callers all
  # run under `set -e`, so a bare grep here would abort them.
  run bash -c 'set -e; source "'"${DIR}"'/commands/web/dev-utils.sh"; \
    echo "docroot/modules/custom/mod/js/dist/only.js" | filter_generated_paths'
  assert_success
  assert_output ""
}

# bats test_tags=release
@test "install from release" {
  set -eu -o pipefail
  echo "# ddev add-on get ${GITHUB_REPO} with project ${PROJNAME} in $(pwd)" >&3
  run ddev add-on get "${GITHUB_REPO}"
  assert_success
  run ddev restart -y
  assert_success
  health_checks
}
