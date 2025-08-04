# Database Connection Testing

This directory contains utilities for testing Oracle database connectivity before integrating with the main MCP server.

## TestJDBC.java

A standalone Java utility to test Oracle database connections using Kerberos authentication.

### Prerequisites

1. **Oracle JDBC Driver**: Download `ojdbc11.jar` from Oracle's website
2. **Valid Kerberos Ticket**: Ensure you have a valid Kerberos ticket using `klist`
3. **Proper Configuration**: Ensure your `krb5.conf` is properly configured

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

### Troubleshooting

1. **"No suitable driver found"**: Oracle JDBC driver not in classpath
2. **"Kerberos authentication failed"**: Check Kerberos tickets with `klist`
3. **Connection timeout**: Verify network connectivity and database availability