# Drupal Java Crawler

A multi-threaded web crawler specifically designed for analyzing Drupal websites and extracting form field information.

## Features

- **Two Crawling Modes:**
  - **Headless Mode (Jsoup)** - Fast, API-based crawling
  - **Headed Mode (Selenium)** - Browser-based crawling with JavaScript support

- **Multi-threaded** concurrent crawling for better performance
- **Automatic form detection** and field analysis
- **Cookie-based authentication** support
- **CSV export** of crawl results
- **Interactive configuration** at runtime

## Installation via DDEV Addon

This crawler is included with the `ddev-dev-tools` addon. When you install the addon on your Drupal project:

```bash
ddev add-on get Vardot/ddev-dev-tools
ddev restart
```

The crawler files are automatically copied to `.ddev/drupal_java_crawler/` and Java/Maven dependencies are installed.

## Usage

### Quick Start

From your Drupal project with the addon installed:

```bash
ddev dev-crawler
```

The command will prompt you interactively for:
1. **Base Domain URL** - e.g., `https://example.com/` or `http://localhost/`
2. **Start Path** (optional) - e.g., `node/1` or leave empty for root
3. **Cookie String** (optional) - for authenticated crawling
4. **Crawl Mode** - Choose between Headless (Jsoup) or Headed (Selenium)
5. **Max Pages** - Maximum number of pages to crawl
6. **Number of Threads** - Concurrent threads for faster crawling

### Example Configuration

```
Base Domain:  https://mysite.ddev.site/
Start Path:   (empty for root)
Cookie:       SSESS123abc...
Mode:         Jsoup (Headless)
Max Pages:    100
Threads:      4
```

### Output

Results are saved to CSV files in the current directory:
- `crawl_results_thread-1.csv`
- `crawl_results_thread-2.csv`
- etc.

Each CSV contains:
- Page URL
- Form action
- Field name
- Field type
- Field attributes
- And more...

## Advanced Usage

### Authentication

For crawling authenticated areas, provide a cookie string when prompted. You can obtain this from your browser's developer tools:

1. Log into your Drupal site
2. Open Developer Tools (F12)
3. Go to Application/Storage → Cookies
4. Copy the session cookie value (e.g., `SSESS...`)

### Choosing the Right Mode

**Use Headless Mode (Jsoup) when:**
- Crawling static content
- Maximum speed is needed
- JavaScript rendering isn't required
- Lower resource usage is preferred

**Use Headed Mode (Selenium) when:**
- Site heavily uses JavaScript
- Forms are loaded dynamically
- You need to interact with AJAX content
- Visual debugging is helpful

## Manual Maven Usage

If you need to run Maven commands directly:

```bash
# Navigate to crawler directory
ddev ssh
cd /var/www/html/.ddev/drupal_java_crawler

# Compile
mvn clean compile

# Run
mvn exec:java

# Package as JAR
mvn clean package
```

## Troubleshooting

### Java/Maven not found
Run the addon installation again:
```bash
ddev add-on remove dev-tools
ddev add-on get Vardot/ddev-dev-tools
ddev restart
```

### Crawler files missing
Ensure the addon is properly installed. Check for files in:
```bash
ls -la .ddev/drupal_java_crawler/
```

### Out of memory errors
Reduce the number of threads or max pages in the configuration.

## Technical Details

- **Language:** Java 17
- **Build Tool:** Maven
- **Main Dependencies:**
  - Jsoup (HTML parsing)
  - Selenium WebDriver (browser automation)
  - OpenCSV (CSV export)
  - WebDriverManager (automatic driver management)

## License

Part of the DDEV Dev Tools addon by Vardot.
