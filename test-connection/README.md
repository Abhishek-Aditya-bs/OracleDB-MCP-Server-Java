# Database Connection Testing

This directory contains utilities for testing Oracle database connectivity before integrating with the main MCP server.

## TestJDBC.java

A standalone Java utility to test Oracle database connections using Kerberos authentication.

### ⚠️ **IMPORTANT: Configuration Required Before Use**

The `TestJDBC.java` file contains **placeholder values** that must be replaced with your actual database and Kerberos configuration details before running.

### Prerequisites

1. **Oracle JDBC Driver**: Download `ojdbc11.jar` from Oracle's website
2. **Valid Kerberos Ticket**: Ensure you have a valid Kerberos ticket using `klist`
3. **Proper Configuration**: Ensure your `krb5.conf` is properly configured
4. **⚠️ Update Placeholders**: Replace all placeholder values in `TestJDBC.java` (see Configuration section below)

### Configuration

Before running the test, you **MUST** update the following placeholders in `TestJDBC.java`:

#### 1. **Kerberos Configuration Path**
```java
// Line ~27: Replace with your actual krb5.conf file path
System.setProperty("java.security.krb5.conf", "/path/to/your/krb5.conf");
```
**Example**: 
- Linux/Mac: `"/etc/krb5.conf"` or `"/home/username/.krb5/krb5.conf"`
- Windows: `"C:\\Windows\\krb5.conf"` or `"C:\\Users\\username\\krb5.conf"`

#### 2. **Kerberos Cache File Path**
```java
// Line ~32: Replace with your actual Kerberos cache file path
props.setProperty("oracle.net.kerberos5_cc_name", "FILE:/path/to/your/krb5cc_username");
```
**How to find your cache file**: Run `klist` command - it shows your cache file location.
**Example**:
- Linux/Mac: `"FILE:/tmp/krb5cc_1000"` or `"FILE:/tmp/krb5cc_username"`
- Windows: `"FILE:C:\\Users\\username\\krb5cc_username"`

#### 3. **JDBC Trace Log Path (Optional)**
```java
// Line ~38: Replace with your desired log file path
props.setProperty("oracle.jdbc.LogFile", "/path/to/your/jdbc_trace.log");
```

#### 4. **Database Connection Details**
```java
// Lines ~43-62: Replace with your actual database connection details
```

**You need to replace:**
- `your-oracle-scan.company.net` → Your actual Oracle SCAN hostname
- `your-oracle-scan2.company.net` → Your second Oracle SCAN hostname (if using RAC)
- `1521` → Your actual Oracle port (if different)
- `YOUR_DATABASE_SERVICE_NAME` → Your actual Oracle service name

**How to get these values**: Contact your DBA or check your existing database configuration files.

### Usage

1. **Place the Oracle JDBC driver in this directory:**
   ```
   test-connection/
   ├── TestJDBC.java
   ├── ojdbc11.jar        # Download this from Oracle
   └── README.md
   ```

2. **Compile the test:**
   ```bash
   javac -cp ".:ojdbc11.jar" TestJDBC.java
   ```

3. **Run the test:**
   ```bash
   java -cp ".:ojdbc11.jar" TestJDBC
   ```

### Expected Output

- **Success**: Connection established, simple query executed, Kerberos tickets displayed
- **Failure**: Error messages with troubleshooting information

### Note

This is a standalone test utility separate from the main MCP server. The main server uses Maven to automatically manage the Oracle JDBC driver dependency, so this manual process is only needed for isolated connection testing.

### ⚠️ **Before Running the Test**

**The test will NOT work until you replace all placeholder values in `TestJDBC.java` with your actual configuration details.**

### Troubleshooting

1. **"No suitable driver found"**: Oracle JDBC driver not in classpath
2. **"Unknown host"** or **"Connection refused"**: Placeholder hostnames not replaced with actual database hostnames
3. **"Invalid service name"**: Placeholder service name not replaced with actual Oracle service name
4. **"Kerberos authentication failed"**: 
   - Check Kerberos tickets with `klist`
   - Verify `krb5.conf` path is correct and file exists
   - Verify Kerberos cache file path is correct
5. **"File not found"** (krb5.conf or cache): Placeholder paths not replaced with actual file paths
6. **Connection timeout**: Verify network connectivity and database availability

### Finding Your Configuration Values

**To find your Kerberos cache file:**
```bash
klist
# Output shows: "Ticket cache: FILE:/tmp/krb5cc_1000"
# Use: "FILE:/tmp/krb5cc_1000"
```

**To find your krb5.conf file:**
```bash
# Common locations:
ls /etc/krb5.conf           # Linux/Mac system-wide
ls ~/.krb5/krb5.conf        # User-specific
ls $KRB5_CONFIG             # If environment variable is set
```

**For database connection details, contact your Database Administrator (DBA) for:**
- Oracle SCAN hostnames
- Port numbers (usually 1521)
- Service names for your target database environment