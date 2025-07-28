#!/bin/bash

# Function to get theme name from arguments or use default
get_theme_name() {
    local args=("$@")
    
    for ((i=0; i<${#args[@]}; i++)); do
        if [[ "${args[i]}" == "--theme" && -n "${args[i+1]}" ]]; then
            theme_name="${args[i+1]}"
            break
        fi
    done
    
    # Check if theme directory exists
    local theme_path="/var/www/html/docroot/themes/custom/${theme_name}"
    if [ ! -d "$theme_path" ]; then
        echo "Error: Theme directory does not exist: $theme_path" >&2
        exit 1
    fi
    
    echo "$theme_name"
}

# Function to get changed files based on file pattern
get_changed_files() {
    local pattern=$1
    local custom_path_pattern="docroot/(modules|themes)/custom/.*"
    local ext_pattern="(${pattern})"
    local changed_files=""
    
    # If we're in Bitbucket Pipelines
    if [ -n "$BITBUCKET_PR_DESTINATION_BRANCH" ] && [ -n "$BITBUCKET_BRANCH" ]; then
        changed_files=$(git diff --name-only "origin/$BITBUCKET_PR_DESTINATION_BRANCH...$BITBUCKET_BRANCH" 2>/dev/null | 
            grep -E "^${custom_path_pattern}\.${ext_pattern}$" |
            grep -v "node_modules" |
            tr '\n' ' ')
    else
        # In local environment, just check for modified files (staged and unstaged)
        changed_files=$(
            {
                git diff --name-only 2>/dev/null
                git diff --name-only --staged 2>/dev/null
            } | grep -E "^${custom_path_pattern}\.${ext_pattern}$" |
            grep -v "node_modules" |
            sort -u | tr '\n' ' '
        )
    fi
    
    echo "$changed_files"
}

# Function to get all files based on file pattern
get_all_files() {
    local pattern=$1
    # Use -regex instead of -name to support multiple extensions and exclude node_modules
    find docroot/modules/custom docroot/themes/custom -type f \
        -regextype posix-extended \
        -regex ".*\.(${pattern})$" \
        -not -path "*/node_modules/*" | tr '\n' ' '
}

# Function to parse arguments and return files to check
get_files_to_check() {
    local file_pattern=$1
    local files=""
    
    # Parse arguments
    while [[ $# -gt 1 ]]; do
        case "$2" in
            --files|-f)
                shift
                files="$2"
                ;;
            --all|-a)
                files=$(get_all_files "$file_pattern")
                ;;
            *)
                ;;
        esac
        shift
    done
    
    # If no files specified, check changed files
    if [ -z "$files" ]; then
        files=$(get_changed_files "$file_pattern")
    fi
    
    echo "$files"
} 