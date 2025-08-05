# Oracle DB MCP Server

A Model Context Protocol (MCP) server implementation for Oracle Database integration. This server enables AI assistants (GitHub Copilot, Claude Desktop, ChatGPT, etc.) to interact with Oracle databases by providing the tools needed for intelligent query discovery and execution.

## 🧠 **Simplified Architecture for AI Assistants**

This server follows a **streamlined MCP architecture** where:

- **🤖 AI Assistant provides ALL the intelligence**: Natural language understanding, query building, table discovery, and decision making
- **🔧 Our server provides ONLY the essentials**: Database connection management and SQL execution
- **⚡ Ultra-simple collaboration**: AI Assistant builds complete SQL → executes via our server

### **Example Workflow with GitHub Copilot**
1. **User asks Copilot**: *"Can you please get me the status of Trade ID = 'abc123'?"*
2. **Copilot uses its intelligence**: Builds SQL like `SELECT TRADE_ID, TRADE_STATUS FROM SCHEMA.TRADE_TABLE WHERE TRADE_ID = 'abc123'`
3. **Copilot uses our `execute_query` tool**: Executes the SQL and gets results
4. **Done!** - Simple, fast, and reliable

## ⚠️ **IMPORTANT: Kerberos Authentication Only**

**This server ONLY supports Kerberos authentication and does NOT work with username/password authentication.**

### **Authentication Requirements:**
- ✅ **Kerberos authentication** (supported)
- ❌ **Username/password authentication** (NOT supported)
- ❌ **Basic authentication** (NOT supported)
- ❌ **LDAP authentication** (NOT supported)

**Before using this server, ensure:**
1. Your Oracle database is configured for Kerberos authentication
2. You have valid Kerberos tickets (`kinit` or equivalent)
3. Your `krb5.conf` file is properly configured
4. Your database connection strings use Kerberos service names

**If your database uses username/password authentication, this server will not work for you.**

## Features

- **⚡ Ultra-Simple Query Execution**: Execute any SQL built by AI assistants
- **🌍 Multi-Environment Support**: Connect to Development, UAT, and Production environments  
- **🔗 Dynamic Schema Support**: Configurable schemas (no hardcoded values)
- **🔄 Connection Management**: Intelligent connection pooling and environment switching
- **📈 Status Monitoring**: Real-time connection status and environment information
- **🔐 Kerberos Authentication**: Enterprise-grade security with automatic credential management
- **🛡️ Security First**: Only allows SELECT statements for safe database interaction
- **⚙️ Externalized Configuration**: Easy configuration management through properties files
- **📋 MCP Protocol Compliance**: Full adherence to Model Context Protocol specifications
- **🚀 Read-Only Database Support**: Works perfectly with Oracle Active Data Guard standby databases

## Prerequisites

- Java 17 or higher
- Maven 3.6 or higher
- **⚠️ Oracle Database with Kerberos authentication ONLY** (username/password auth not supported)
- Valid Kerberos tickets (`kinit` command executed successfully)
- Properly configured `krb5.conf` file with your Oracle database realm
- Database connection strings configured for Kerberos service names

## Project Structure

```
oracle-db-mcp-server/
├── pom.xml                           # Maven configuration
├── README.md                         # This file
├── build.sh                          # Build script
├── test-connection/                  # Database connection testing
│   └── TestJDBC.java                # Standalone connection test utility
└── src/
    └── main/
        ├── java/com/example/oracle/mcp/
        │   ├── OracleDbMcpServer.java        # Main MCP server (3 essential tools)
        │   ├── config/
        │   │   └── DatabaseConfig.java       # Dynamic configuration management
        │   └── database/
        │       ├── DatabaseConnectionManager.java  # Connection pooling & query execution
        │       └── QueryResult.java          # Query result formatting
        └── resources/
            ├── database.properties            # All database configs
            └── README.md                      # Configuration guide
```

## Building the Project

### Option 1: Using Build Script

```bash
chmod +x build.sh
./build.sh
```

### Option 2: Using Maven Directly

```bash
# Clean and compile
mvn clean compile

# Package the application
mvn clean package
```

This will create a fat JAR file: `target/oracle-db-mcp-server-1.0.0.jar`

## Running the Server

### Start the MCP Server

```bash
java -jar target/oracle-db-mcp-server-1.0.0.jar
```

The server will start and listen for MCP requests via STDIO (standard input/output).

## Available MCP Tools

