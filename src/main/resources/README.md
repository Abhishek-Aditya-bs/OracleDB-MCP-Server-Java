# Database Configuration

This directory contains the database configuration files for the Oracle DB MCP Server.

## database.properties

This file contains all database connection configurations for different environments:

### Configuration Sections

1. **Kerberos Configuration** - Shared across all environments
   - `kerberos.debug`: Enable Kerberos debug logging
   - `kerberos.config.path`: Path to krb5.conf file
   - `kerberos.ccache.path`: Path to Kerberos credential cache

2. **Connection Settings** - Common settings
   - `connection.timeout`: Database connection timeout
   - `jdbc.trace.enabled`: Enable JDBC tracing for debugging
   - `jdbc.trace.logfile`: JDBC trace log file location

3. **Schema Configuration**
   - `schema.default`: Default schema (AFX)
   - `schema.secondary`: Secondary schema (AFX_WF)

4. **Environment-specific URLs**
   - `dev.url`: Development environment connection string
   - `uat.url`: UAT/Testing environment connection string  
   - `prod.url`: Production environment connection string

### ⚠️ **CRITICAL: Replace Placeholder Values**

**This configuration file contains placeholder values that MUST be replaced before use:**

1. **Update your Kerberos paths:**
   ```properties
   # REPLACE THESE PLACEHOLDER PATHS:
   kerberos.config.path=/path/to/your/krb5.conf          # ← Your actual krb5.conf location
   kerberos.ccache.path=FILE:/path/to/your/krb5cc_dummy  # ← Your actual credential cache
   ```

2. **Update ALL environment connection strings:**
   **Current values are PLACEHOLDERS and will not work:**
   
   - **Dev environment**: Replace `dev-oracle-scan.company.net` and `DEV_SERVICE_NAME`
   - **UAT environment**: Replace `uat-oracle-scan.company.net` and `UAT_SERVICE_NAME`
   - **Prod environment**: Replace `prod-oracle-scan.company.net` and `PROD_SERVICE_NAME`
   - **Schema names**: Replace `YOUR_DEFAULT_SCHEMA` and `YOUR_SECONDARY_SCHEMA`
   
   **Example of what needs to be changed:**
   ```properties
   # BEFORE (placeholder - won't work):
   (ADDRESS = (PROTOCOL = TCP)(HOST = prod-oracle-scan.company.net)(PORT = 1521))
   (SERVICE_NAME = PROD_SERVICE_NAME)
   
   # AFTER (actual values - will work):
   (ADDRESS = (PROTOCOL = TCP)(HOST = your-actual-oracle-host.domain.com)(PORT = 6335))
   (SERVICE_NAME = YOUR_ACTUAL_SERVICE_NAME)
   ```

3. **Update schema names:**
   - Replace `YOUR_DEFAULT_SCHEMA` with your actual primary schema name
   - Replace `YOUR_SECONDARY_SCHEMA` with your actual secondary schema name

**🚨 THE SERVER WILL FAIL TO CONNECT UNTIL ALL PLACEHOLDER VALUES ARE REPLACED WITH ACTUAL DATABASE CONNECTION DETAILS.**

### Security Notes

- Keep this file secure as it contains database connection information
- Consider using environment variables for sensitive paths in production
- The credential cache path contains your Kerberos tickets - protect it appropriately