package org.example;

/**
 * DrupalCrawler - Multi-threaded Web Crawler for Drupal Sites
 * 
 * This crawler analyzes Drupal websites and extracts form field information.
 * It supports two modes:
 *   1. Headless Mode (Jsoup) - Fast, API-based crawling
 *   2. Headed Mode (Selenium) - Browser-based crawling with JavaScript support
 * 
 * Features:
 *   - Multi-threaded concurrent crawling
 *   - Automatic form detection and field analysis
 *   - Cookie-based authentication
 *   - CSV export of results
 *   - Interactive runtime configuration
 * 
 * Usage:
 *   Run with: mvn exec:java
 *   Follow the interactive prompts to configure your crawl
 * 
 * Output:
 *   Results are saved to crawl_results_thread-<threadId>.csv
 */

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.interactions.Actions;
import io.github.bonigarcia.wdm.WebDriverManager;
import java.time.Duration;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class DrupalCrawler {

    // Thread-safe collections for managing crawl state
    // visited: tracks URLs that have been fully processed
    // enqueued: tracks URLs that have been added to the queue (prevents duplicates)
    // queue: work queue for URLs to be processed
    // processedCount: atomic counter for thread-safe counting

    private static final Set<String> visited = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final Set<String> enqueued = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final ConcurrentLinkedQueue<UrlTask> queue = new ConcurrentLinkedQueue<>();
    private static final AtomicInteger processedCount = new AtomicInteger(0);

    // Configurable at runtime
    private static String BASE_DOMAIN = "";
    private static String START_PATH = "";
    private static String COOKIE_STRING = "";

    private static boolean useSelenium = false;

    public static void main(String[] args) {
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║           DRUPAL CRAWLER - CONFIGURATION                   ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");
        System.out.println();
        
        // Prompt for URL configuration
        BASE_DOMAIN = promptForBaseUrl();
        START_PATH = promptForStartPath();
        
        // Prompt for cookie
        COOKIE_STRING = promptForCookie();
        
        // Prompt for crawl mode
        useSelenium = promptForMode();
        
        if (useSelenium) {
            System.out.println("Setting up Selenium WebDriver...");
            WebDriverManager.chromedriver().setup();
            System.out.println("WebDriver ready!");
        }
        
        int maxLinks = promptForMaxLinks();
        int numThreads = promptForNumThreads();
        String startUrl = BASE_DOMAIN + START_PATH;
        
        // Display configuration summary
        System.out.println();
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║           CRAWL CONFIGURATION SUMMARY                      ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");
        System.out.println("Base Domain:  " + BASE_DOMAIN);
        System.out.println("Start Path:   " + (START_PATH.isEmpty() ? "(root)" : START_PATH));
        System.out.println("Full URL:     " + startUrl);
        System.out.println("Cookie:       " + (COOKIE_STRING.isEmpty() ? "(none)" : COOKIE_STRING.substring(0, Math.min(50, COOKIE_STRING.length())) + "..."));
        System.out.println("Mode:         " + (useSelenium ? "Selenium (Headed)" : "Jsoup (Headless)"));
        System.out.println("Max Pages:    " + maxLinks);
        System.out.println("Threads:      " + numThreads);
        System.out.println("═══════════════════════════════════════════════════════════");
        System.out.println();
        System.out.println("Starting crawl...");
        System.out.println();
        
        long totalStartTime = System.currentTimeMillis();

        queue.add(new UrlTask(startUrl, "ROOT"));
        enqueued.add(startUrl);

        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        for (int i = 0; i < numThreads; i++) {
            executor.submit(new Worker(maxLinks));
        }

        executor.shutdown();
        try {
            executor.awaitTermination(24, TimeUnit.HOURS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        long totalElapsedTime = System.currentTimeMillis() - totalStartTime;
        System.out.println();
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║           CRAWL COMPLETE                                   ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");
        System.out.printf("Total pages visited: %d (limit: %d)%n", visited.size(), maxLinks);
        System.out.printf("Total time: %d ms (%.2f seconds)%n", totalElapsedTime, totalElapsedTime / 1000.0);
        System.out.println("Results saved to: crawl_results_thread-<threadId>.csv");
        System.out.println("═══════════════════════════════════════════════════════════");
    }

    private static String promptForBaseUrl() {
        Scanner scanner = new Scanner(System.in);
        String url = null;
        
        System.out.println("=".repeat(50));
        System.out.println("1. URL CONFIGURATION");
        System.out.println("=".repeat(50));
        
        while (url == null) {
            System.out.println("Enter Base Domain URL:");
            System.out.println("  Examples:");
            System.out.println("    - https://example.com/");
            System.out.println("    - http://localhost/");
            System.out.println("    - http://192.168.1.100/");
            System.out.print("> ");
            String line = scanner.nextLine().trim();
            
            if (line.isEmpty()) {
                System.out.println("❌ URL cannot be empty. Please try again.");
                continue;
            }
            
            // Ensure URL ends with /
            if (!line.endsWith("/")) {
                line = line + "/";
            }
            
            // Basic URL validation - accepts both http:// and https://
            if (line.startsWith("http://") || line.startsWith("https://")) {
                url = line;
                System.out.println("✓ Base Domain set: " + url);
            } else {
                System.out.println("❌ Invalid URL. Must start with http:// or https://");
            }
        }
        
        return url;
    }

    private static String promptForStartPath() {
        Scanner scanner = new Scanner(System.in);
        
        System.out.println();
        System.out.print("Enter Start Path (optional, press Enter to start from root): ");
        String path = scanner.nextLine().trim();
        
        if (path.isEmpty()) {
            System.out.println("✓ Starting from root path");
            return "";
        }
        
        // Remove leading slash if present
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        
        System.out.println("✓ Start Path set: " + path);
        return path;
    }

    private static String promptForCookie() {
        Scanner scanner = new Scanner(System.in);
        
        System.out.println();
        System.out.println("=".repeat(50));
        System.out.println("2. AUTHENTICATION CONFIGURATION");
        System.out.println("=".repeat(50));
        System.out.println("Enter Cookie String (optional, press Enter to skip):");
        System.out.println("Example: SESS123=abc; token=xyz");
        System.out.print("> ");
        
        String cookie = scanner.nextLine().trim();
        
        if (cookie.isEmpty()) {
            System.out.println("✓ No cookie set (anonymous access)");
            return "";
        }
        
        System.out.println("✓ Cookie configured (" + cookie.length() + " characters)");
        return cookie;
    }

    private static boolean promptForMode() {
        Scanner scanner = new Scanner(System.in);
        
        System.out.println();
        System.out.println("=".repeat(50));
        System.out.println("3. CRAWL MODE");
        System.out.println("=".repeat(50));
        
        while (true) {
            System.out.println("Select Crawl Mode:");
            System.out.println("1. Headless (API/Jsoup - Fast, no browser)");
            System.out.println("2. Headed (Selenium - Visual browser, slower)");
            System.out.print("Enter your choice (1 or 2): ");
            String choice = scanner.nextLine().trim();
            
            if (choice.equals("1")) {
                System.out.println("✓ Headless mode selected (API/Jsoup)");
                return false;
            } else if (choice.equals("2")) {
                System.out.println("✓ Headed mode selected (Selenium)");
                return true;
            } else {
                System.out.println("❌ Invalid choice. Please enter 1 or 2.");
            }
        }
    }

    private static int promptForMaxLinks() {
        Scanner scanner = new Scanner(System.in);
        Integer max = null;

        System.out.println();
        System.out.println("=".repeat(50));
        System.out.println("4. CRAWL LIMITS");
        System.out.println("=".repeat(50));

        while (max == null) {
            System.out.print("Enter the maximum number of pages to scan (integer > 0): ");
            String line = scanner.nextLine();
            try {
                int parsed = Integer.parseInt(line.trim());
                if (parsed > 0) {
                    max = parsed;
                } else {
                    System.out.println("❌ Please enter a number greater than 0.");
                }
            } catch (NumberFormatException ex) {
                System.out.println("❌ Invalid number. Try again.");
            }
        }

        System.out.println("✓ Max pages to scan: " + max);
        return max;
    }

    private static int promptForNumThreads() {
        Scanner scanner = new Scanner(System.in);
        Integer threads = null;
        int defaultThreads = Math.max(2, Runtime.getRuntime().availableProcessors());
        
        System.out.println();
        System.out.println("=".repeat(50));
        System.out.println("5. PERFORMANCE");
        System.out.println("=".repeat(50));
        
        while (threads == null) {
            System.out.print("Enter number of threads (default " + defaultThreads + "): ");
            String line = scanner.nextLine().trim();
            if (line.isEmpty()) {
                threads = defaultThreads;
                break;
            }
            try {
                int parsed = Integer.parseInt(line);
                if (parsed > 0 && parsed <= 64) {
                    threads = parsed;
                } else {
                    System.out.println("❌ Please enter a number between 1 and 64.");
                }
            } catch (NumberFormatException ex) {
                System.out.println("❌ Invalid number. Try again.");
            }
        }
        System.out.println("✓ Threads: " + threads);
        return threads;
    }

    private static class Worker implements Runnable {
        private final int maxLinks;
        private WebDriver driver;

        Worker(int maxLinks) {
            this.maxLinks = maxLinks;
        }

        @Override
        public void run() {
            System.out.printf("[%s] ═════════════════════════════════════════════════%n", Thread.currentThread().getName());
            System.out.printf("[%s] Worker initialized%n", Thread.currentThread().getName());
            System.out.printf("[%s] Target Domain: %s%n", Thread.currentThread().getName(), BASE_DOMAIN);
            System.out.printf("[%s] Using Cookie: %s%n", Thread.currentThread().getName(), 
                COOKIE_STRING.isEmpty() ? "No (anonymous)" : "Yes (" + COOKIE_STRING.length() + " chars)");
            System.out.printf("[%s] Mode: %s%n", Thread.currentThread().getName(), 
                useSelenium ? "Selenium" : "Jsoup");
            System.out.printf("[%s] ═════════════════════════════════════════════════%n", Thread.currentThread().getName());
            
            // Initialize Selenium driver if in headed mode
            if (useSelenium) {
                ChromeOptions options = new ChromeOptions();
                options.addArguments("--start-maximized");
                options.addArguments("--disable-blink-features=AutomationControlled");
                driver = new ChromeDriver(options);
                System.out.printf("[%s] ✓ Selenium browser initialized%n", Thread.currentThread().getName());
                
                // Step 1: Navigate to the start URL (main + path)
                String startUrl = BASE_DOMAIN + START_PATH;
                System.out.printf("[%s] Step 1: Navigating to %s%n", Thread.currentThread().getName(), startUrl);
                driver.get(startUrl);
                
                // Step 2: Inject cookies
                if (!COOKIE_STRING.isEmpty()) {
                    System.out.printf("[%s] Step 2: Injecting cookies...%n", Thread.currentThread().getName());
                    String[] cookies = COOKIE_STRING.split(";");
                    for (String cookie : cookies) {
                        cookie = cookie.trim();
                        if (!cookie.isEmpty()) {
                            String[] parts = cookie.split("=", 2);
                            if (parts.length == 2) {
                                String name = parts[0].trim();
                                String value = parts[1].trim();
                                org.openqa.selenium.Cookie seleniumCookie = new org.openqa.selenium.Cookie(name, value);
                                driver.manage().addCookie(seleniumCookie);
                                System.out.printf("[%s]   ✓ Cookie added: %s%n", Thread.currentThread().getName(), name);
                            }
                        }
                    }
                    
                    // Step 3: Navigate again to apply cookies
                    System.out.printf("[%s] Step 3: Reloading %s with cookies...%n", Thread.currentThread().getName(), startUrl);
                    driver.get(startUrl);
                    System.out.printf("[%s] ✓ Ready to crawl with authenticated session%n", Thread.currentThread().getName());
                } else {
                    System.out.printf("[%s] ✓ Ready to crawl (no authentication)%n", Thread.currentThread().getName());
                }
            }

            String fileName = "crawl_results_thread-" + Thread.currentThread().getId() + ".csv";
            try (PrintWriter csvWriter = new PrintWriter(new FileWriter(fileName))) {
                csvWriter.println("URL,Status Code,Referrer,Form Submission,IssueTypes,IssueSnippets");

                while (true) {
                    if (processedCount.get() >= maxLinks && queue.isEmpty()) {
                        break;
                    }

                    UrlTask task = queue.poll();
                    if (task == null) {
                        try { Thread.sleep(50); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
                        continue;
                    }

                    String currentUrl = task.url;
                    String referrer = task.referrer;

                    if (currentUrl.contains("/logout") || currentUrl.contains("/masquerade")) {
                        continue;
                    }

                    if (!visited.add(currentUrl)) {
                        continue;
                    }

                    int current = processedCount.incrementAndGet();
                    if (current > maxLinks) {
                        break;
                    }

                    long startTime = System.currentTimeMillis();
                    long duration;
                    int statusCode = -1;
                    String issueTypesField = "";
                    String issueSnippetsField = "";
                    String formSubmissionStatus = "N/A";

                    if (useSelenium) {
                        // Selenium mode
                        try {
                            driver.get(currentUrl);
                            duration = System.currentTimeMillis() - startTime;
                            statusCode = 200; // Selenium doesn't easily expose status codes

                            // Parse with Jsoup for message extraction
                            Document doc = Jsoup.parse(driver.getPageSource());
                            List<Message> messages = extractDrupalMessages(doc);
                            if (!messages.isEmpty()) {
                                issueTypesField = joinTypes(messages);
                                issueSnippetsField = joinSnippets(messages, 200);
                                System.out.println("[%s] ↳ Found %d message(s)%n"+messages.size()+messages.toString());
                            }

                            // Check if edit/add page
                            boolean isEditOrAddPage = currentUrl.contains("/edit") || currentUrl.contains("/add");
                            if (isEditOrAddPage) {
                                System.out.printf("[%s] Detected edit/add page: %s%n", 
                                    Thread.currentThread().getName(), currentUrl);
                                
                                try {
                                    List<WebElement> forms = driver.findElements(By.tagName("form"));
                                    for (WebElement form : forms) {
                                        String action = form.getAttribute("action");
                                        if (action == null || action.isEmpty()) {
                                            action = currentUrl;
                                        }
                                        
                                        if (action.contains("delete")) {
                                            formSubmissionStatus = "SKIPPED (Delete Action)";
                                            System.out.printf("[%s] Skipping form (contains delete)%n", 
                                                Thread.currentThread().getName());
                                            continue;
                                        }

                                        formSubmissionStatus = submitDrupalFormSelenium(driver, form);
                                        break;
                                    }
                                } catch (Exception e) {
                                    formSubmissionStatus = "ERROR: " + e.getMessage();
                                    System.err.printf("[%s] Form submission failed: %s%n", 
                                        Thread.currentThread().getName(), e.getMessage());
                                }
                            }

                            // Extract links
                            List<WebElement> links = driver.findElements(By.tagName("a"));
                            for (WebElement link : links) {
                                String href = link.getAttribute("href");
                                if (href != null && href.startsWith(BASE_DOMAIN)
                                        && !href.contains("/logout")
                                        && enqueued.add(href)) {
                                    queue.add(new UrlTask(href, currentUrl));
                                }
                            }
                        } catch (Exception e) {
                            duration = System.currentTimeMillis() - startTime;
                            statusCode = -1;
                            System.err.printf("[%s] Error: %s%n", Thread.currentThread().getName(), e.getMessage());
                        }
                    } else {
                        // Jsoup mode (original)
                        org.jsoup.Connection.Response response = Jsoup.connect(currentUrl)
                                .header("Cookie", COOKIE_STRING)
                                .userAgent("Mozilla/5.0")
                                .timeout(1000000)
                                .ignoreHttpErrors(true)
                                .execute();

                        duration = System.currentTimeMillis() - startTime;
                        statusCode = response.statusCode();

                        if (statusCode == 200) {
                            Document doc = response.parse();

                            List<Message> messages = extractDrupalMessages(doc);
                            if (!messages.isEmpty()) {
                                issueTypesField = joinTypes(messages);
                                issueSnippetsField = joinSnippets(messages, 200);
                                System.out.println("[%s] ↳ Found %d message(s)%n"+messages.size()+messages.toString());
                            }

                            // Check if this is an edit or add page
                            boolean isEditOrAddPage = currentUrl.contains("/edit") || currentUrl.contains("/add");
                            
                            if (isEditOrAddPage) {
                                System.out.printf("[%s] Detected edit/add page: %s%n", 
                                    Thread.currentThread().getName(), currentUrl);
                                
                                // Find the main form on the page
                                Elements forms = doc.select("form");
                                for (Element form : forms) {
                                    String action = form.absUrl("action");
                                    if (action.isEmpty()) {
                                        action = currentUrl;
                                    }

                                    // Skip if action contains "delete"
                                    if (action.contains("delete")) {
                                        System.out.printf("[%s] Skipping form submission (contains delete): %s%n", 
                                            Thread.currentThread().getName(), action);
                                        continue;
                                    }

                                    try {
                                        submitDrupalForm(form, action, currentUrl);
                                    } catch (Exception e) {
                                        System.err.printf("[%s] Failed to submit form at %s: %s%n", 
                                            Thread.currentThread().getName(), action, e.getMessage());
                                    }
                                    
                                    // Only submit the first main form
                                    break;
                                }
                            }

                            Elements links = doc.select("a[href]");
                            for (Element link : links) {
                                String href = link.absUrl("href");
                                if (href.startsWith(BASE_DOMAIN)
                                        && !href.contains("/logout")
                                        && enqueued.add(href)) {
                                    queue.add(new UrlTask(href, currentUrl));
                                }
                            }
                        }
                    }

                    int remainingAfter = queue.size();

                    csvWriter.printf("\"%s\",%d,\"%s\",\"%s\",\"%s\",\"%s\"%n",
                            escapeCsv(currentUrl),
                            statusCode,
                            escapeCsv(referrer),
                            escapeCsv(formSubmissionStatus),
                            escapeCsv(issueTypesField),
                            escapeCsv(issueSnippetsField));
                    csvWriter.flush();

                    System.out.printf("[%s] #%d | %d | %d ms | Remaining: %d | %s | Ref: %s%n",
                            Thread.currentThread().getName(), current, statusCode, duration, remainingAfter, currentUrl, referrer);

                }
            } catch (IOException e) {
                System.err.println("Failed to write CSV for thread " + Thread.currentThread().getName() + ": " + e.getMessage());
            } finally {
                if (driver != null) {
                    driver.quit();
                    System.out.printf("[%s] Browser closed%n", Thread.currentThread().getName());
                }
            }
        }
    }

    private static List<Message> extractDrupalMessages(Document doc) {
        String selector = String.join(", ",
                "#messages",
                ".messages",
                ".messages--status",
                ".messages--warning",
                ".messages--error",
                ".messages__wrapper",
                ".messages__content",
                ".messages-list__wrapper",
                ".messages-list__item",
                "details.error-with-backtrace",
                "pre.backtrace",
                ".alert",
                ".alert-warning",
                ".alert-danger",
                ".alert-error",
                "[role=alert]",
                "[aria-live=assertive]",
                ".form-item--error-message",
                ".error-message",
                "div.error",
                "span.error",
                ".field-validation-error",
                ".form-error",
                ".validation-error");

        Elements candidates = doc.select(selector);

        LinkedHashMap<String, Message> unique = new LinkedHashMap<>();
        for (Element el : candidates) {
            String text = normalize(extractMessageText(el));
            if (text.isEmpty()) continue;

            String type = classifyWithParents(el);
            unique.putIfAbsent(text, new Message(type, text));
        }

        return new ArrayList<>(unique.values());
    }

    private static String extractMessageText(Element el) {
        Element content = el.selectFirst(".messages__content");
        if (content != null) {
            return content.text();
        }
        Element detailsSummary = el.selectFirst("details > summary");
        if (detailsSummary != null) {
            return detailsSummary.text();
        }
        return el.text();
    }

    private static String classifyWithParents(Element el) {
        List<Element> lineage = new ArrayList<>();
        lineage.add(el);
        lineage.addAll(el.parents());

        boolean hasError = false;
        boolean hasWarning = false;
        boolean hasStatus = false;
        boolean hasAlert = false;

        for (int i = 0; i < Math.min(5, lineage.size()); i++) {
            Set<String> classes = lineage.get(i).classNames();
            String cls = String.join(" ", classes).toLowerCase(Locale.ROOT);
            if (cls.contains("messages--error") || cls.contains("alert-danger") || cls.contains("alert-error") || hasWord(cls, "error")) hasError = true;
            if (cls.contains("messages--warning") || cls.contains("alert-warning") || hasWord(cls, "warning")) hasWarning = true;
            if (cls.contains("messages--status") || cls.contains("alert-info") || hasWord(cls, "status")) hasStatus = true;
            if (cls.contains("alert")) hasAlert = true;
        }

        if (hasError) return "error";
        if (hasWarning) return "warning";
        if (hasStatus) return "info";
        if (hasAlert) return "alert";
        return "info";
    }

    private static boolean hasWord(String haystack, String word) {
        return haystack.matches(".*(^|\\s)" + java.util.regex.Pattern.quote(word) + "(\\s|$).*");
    }

    private static String joinTypes(List<Message> messages) {
        List<String> types = new ArrayList<>();
        for (Message m : messages) {
            types.add(m.type);
        }
        return String.join(";", types);
    }

    private static String joinSnippets(List<Message> messages, int maxSnippetLen) {
        List<String> snippets = new ArrayList<>();
        for (Message m : messages) {
            snippets.add(truncate(m.text, maxSnippetLen));
        }
        return String.join("; ", snippets);
    }

    private static String normalize(String s) {
        if (s == null) return "";
        return s.replaceAll("\\s+", " ").trim();
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        if (s.length() <= maxLen) return s;
        return s.substring(0, Math.max(0, maxLen - 1)) + "…";
    }

    private static String escapeCsv(String s) {
        if (s == null) return "";
        return s.replace("\"", "\"\"");
    }

    private static String determineFillValue(String name, String type, String value, String placeholder) {
        // Return existing value if present and valid (except for empty required fields)
        if (!value.isEmpty() && !value.equals("") && !value.equals("0")) {
            return value;
        }

        String nameLower = name.toLowerCase();
        
        // ============= HTML INPUT TYPES =============
        if ("email".equals(type)) {
            return "automated.test@example.com";
        } else if ("number".equals(type)) {
            return "1";
        } else if ("url".equals(type)) {
            return "https://example.com";
        } else if ("tel".equals(type)) {
            return "+1234567890";
        } else if ("date".equals(type)) {
            return "2024-12-11";
        } else if ("datetime-local".equals(type)) {
            return "2024-12-11T12:00";
        } else if ("time".equals(type)) {
            return "12:00:00";
        } else if ("color".equals(type)) {
            return "#FF0000";
        }

        // ============= TEXT FIELDS (Plain & Formatted) =============
        // Text (formatted) with format selector
        if (name.matches(".*\\[format\\].*")) {
            return "basic_html";
        }
        // Text (formatted, long, with summary) - summary field
        if (name.matches(".*\\[summary\\].*")) {
            return "Automated test summary content";
        }
        // Text value fields
        if (name.matches(".*\\[value\\].*")) {
            if (nameLower.contains("body") || nameLower.contains("description")) {
                return "Automated test content with detailed information for testing purposes. " + System.currentTimeMillis();
            }
            return "Automated test content " + System.currentTimeMillis();
        }

        // ============= NUMBER FIELDS =============
        // Integer
        if (nameLower.contains("integer") || nameLower.contains("quantity") || nameLower.contains("count")) {
            return "42";
        }
        // Decimal
        if (nameLower.contains("decimal") || nameLower.contains("amount")) {
            return "99.99";
        }
        // Float
        if (nameLower.contains("float") || nameLower.contains("rating")) {
            return "4.5";
        }
        // Commerce Price fields
        if (nameLower.contains("price[") || nameLower.contains("amount[")) {
            if (nameLower.contains("number")) {
                return "99.99";
            }
            if (nameLower.contains("currency_code")) {
                return "USD";
            }
        }

        // ============= DATE & TIME FIELDS =============
        // Date composite fields
        if (name.matches(".*\\[date\\]\\[date\\].*") || name.matches(".*\\[value\\]\\[date\\].*")) {
            return "2024-12-11";
        }
        if (name.matches(".*\\[date\\]\\[time\\].*") || name.matches(".*\\[value\\]\\[time\\].*")) {
            return "12:00:00";
        }
        // Timestamp fields
        if (nameLower.contains("created") || nameLower.contains("changed") || nameLower.contains("timestamp")) {
            return "2024-12-11 12:00:00";
        }

        // ============= BOOLEAN FIELDS =============
        // Handled as checkboxes in main logic

        // ============= LINK FIELDS =============
        if (name.matches(".*\\[uri\\].*")) {
            if (placeholder != null && (placeholder.contains("http") || placeholder.contains("://"))) {
                return "https://example.com";
            } else {
                return "/node/1";
            }
        }
        if (name.matches(".*\\[title\\].*") && name.matches(".*field.*")) {
            return "Link Title";
        }
        if (name.matches(".*\\[options\\]\\[attributes\\]\\[target\\].*")) {
            return "_blank";
        }

        // ============= EMAIL FIELDS =============
        if (nameLower.contains("email") || nameLower.contains("mail")) {
            return "automated.test@example.com";
        }

        // ============= REFERENCE FIELDS =============
        // Entity reference (autocomplete) - leave empty unless we have valid IDs
        if (name.matches(".*\\[target_id\\].*")) {
            return "";
        }
        // Media reference (entity reference to media)
        if (nameLower.contains("field_media") && nameLower.contains("target_id")) {
            return ""; // Would need valid media entity ID
        }
        // Taxonomy term reference
        if (nameLower.contains("field_tags") || nameLower.contains("taxonomy")) {
            return ""; // Would need valid term ID
        }

        // ============= PATH & ALIAS =============
        if (nameLower.contains("path[") || nameLower.contains("alias")) {
            return "/automated-test-" + System.currentTimeMillis();
        }

        // ============= LANGUAGE =============
        if (nameLower.contains("langcode") || (nameLower.contains("language") && !nameLower.contains("field"))) {
            return "en";
        }

        // ============= STATUS FIELDS =============
        // Published/unpublished (handled as checkbox)
        if (nameLower.contains("status[value]")) {
            return "1";
        }
        // Promoted to front page
        if (nameLower.contains("promote[value]")) {
            return "0";
        }
        // Sticky at top
        if (nameLower.contains("sticky[value]")) {
            return "0";
        }

        // ============= REVISION LOG =============
        if (nameLower.contains("revision_log")) {
            return "Automated test revision";
        }

        // ============= ADDRESS MODULE =============
        if (nameLower.contains("address[")) {
            if (nameLower.contains("country_code")) {
                return "US";
            } else if (nameLower.contains("address_line1")) {
                return "123 Test Street";
            } else if (nameLower.contains("address_line2")) {
                return "Apt 456";
            } else if (nameLower.contains("locality") || nameLower.contains("city")) {
                return "Test City";
            } else if (nameLower.contains("administrative_area") || nameLower.contains("state")) {
                return "CA";
            } else if (nameLower.contains("postal_code") || nameLower.contains("zip")) {
                return "12345";
            } else if (nameLower.contains("given_name") || nameLower.contains("first")) {
                return "John";
            } else if (nameLower.contains("family_name") || nameLower.contains("last")) {
                return "Doe";
            } else if (nameLower.contains("organization")) {
                return "Test Organization";
            }
        }

        // ============= TELEPHONE =============
        if (nameLower.contains("telephone") || nameLower.contains("phone")) {
            return "+1-555-123-4567";
        }

        // ============= GEOLOCATION =============
        if (nameLower.contains("geolocation")) {
            if (nameLower.contains("lat")) {
                return "40.7128";
            } else if (nameLower.contains("lng") || nameLower.contains("lon")) {
                return "-74.0060";
            }
        }

        // ============= VIDEO EMBED =============
        if (nameLower.contains("video") && nameLower.contains("url")) {
            return "https://www.youtube.com/watch?v=dQw4w9WgXcQ";
        }

        // ============= METATAG =============
        if (nameLower.contains("metatag[") || nameLower.contains("meta_")) {
            if (nameLower.contains("title")) {
                return "Test Meta Title";
            } else if (nameLower.contains("description")) {
                return "Test meta description for SEO";
            } else if (nameLower.contains("keywords")) {
                return "test, automated, drupal";
            }
            return "test value";
        }

        // ============= WEBFORM =============
        if (nameLower.contains("webform")) {
            return "Webform test value";
        }

        // ============= STANDARD FIELD NAMES =============
        if (nameLower.contains("title") && !nameLower.contains("field")) {
            return "Automated Test " + System.currentTimeMillis();
        }
        if (nameLower.contains("name") && !nameLower.contains("field_name")) {
            return "Test Name " + System.currentTimeMillis();
        }
        if (nameLower.contains("summary")) {
            return "Automated test summary";
        }
        if (nameLower.contains("description")) {
            return "Automated test description";
        }
        if (nameLower.contains("subject")) {
            return "Test Subject";
        }
        if (nameLower.contains("message") || nameLower.contains("comment")) {
            return "Test message content";
        }

        // ============= DEFAULT =============
        return "test_value";
    }

    private static String submitDrupalFormSelenium(WebDriver driver, WebElement form) {
        try {
            // Check for media selection requirements FIRST
            JavascriptExecutor js = (JavascriptExecutor) driver;
            
            // Check for media/image/video requirements in form
            List<WebElement> mediaRequirements = form.findElements(By.cssSelector(
                ".form-required, .fieldset__label.form-required, span.form-required, label.form-required"
            ));
            
            for (WebElement req : mediaRequirements) {
                String text = req.getText().toLowerCase();
                if ((text.contains("media") || text.contains("image") || text.contains("video") || 
                     text.contains("slide") || text.contains("gallery")) && 
                    !text.contains("url") && !text.contains("embed")) {
                    String reason = "SKIPPED (Requires Media: " + req.getText().trim() + ")";
                    System.out.printf("[%s] ⊗ %s%n", Thread.currentThread().getName(), reason);
                    return reason;
                }
            }
            
            // Check for file inputs
            List<WebElement> fileInputs = form.findElements(By.cssSelector("input[type=file]"));
            if (!fileInputs.isEmpty()) {
                System.out.printf("[%s] ⊗ SKIPPED: Form requires file upload%n", 
                    Thread.currentThread().getName());
                return "SKIPPED (Requires File Upload)";
            }
        } catch (Exception e) {
            // Continue if check fails
        }
        
        int maxRetries = 3;
        boolean firstAttempt = true;
        
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                System.out.printf("[%s] Form submission attempt %d/%d%n", 
                    Thread.currentThread().getName(), attempt, maxRetries);

                String originalUrl = driver.getCurrentUrl();
                WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(3)); // Reduced from 10 to 3
                JavascriptExecutor js = (JavascriptExecutor) driver;

                // Only fill all fields on first attempt
                if (firstAttempt) {
                    System.out.printf("[%s] Filling form fields...%n", Thread.currentThread().getName());
                    List<WebElement> inputs = form.findElements(By.cssSelector("input, textarea, select"));
                    int fieldsProcessed = 0;
                    int requiredFieldsFilled = 0;

                    for (WebElement input : inputs) {
                        try {
                            String name = input.getAttribute("name");
                            String type = input.getAttribute("type");
                            String tagName = input.getTagName();
                            boolean required = "true".equals(input.getAttribute("required")) || 
                                             "required".equals(input.getAttribute("required"));

                            if (name == null || name.isEmpty()) continue;
                            if ("submit".equals(type) || "button".equals(type) || "file".equals(type)) continue;

                            if ("hidden".equals(type)) {
                                fieldsProcessed++;
                            } else if ("checkbox".equals(type)) {
                                if (!input.isSelected()) {
                                    js.executeScript("arguments[0].click();", input);
                                    fieldsProcessed++;
                                }
                            } else if ("radio".equals(type)) {
                                if (!input.isSelected()) {
                                    js.executeScript("arguments[0].click();", input);
                                    fieldsProcessed++;
                                }
                            } else if ("select".equals(tagName)) {
                                Select select = new Select(input);
                                List<WebElement> options = select.getOptions();
                                for (WebElement option : options) {
                                    String value = option.getAttribute("value");
                                    if (value != null && !value.isEmpty() && !value.equals("_none")) {
                                        select.selectByValue(value);
                                        fieldsProcessed++;
                                        if (required) requiredFieldsFilled++;
                                        break;
                                    }
                                }
                            } else if ("textarea".equals(tagName)) {
                                String currentValue = (String) js.executeScript("return arguments[0].value;", input);
                                if (currentValue == null || currentValue.isEmpty()) {
                                    String textareaId = input.getAttribute("id");
                                    String content = determineFillValue(name, "textarea", "", "");
                                    boolean filled = false;
                                    
                                    if (textareaId != null && !textareaId.isEmpty()) {
                                        // Check for CKEditor 5
                                        String ckeditorId = input.getAttribute("data-ckeditor5-id");
                                        if (ckeditorId != null && !ckeditorId.isEmpty()) {
                                            Boolean hasCKEditor = (Boolean) js.executeScript(
                                                "var textarea = document.getElementById(arguments[0]);" +
                                                "var container = textarea.nextElementSibling;" +
                                                "return container && container.classList.contains('ck-editor');",
                                                textareaId);
                                            
                                            if (Boolean.TRUE.equals(hasCKEditor)) {
                                                js.executeScript(
                                                    "var textarea = document.getElementById(arguments[0]);" +
                                                    "var container = textarea.nextElementSibling;" +
                                                    "var editable = container.querySelector('.ck-editor__editable');" +
                                                    "if (editable) {" +
                                                    "  editable.innerHTML = '<p>' + arguments[1] + '</p>';" +
                                                    "  var event = new Event('input', { bubbles: true });" +
                                                    "  editable.dispatchEvent(event);" +
                                                    "  textarea.value = arguments[1];" +
                                                    "  console.log('CKEditor 5 filled');" +
                                                    "}",
                                                    textareaId, content);
                                                System.out.printf("[%s]   ✓ CKEditor 5: %s%n", 
                                                    Thread.currentThread().getName(), name);
                                                filled = true;
                                            }
                                        }
                                        
                                        // Check for ACE editor if not CKEditor
                                        if (!filled) {
                                            String aceEditorId = textareaId + "-ace-editor";
                                            Boolean hasAceEditor = (Boolean) js.executeScript(
                                                "return document.getElementById(arguments[0]) !== null && " +
                                                "typeof ace !== 'undefined';", aceEditorId);
                                            
                                            if (Boolean.TRUE.equals(hasAceEditor)) {
                                                js.executeScript(
                                                    "try { " +
                                                    "  var editor = ace.edit(arguments[0]); " +
                                                    "  editor.setValue(arguments[1], -1); " +
                                                    "  editor.clearSelection(); " +
                                                    "} catch(e) { console.log('ACE error:', e); }", 
                                                    aceEditorId, content);
                                                System.out.printf("[%s]   ✓ ACE Editor: %s%n", 
                                                    Thread.currentThread().getName(), name);
                                                filled = true;
                                            }
                                        }
                                    }
                                    
                                    // Fallback to regular textarea
                                    if (!filled) {
                                        js.executeScript("arguments[0].value = arguments[1];", input, content);
                                    }
                                    
                                    fieldsProcessed++;
                                    if (required) requiredFieldsFilled++;
                                }
                            } else {
                                // Text-like inputs
                                String currentValue = (String) js.executeScript("return arguments[0].value;", input);
                                if (currentValue == null || currentValue.isEmpty()) {
                                    String fillValue = determineFillValue(name, type != null ? type : "", "", 
                                        input.getAttribute("placeholder"));
                                    
                                    if (fillValue != null && !fillValue.isEmpty()) {
                                        js.executeScript("arguments[0].value = arguments[1];", input, fillValue);
                                        fieldsProcessed++;
                                        if (required) {
                                            requiredFieldsFilled++;
                                            System.out.printf("[%s]   ✓ Required: %s%n", 
                                                Thread.currentThread().getName(), name);
                                        }
                                    }
                                }
                            }
                        } catch (Exception e) {
                            // Skip problematic field
                        }
                    }

                    System.out.printf("[%s] Filled %d fields (%d required)%n", 
                        Thread.currentThread().getName(), fieldsProcessed, requiredFieldsFilled);
                    firstAttempt = false;
                } else {
                    // On retry, only fill missing/invalid fields
                    System.out.printf("[%s] Checking for missing fields...%n", Thread.currentThread().getName());
                    
                    List<WebElement> invalidFields = (List<WebElement>) js.executeScript(
                        "var form = arguments[0];" +
                        "var invalid = [];" +
                        "var inputs = form.querySelectorAll('input:not([type=hidden]):not([type=submit]):not([type=button]), textarea, select');" +
                        "for(var i=0; i<inputs.length; i++) {" +
                        "  var inp = inputs[i];" +
                        "  if(inp.validity && !inp.validity.valid) {" +
                        "    invalid.push(inp);" +
                        "  } else if(inp.hasAttribute('required') && (!inp.value || inp.value.trim() === '')) {" +
                        "    invalid.push(inp);" +
                        "  }" +
                        "}" +
                        "return invalid;", form);

                    if (invalidFields.isEmpty()) {
                        System.out.printf("[%s] No missing fields found%n", Thread.currentThread().getName());
                    } else {
                        System.out.printf("[%s] Found %d missing/invalid fields, filling them...%n", 
                            Thread.currentThread().getName(), invalidFields.size());
                        
                        for (WebElement invalid : invalidFields) {
                            String name = invalid.getAttribute("name");
                            String type = invalid.getAttribute("type");
                            String tagName = invalid.getTagName();
                            System.out.printf("[%s]   ↻ Filling: %s%n", Thread.currentThread().getName(), name);
                            
                            String fillValue = determineFillValue(name, type != null ? type : "", "", "");
                            if (fillValue != null && !fillValue.isEmpty()) {
                                boolean filled = false;
                                
                                // Check if it's a rich text editor textarea
                                if ("textarea".equals(tagName)) {
                                    String textareaId = invalid.getAttribute("id");
                                    if (textareaId != null && !textareaId.isEmpty()) {
                                        // Check CKEditor 5
                                        String ckeditorId = invalid.getAttribute("data-ckeditor5-id");
                                        if (ckeditorId != null && !ckeditorId.isEmpty()) {
                                            Boolean hasCKEditor = (Boolean) js.executeScript(
                                                "var textarea = document.getElementById(arguments[0]);" +
                                                "var container = textarea.nextElementSibling;" +
                                                "return container && container.classList.contains('ck-editor');",
                                                textareaId);
                                            
                                            if (Boolean.TRUE.equals(hasCKEditor)) {
                                                js.executeScript(
                                                    "var textarea = document.getElementById(arguments[0]);" +
                                                    "var container = textarea.nextElementSibling;" +
                                                    "var editable = container.querySelector('.ck-editor__editable');" +
                                                    "if (editable) {" +
                                                    "  editable.innerHTML = '<p>' + arguments[1] + '</p>';" +
                                                    "  textarea.value = arguments[1];" +
                                                    "}",
                                                    textareaId, fillValue);
                                                filled = true;
                                            }
                                        }
                                        
                                        // Check ACE editor
                                        if (!filled) {
                                            String aceEditorId = textareaId + "-ace-editor";
                                            Boolean hasAceEditor = (Boolean) js.executeScript(
                                                "return document.getElementById(arguments[0]) !== null && typeof ace !== 'undefined';", 
                                                aceEditorId);
                                            
                                            if (Boolean.TRUE.equals(hasAceEditor)) {
                                                js.executeScript(
                                                    "try { var editor = ace.edit(arguments[0]); editor.setValue(arguments[1], -1); } " +
                                                    "catch(e) { console.log(e); }", 
                                                    aceEditorId, fillValue);
                                                filled = true;
                                            }
                                        }
                                    }
                                }
                                
                                // Regular field if not filled yet
                                if (!filled) {
                                    js.executeScript("arguments[0].value = arguments[1];", invalid, fillValue);
                                }
                            }
                        }
                    }
                }

                // Submit form with multiple strategies
                System.out.printf("[%s] Submitting form...%n", Thread.currentThread().getName());
                
                WebElement submitBtn = null;
                boolean submitted = false;
                
                // Try multiple selectors to find submit button
                String[] submitSelectors = {
                    "input[type=submit][value='Save']",
                    "input#edit-submit",
                    "input[data-drupal-selector='edit-submit']",
                    "button[type=submit]",
                    "input[type=submit]",
                    ".button--primary",
                    ".form-submit"
                };
                
                for (String selector : submitSelectors) {
                    List<WebElement> buttons = form.findElements(By.cssSelector(selector));
                    if (!buttons.isEmpty()) {
                        submitBtn = buttons.get(0);
                        System.out.printf("[%s] Found submit button with selector: %s%n", 
                            Thread.currentThread().getName(), selector);
                        break;
                    }
                }
                
                if (submitBtn != null) {
                    // Scroll button into view
                    js.executeScript("arguments[0].scrollIntoView({behavior: 'instant', block: 'center'});", submitBtn);
                    Thread.sleep(200);
                    
                    // Remove any overlays that might block the button
                    js.executeScript(
                        "var overlays = document.querySelectorAll('.ui-widget-overlay, .modal-backdrop, .overlay');" +
                        "overlays.forEach(function(o) { o.style.display = 'none'; });"
                    );
                    
                    // Try clicking with multiple methods
                    String[] clickMethods = {"javascript", "actions", "selenium", "form-submit"};
                    
                    for (String method : clickMethods) {
                        try {
                            System.out.printf("[%s] Trying click method: %s%n", 
                                Thread.currentThread().getName(), method);
                            
                            switch (method) {
                                case "javascript":
                                    // Most reliable - direct JavaScript click
                                    js.executeScript("arguments[0].click();", submitBtn);
                                    break;
                                    
                                case "actions":
                                    // Actions class click
                                    Actions actions = new Actions(driver);
                                    actions.moveToElement(submitBtn).click().perform();
                                    break;
                                    
                                case "selenium":
                                    // Standard Selenium click
                                    WebDriverWait clickWait = new WebDriverWait(driver, Duration.ofSeconds(2));
                                    clickWait.until(ExpectedConditions.elementToBeClickable(submitBtn));
                                    submitBtn.click();
                                    break;
                                    
                                case "form-submit":
                                    // Direct form submission
                                    js.executeScript("arguments[0].submit();", form);
                                    break;
                            }
                            
                            submitted = true;
                            System.out.printf("[%s] ✓ Clicked successfully with: %s%n", 
                                Thread.currentThread().getName(), method);
                            break;
                            
                        } catch (Exception e) {
                            System.out.printf("[%s] ✗ %s failed: %s%n", 
                                Thread.currentThread().getName(), method, e.getMessage());
                            continue;
                        }
                    }
                    
                    if (!submitted) {
                        System.out.printf("[%s] ❌ All click methods failed%n", 
                            Thread.currentThread().getName());
                        return "FAILED (All Click Methods Failed)";
                    }
                    
                    Thread.sleep(2000);

                    String newUrl = driver.getCurrentUrl();
                    if (!newUrl.equals(originalUrl)) {
                        String successMsg = "SUCCESS (Redirected to: " + newUrl + ")";
                        System.out.printf("[%s] ✅ %s%n", Thread.currentThread().getName(), successMsg);
                        return successMsg;
                    } else {
                        System.out.printf("[%s] ❌ FAILED: Stayed on same page%n", 
                            Thread.currentThread().getName());
                        
                        // Check for error messages
                        Document doc = Jsoup.parse(driver.getPageSource());
                        List<Message> messages = extractDrupalMessages(doc);
                        if (!messages.isEmpty()) {
                            System.out.printf("[%s] ERRORS: %s%n", 
                                Thread.currentThread().getName(), joinSnippets(messages, 300));
                        }
                        
                        if (attempt < maxRetries) {
                            Thread.sleep(500);
                            continue;
                        }
                    }
                } else {
                    System.out.printf("[%s] ❌ No submit button found%n", 
                        Thread.currentThread().getName());
                    return "FAILED (No Submit Button Found)";
                }
            } catch (Exception e) {
                System.err.printf("[%s] Error (attempt %d): %s%n", 
                    Thread.currentThread().getName(), attempt, e.getMessage());
                if (attempt < maxRetries) {
                    try { Thread.sleep(500); } catch (InterruptedException ie) {}
                } else {
                    return "FAILED (Error: " + e.getMessage() + ")";
                }
            }
        }
        
        System.out.printf("[%s] ❌ Failed after %d attempts%n", 
            Thread.currentThread().getName(), maxRetries);
        return "FAILED (After " + maxRetries + " Attempts)";
    }

    private static void submitDrupalForm(Element form, String action, String referrer) throws IOException {
        // Check if form requires file upload - skip if so
        Elements fileInputs = form.select("input[type=file]");
        if (!fileInputs.isEmpty()) {
            System.out.printf("[%s] Skipping form submission (requires file upload): %s%n", 
                Thread.currentThread().getName(), action);
            return;
        }

        System.out.printf("[%s] Attempting to submit Drupal form to: %s%n", 
            Thread.currentThread().getName(), action);

        org.jsoup.Connection connection = Jsoup.connect(action)
                .header("Cookie", COOKIE_STRING)
                .userAgent("Mozilla/5.0")
                .timeout(1000000)
                .referrer(referrer)
                .ignoreHttpErrors(true)
                .followRedirects(true)
                .method(org.jsoup.Connection.Method.POST);

        // Collect all form fields - be aggressive and fill ALL fields
        Elements allInputs = form.select("input, textarea, select");
        int fieldsProcessed = 0;
        int totalFields = 0;
        java.util.Set<String> radioGroups = new java.util.HashSet<>();
        java.util.Set<String> processedNames = new java.util.HashSet<>();
        
        for (Element input : allInputs) {
            totalFields++;
            String name = input.attr("name");
            String type = input.attr("type").toLowerCase();
            String value = input.attr("value");
            String placeholder = input.attr("placeholder");
            String tagName = input.tagName();

            if (name.isEmpty() || processedNames.contains(name)) {
                continue;
            }

            // Skip buttons and file inputs
            if ("submit".equals(type) || "button".equals(type) || "image".equals(type) || "file".equals(type)) {
                continue;
            }

            // ALWAYS process hidden fields (tokens, form IDs, etc.)
            if ("hidden".equals(type)) {
                connection.data(name, value);
                processedNames.add(name);
                fieldsProcessed++;
                continue;
            }

            // Handle checkboxes - check all that have values or are pre-checked
            if ("checkbox".equals(type)) {
                if (input.hasAttr("checked") || !value.isEmpty()) {
                    connection.data(name, value.isEmpty() ? "1" : value);
                    processedNames.add(name);
                    fieldsProcessed++;
                }
                continue;
            }

            // Handle radio buttons - select first in each group
            if ("radio".equals(type)) {
                if (input.hasAttr("checked")) {
                    connection.data(name, value);
                    radioGroups.add(name);
                    processedNames.add(name);
                    fieldsProcessed++;
                } else if (!radioGroups.contains(name)) {
                    connection.data(name, value);
                    radioGroups.add(name);
                    processedNames.add(name);
                    fieldsProcessed++;
                }
                continue;
            }

            // Handle select dropdowns - ALWAYS select first valid option
            if ("select".equals(tagName)) {
                Element selected = input.selectFirst("option[selected]");
                if (selected != null) {
                    connection.data(name, selected.attr("value"));
                    processedNames.add(name);
                    fieldsProcessed++;
                } else {
                    Elements options = input.select("option");
                    for (Element option : options) {
                        String optValue = option.attr("value");
                        if (!optValue.isEmpty() && !optValue.equals("_none") && !optValue.equals("-") && !optValue.equals("")) {
                            connection.data(name, optValue);
                            processedNames.add(name);
                            fieldsProcessed++;
                            break;
                        }
                    }
                }
                continue;
            }

            // Handle textareas - ALWAYS fill
            if ("textarea".equals(tagName)) {
                String textValue = !value.isEmpty() ? value : "Automated test content for field";
                connection.data(name, textValue);
                processedNames.add(name);
                fieldsProcessed++;
                continue;
            }

            // Handle all text-like inputs - determine appropriate value
            String fillValue = determineFillValue(name, type, value, placeholder);
            
            if (fillValue != null) {
                connection.data(name, fillValue);
                processedNames.add(name);
                fieldsProcessed++;
            }
        }

        System.out.printf("[%s] Processed %d of %d total fields, submitting...%n", 
            Thread.currentThread().getName(), fieldsProcessed, totalFields);

        // Execute the POST request
        org.jsoup.Connection.Response postResponse = connection.execute();
        int postStatusCode = postResponse.statusCode();
        String finalUrl = postResponse.url().toString();
        
        // Parse response and extract messages
        Document responseDoc = postResponse.parse();
        List<Message> postMessages = extractDrupalMessages(responseDoc);
        
        // Also check for inline error messages
        Elements errorMessages = responseDoc.select(".messages--error, .form-item--error-message, .error");
        if (errorMessages.size() > 0 && postMessages.isEmpty()) {
            for (Element error : errorMessages) {
                String errorText = error.text();
                if (!errorText.isEmpty()) {
                    postMessages.add(new Message("error", errorText));
                }
            }
        }
        
        // Drupal redirects on success (302/303), returns 200 on validation failure
        if (postStatusCode == 302 || postStatusCode == 303 || !finalUrl.equals(action)) {
            // Success - form was accepted and redirected
            System.out.printf("[%s] ✅ SUCCESS: Form submitted - Redirected to: %s%n", 
                Thread.currentThread().getName(), finalUrl);
            if (!postMessages.isEmpty()) {
                System.out.printf("[%s] SUCCESS messages: %s%n", 
                    Thread.currentThread().getName(), joinSnippets(postMessages, 300));
            }
        } else if (postStatusCode == 200) {
            // Failure - form validation errors, stayed on same page
            System.out.printf("[%s] ❌ FAILED: Form validation errors - Status: 200%n", 
                Thread.currentThread().getName());
            if (!postMessages.isEmpty()) {
                System.out.printf("[%s] VALIDATION ERRORS: %s%n", 
                    Thread.currentThread().getName(), joinSnippets(postMessages, 300));
            } else {
                System.out.printf("[%s] No specific error messages found - check required fields%n", 
                    Thread.currentThread().getName());
            }
        } else {
            // Other status codes
            System.out.printf("[%s] ⚠ Form submitted - Status: %d%n", 
                Thread.currentThread().getName(), postStatusCode);
            if (!postMessages.isEmpty()) {
                System.out.printf("[%s] Response messages: %s%n", 
                    Thread.currentThread().getName(), joinSnippets(postMessages, 300));
            }
        }
    }

    private static class UrlTask {
        String url;
        String referrer;

        UrlTask(String url, String referrer) {
            this.url = url;
            this.referrer = referrer;
        }
    }

    private static class Message {
        String type;
        String text;

        Message(String type, String text) {
            this.type = type;
            this.text = text;
        }

        @Override
        public String toString() {
            return "[" + type + "] " + text;
        }
    }
}