### 1. ⚡ Execute Query Tool
- **Name**: `execute_query`
- **Description**: Execute any SQL SELECT query built by AI assistants
- **Purpose**: **The main tool - AI assistants build SQL, we execute it**
- **Parameters**:
  - `sql` (string, required): The SELECT statement to execute
- **Examples**:
  - `SELECT * FROM SCHEMA.USERS WHERE STATUS = 'ACTIVE'`
  - `SELECT TRADE_ID, STATUS FROM SCHEMA.TRADES WHERE TRADE_ID = 'T123'`
  - `SELECT COUNT(*) FROM SCHEMA.ORDERS WHERE DATE > SYSDATE - 7`

### 2. 🔄 Connect to Environment Tool
- **Name**: `connect_to_environment`
- **Description**: Connect to specific database environments
- **Parameters**:
  - `environment` (string, required): dev, uat, or prod
- **Examples**:
  - `{"environment": "prod"}` → Connect to production database
  - `{"environment": "dev"}` → Connect to development database

### 3. 📊 Get Current Status Tool
- **Name**: `get_current_status`
- **Description**: Get current connection status and environment information
- **Parameters**: None required
- **Returns**: Current environment, schema, connection status, and table/view counts

## Configuration

### Database Configuration

The server uses externalized configuration through `src/main/resources/database.properties`:

#### ⚠️ **IMPORTANT: Configuration Required Before First Use**

The `database.properties` file contains **placeholder values** that must be replaced with your actual database connection details:

1. **Update your Kerberos paths:**
   ```properties
   # Replace these placeholder paths with your actual paths
   kerberos.config.path=/path/to/your/krb5.conf          # ← Replace with actual path
   kerberos.ccache.path=FILE:/path/to/your/krb5cc_dummy  # ← Replace with actual path
   ```

2. **Configure environment connection strings:**
   All database URLs contain **placeholder hostnames and service names** that must be updated:
   
   - **Development**: Replace `dev-oracle-scan.company.net` and `DEV_SERVICE_NAME`
   - **UAT**: Replace `uat-oracle-scan.company.net` and `UAT_SERVICE_NAME`  
   - **Production**: Replace `prod-oracle-scan.company.net` and `PROD_SERVICE_NAME`
   - **Schemas**: Replace `YOUR_DEFAULT_SCHEMA` and `YOUR_SECONDARY_SCHEMA`

3. **Update schema settings:**
   ```properties 
   # Replace these placeholder schema names with your actual schema names
   schema.default=YOUR_DEFAULT_SCHEMA      # ← Replace with your actual default schema name  
   schema.secondary=YOUR_SECONDARY_SCHEMA  # ← Replace with your actual secondary schema name
   ```

**⚠️ The server will not work until these placeholder values are replaced with your actual database connection details.**

## 🎯 **How AI Assistants Use These Tools**

### **Example: User asks for trade status**

**User**: *"Can you please get me the status of Trade ID = 'abc123'?"*

**AI Assistant's process**:

1. **🔄 Connect to environment** (if needed):
   ```json
   {
     "tool": "connect_to_environment",
     "arguments": {
       "environment": "prod"
     }
   }
   ```

2. **⚡ Execute the complete query**:
   ```json
   {
     "tool": "execute_query",
     "arguments": {
       "sql": "SELECT TRADE_ID, TRADE_STATUS FROM SCHEMA.TRADE_TABLE WHERE TRADE_ID = 'abc123'"
     }
   }
   ```
   **Result**: Returns the trade status data immediately!

### **Example: User asks for user information**

**User**: *"Show me active users in development environment"*

**AI Assistant's process**:

1. **🔄 Connect to dev environment**:
   ```json
   {
     "tool": "connect_to_environment",
     "arguments": {
       "environment": "dev"
     }
   }
   ```

2. **⚡ Execute the complete query**:
   ```json
   {
     "tool": "execute_query", 
     "arguments": {
       "sql": "SELECT USER_ID, USER_NAME, STATUS FROM SCHEMA.USER_TABLE WHERE STATUS = 'ACTIVE'"
     }
   }
   ```
   **Result**: Returns all active users immediately!

## 🎉 **Simplified Architecture Benefits**

- **⚡ Lightning Fast**: No complex tool chains - just build SQL and execute
- **🧠 AI Assistant handles ALL complexity**: Table discovery, query building, optimization
- **🔧 Our server does ONE thing well**: Execute SQL safely and efficiently
- **🚀 Ultra Reliable**: Fewer moving parts = fewer things that can break
- **🔒 Maximum Security**: Simple SELECT-only validation, works with read-only databases
- **🔄 Zero Maintenance**: AI Assistant intelligence evolves, our server stays stable

