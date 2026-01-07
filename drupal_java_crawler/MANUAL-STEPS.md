# Manual Steps to Run Crawler

If the BAT file isn't working, follow these manual steps:

## Step 1: Start DDEV

```powershell
ddev start
```

Wait for DDEV to start (first time takes 2-3 minutes).

## Step 2: Verify Setup

```powershell
# Check Java
ddev exec java -version

# Check Maven
ddev exec mvn -version
```

Both should show version information. If not, restart DDEV:

```powershell
ddev restart
```

## Step 3: Clean Build

```powershell
ddev exec mvn clean compile
```

This should show `BUILD SUCCESS`.

## Step 4: Run Crawler

```powershell
ddev exec mvn exec:java
```





