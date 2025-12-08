#!/bin/bash

## #ddev-generated
# Description: Utility functions for file handling in DDEV commands

# Function to check if a command is executable
# Usage: check_command_executable "command_path" ["command_name"]
# Returns: 0 if executable, exits with 1 if not executable or not found
check_command_executable() {
    local cmd_path="$1"
    local cmd_name="${2:-$1}"

    # Check if command exists
    if [ ! -e "$cmd_path" ]; then
        >&2 echo "❌ Error: Command not found: $cmd_name"
        >&2 echo "   Path: $cmd_path"
        >&2 echo "   Please ensure the required dependencies are installed."
        exit 1
    fi

    # Check if command is executable
    if [ ! -x "$cmd_path" ]; then
        >&2 echo "❌ Error: Permission denied: $cmd_name is not executable"
        >&2 echo "   Path: $cmd_path"
        >&2 echo "   Fix: Run 'chmod +x $cmd_path' to make it executable"
        exit 1
    fi

    return 0
}

# Function to detect document root (docroot or web)
get_docroot() {
    if [ -d "web/core" ]; then
        echo "web"
    elif [ -d "docroot/core" ]; then
        echo "docroot"
    else
        >&2 echo "❌ Error: Could not detect document root (neither web/ nor docroot/ found with core and modules)"
        exit 1
    fi
}

# Function to get changed files based on extensions
get_changed_files() {
    local extensions=("$@")
    local ext_pattern=$(IFS='|'; echo "${extensions[*]}")
    local docroot=$(get_docroot)
    local custom_path_pattern="${docroot}/(modules|themes)/custom/.*"
    local changed_files=""

    # If we're in Bitbucket Pipelines
    if [ "${IS_CI:-false}" = "TRUE" ]; then
        changed_files=$(git diff --name-only "origin/$BITBUCKET_PR_DESTINATION_BRANCH...$BITBUCKET_BRANCH" 2>/dev/null |
            grep -E "^${custom_path_pattern}\.($ext_pattern)$" |
            grep -v "node_modules" |
            tr '\n' ' ')
    else
        # In local environment, check both staged and unstaged changes
        changed_files=$(
            {
                git diff --name-only 2>/dev/null
                git diff --name-only --staged 2>/dev/null
            } | grep -E "^${custom_path_pattern}\.($ext_pattern)$" |
            grep -v "node_modules" |
            sort -u | tr '\n' ' '
        )
    fi

    echo "$changed_files"
}

# Function to get all files based on extensions
get_all_files() {
    local extensions=("$@")
    local ext_pattern=$(IFS='|'; echo "${extensions[*]}")
    local docroot=$(get_docroot)

    find ${docroot}/modules/custom ${docroot}/themes/custom -type f \
        -regextype posix-extended \
        -regex ".*\.($ext_pattern)$" \
        -not -path "*/node_modules/*" | tr '\n' ' '
}

# Main function to get files based on arguments
get_files() {
    # First arguments are extensions
    local extensions=()
    local i=0
    while [ "$#" -gt 0 ] && [ "${1:0:2}" != "--" ]; do
        extensions[$i]="$1"
        i=$((i + 1))
        shift
    done

    # Build extension pattern once for all cases
    local ext_pattern=$(IFS='|'; echo "${extensions[*]}")

    local files=""

    # Parse remaining arguments
    if [ "$#" -eq 0 ]; then
        # No flags - get changed files
        >&2 echo "🔄 Checking git diff files..."
        files=$(get_changed_files "${extensions[@]}")
    else
        case "$1" in
            --all)
                >&2 echo "🔍 Checking all files with extensions: ${extensions[*]}"
                files=$(get_all_files "${extensions[@]}")
                ;;
            --files=*)
                local specified_files=${1#--files=}
                >&2 echo "🔍 Checking specified files..."
                # Filter specified files by extension
                files=$(echo "$specified_files" | tr ',' '\n' | \
                    while IFS= read -r file; do
                        if [ -f "$file" ] && echo "$file" | grep -q -E "\.($ext_pattern)$"; then
                            echo "$file"
                        fi
                    done | tr '\n' ' ')
                ;;
            *)
                >&2 echo "❌ Unknown option: $1"
                >&2 echo "Usage: get_files [extensions...] [--all | --files=file1,file2]"
                return 1
                ;;
        esac
    fi

    if [ -z "${files// /}" ]; then
        >&2 echo "🚫 No matching files found"
        return 0
    fi

    echo "$files"
}