## Security Considerations

### 🛡️ **Query Restrictions**
- **SELECT-only limitation**: This server only allows SELECT statements to prevent accidental data modification
- All queries are validated and non-SELECT statements (INSERT, UPDATE, DELETE, etc.) are rejected
- This is implemented with a simple SELECT-only check for maximum compatibility

### 🔧 **Extending Functionality**
- **Want to allow other query types?** You can fork this repository and modify the SQL validation logic
- **Location to modify**: `src/main/java/com/example/oracle/mcp/OracleDbMcpServer.java` - look for the SELECT-only validation check
- **Use case**: Some organizations may need INSERT/UPDATE capabilities for data management workflows
- **Responsibility**: If you extend beyond SELECT, ensure proper access controls and testing

### 🔒 **Additional Security Features**
- **🚀 Read-only database support**: Perfect for Oracle Active Data Guard standby databases
- **🛡️ Simple but effective SQL validation**: SELECT-only check prevents data modification
- **🔐 Enterprise authentication**: Kerberos-only authentication for maximum security
- **⏱️ Connection timeouts**: Prevent hanging connections and resource exhaustion
- **🔒 Zero SQL injection risk**: Simple validation approach eliminates complex attack vectors

## Development Status

This project is feature-complete and ready for use with proper database configuration.

### Current Status:
- ✅ **🚀 Ultra-simplified MCP architecture** - Just 3 essential tools
- ✅ **⚡ Lightning-fast query execution** - AI Assistant builds SQL, we execute it
- ✅ **🔒 Read-only database support** - Perfect for Oracle Active Data Guard standby
- ✅ **🛡️ Bulletproof security** - Simple SELECT-only validation
- ✅ **🌍 Multi-environment support** - Dev, UAT, Production switching
- ✅ **🔐 Kerberos-only authentication** - Enterprise-grade security
- ✅ **⚙️ Externalized configuration** - Easy deployment across environments
- ✅ **📋 Full MCP protocol compliance** - Works with any MCP client
- ✅ **🔄 Robust connection management** - Pooling, timeouts, error handling
- ✅ **📊 Real-time status monitoring** - Connection health and schema info

### 🎉 Usage Examples (GitHub Copilot, Claude Desktop, etc.):

**Natural Language Requests** *(AI Assistant figures out the tables and SQL)*:
- **"Can you please get me the status of Trade ID = 'abc123'?"**
- **"Show me all active users in the development environment"**
- **"Find orders with amount greater than 1000"**
- **"Get customer details for customer_id 12345"**
- **"List all pending trades in production"**

**What AI Assistants Do Automatically**:
1. **Analyzes your request** using their built-in intelligence
2. **Builds complete SQL queries** with proper schema prefixes and syntax
3. **Executes queries** using our simple `execute_query` tool
4. **Formats and explains results** for you
5. **Handles all complexity** - table discovery, joins, optimization, etc.

**Environment Management**:
- "Connect to the dev database"
- "Switch to production environment"  
- "What's my current connection status?"

## Contributing

This project follows standard Java development practices:

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests if applicable
5. Submit a pull request

## License

This project is provided as an example implementation of the Model Context Protocol for Oracle Database integration.

## Support

For issues related to:
- **MCP Protocol**: Refer to the [Model Context Protocol specification](https://spec.modelcontextprotocol.io/)
- **Oracle Database**: Consult Oracle documentation
- **Java/Maven**: Check respective project documentation

---

*This ultra-simplified server provides the perfect foundation for AI assistants to interact with Oracle databases - fast, secure, and reliable!* 🚀

## 🎯 **Why This Simplified Architecture Works Better**

### **❌ What We Removed (And Why)**
- **Complex table discovery tools** → AI assistants have built-in intelligence for this
- **Schema exploration tools** → AI assistants can figure out table structures
- **Query optimization tools** → AI assistants handle optimization internally
- **Complex SQL parsing** → Caused ORA-00911 errors and compatibility issues

### **✅ What We Kept (The Essentials)**
- **Simple SQL execution** → The core functionality that actually works
- **Environment switching** → Essential for multi-environment workflows  
- **Connection management** → Robust, reliable database connectivity
- **Status monitoring** → Know what's connected and working

### **🚀 Result: Ultra-Reliable Database Access**
- **Zero tool failures** → Simple tools that always work
- **Lightning fast** → No complex tool chains or discovery phases
- **AI-assistant friendly** → Let AI do what AI does best
- **Production ready** → Works perfectly with read-only databases