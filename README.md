# Oracle DB MCP Server

A Model Context Protocol (MCP) server implementation for Oracle Database integration. This server enables AI assistants (GitHub Copilot, Claude Desktop, ChatGPT, etc.) to interact with Oracle databases by providing the tools needed for intelligent query discovery and execution.

## 🧠 **Perfect Architecture for AI Assistants**

This server follows the **correct MCP architecture** where:

- **🤖 AI Assistant provides the intelligence**: Natural language understanding, query building, and decision making
- **🔧 Our server provides the tools**: Database discovery, schema exploration, and query execution
- **⚡ Perfect collaboration**: AI Assistant discovers tables → builds SQL → executes via our server

### **Example Workflow with GitHub Copilot**
1. **User asks Copilot**: *"Can you please get me the status of Trade ID = 'abc123'?"*
2. **Copilot uses our `search_tables` tool**: Searches for "trade" and discovers `AFX_TRADE` table
3. **Copilot uses our `get_table_details` tool**: Gets column details like `TRADE_ID`, `TRADE_STATUS`
4. **Copilot builds SQL**: `SELECT TRADE_ID, TRADE_STATUS FROM AFX.AFX_TRADE WHERE TRADE_ID = 'abc123'`
5. **Copilot uses our `execute_query` tool**: Executes the SQL and gets results

## Features

- **🔍 Smart Table Discovery**: Search tables by keywords (trade, user, order, etc.)
- **📋 Detailed Schema Information**: Get complete table and column details for query building
- **🌍 Multi-Environment Support**: Connect to Development, UAT, and Production environments  
- **🔗 Dynamic Schema Support**: Configurable schemas (no hardcoded values)
- **⚡ Simple Query Execution**: Execute SQL built by AI assistants
- **📊 Query Optimization**: Generate and analyze execution plans (EXPLAIN PLAN) 
- **🔄 Connection Management**: Intelligent connection pooling and environment switching
- **📈 Status Monitoring**: Real-time connection status and environment information
- **🔐 Kerberos Authentication**: Enterprise-grade security with automatic credential management
- **🛡️ Security First**: Only allows SELECT statements for safe database interaction
- **⚙️ Externalized Configuration**: Easy configuration management through properties files
- **📋 MCP Protocol Compliance**: Full adherence to Model Context Protocol specifications

## Prerequisites

- Java 17 or higher
- Maven 3.6 or higher
- Oracle Database access with appropriate credentials
- For Kerberos authentication: Valid Kerberos tickets and proper krb5.conf configuration

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
        │   ├── OracleDbMcpServer.java        # Main MCP server (7 tools)
        │   ├── config/
        │   │   └── DatabaseConfig.java       # Dynamic configuration management
        │   └── database/
        │       ├── DatabaseConnectionManager.java  # Connection pooling & table search
        │       ├── TableSearchResult.java    # Table discovery results
        │       ├── QueryResult.java          # Query result formatting
        │       └── SchemaInfo.java           # Schema metadata
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

### 1. 🔍 Search Tables Tool
- **Name**: `search_tables`
- **Description**: Search for database tables that match a pattern
- **Purpose**: **For AI assistants to discover relevant tables**
- **Examples**:
  - `search_pattern: "trade"` → Finds `AFX_TRADE`, `TRADE_HISTORY` tables
  - `search_pattern: "user"` → Finds `AFX_USERS`, `USER_PROFILES` tables
  - `search_pattern: "order"` → Finds `AFX_ORDERS`, `ORDER_DETAILS` tables
- **Parameters**:
  - `search_pattern` (string, required): Keyword to search for
  - `environment` (string, optional): dev, uat, or prod
  - `schema` (string, optional): Specific schema to search

### 2. 📋 Get Table Details Tool
- **Name**: `get_table_details`
- **Description**: Get detailed information about a specific table
- **Purpose**: **For AI assistants to understand table structure before building SQL**
- **Returns**: Complete column information, data types, nullable constraints
- **Parameters**:
  - `table_name` (string, required): Table name or SCHEMA.TABLE_NAME
  - `environment` (string, optional): Target environment
  - `schema` (string, optional): Schema name if not in table_name

### 3. ⚡ Execute Query Tool
- **Name**: `execute_query`
- **Description**: Execute a SQL SELECT query built by AI assistants
- **Purpose**: **Run the SQL that AI assistants build**
- **Parameters**:
  - `sql` (string, required): The SELECT statement to execute
  - `environment` (string, optional): Target environment
  - `schema` (string, optional): Target schema

### 4. Explain Plan Tool
- **Name**: `explain_plan`
- **Description**: Generate execution plans for query optimization
- **Parameters**:
  - `query` (string, required): The SQL query to analyze
  - `environment` (string, optional): Target environment
  - `schema` (string, optional): Target schema

### 5. Get Schema Info Tool  
- **Name**: `get_schema_info`
- **Description**: Retrieve comprehensive database schema information
- **Parameters**:
  - `request` (string, required): Natural language request
  - `environment` (string, optional): Target environment
  - `schema` (string, optional): Target schema
  - `table_pattern` (string, optional): Filter pattern for tables

### 6. Connect to Environment Tool
- **Name**: `connect_to_environment`
- **Description**: Connect to specific database environments
- **Parameters**:
  - `request` (string, required): Natural language connection request
  - `environment` (string, optional): Explicit environment

### 7. Get Current Status Tool
- **Name**: `get_current_status`
- **Description**: Get current connection status and environment information
- **Parameters**:
  - `include_schema_info` (boolean, optional): Include schema summary

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

1. **🔍 Discover tables**:
   ```json
   {
     "tool": "search_tables",
     "arguments": {
       "search_pattern": "trade",
       "environment": "prod"
     }
   }
   ```
   **Result**: Finds `AFX_TRADE` table with high relevance score

2. **📋 Get table structure**:
   ```json
   {
     "tool": "get_table_details", 
     "arguments": {
       "table_name": "AFX.AFX_TRADE",
       "environment": "prod"
     }
   }
   ```
   **Result**: Discovers `TRADE_ID`, `TRADE_STATUS`, `TRADE_DATE` columns

3. **⚡ Execute query**:
   ```json
   {
     "tool": "execute_query",
     "arguments": {
       "sql": "SELECT TRADE_ID, TRADE_STATUS FROM AFX.AFX_TRADE WHERE TRADE_ID = 'abc123'",
       "environment": "prod"
     }
   }
   ```
   **Result**: Returns the trade status data

### **Example: User asks for user information**

**User**: *"Show me active users in development environment"*

**AI Assistant's process**:

1. **🔍 Search for user tables**:
   ```json
   {
     "tool": "search_tables",
     "arguments": {
       "search_pattern": "user",
       "environment": "dev"
     }
   }
   ```

2. **📋 Get user table details**:
   ```json
   {
     "tool": "get_table_details",
     "arguments": {
       "table_name": "AFX_USERS",
       "environment": "dev"
     }
   }
   ```

3. **⚡ Execute user query**:
   ```json
   {
     "tool": "execute_query", 
     "arguments": {
       "sql": "SELECT USER_ID, USER_NAME, STATUS FROM AFX.AFX_USERS WHERE STATUS = 'ACTIVE'",
       "environment": "dev"
     }
   }
   ```

## 🎉 **Perfect MCP Architecture Benefits**

- **🧠 AI Assistant handles complexity**: Natural language understanding, query logic, business rules
- **🔧 Our server stays focused**: Database operations, connection management, security
- **🚀 Scalable**: AI Assistant can handle any query complexity without server changes
- **🔒 Secure**: Server only provides safe database access tools
- **🔄 Maintainable**: Clear separation of concerns

## Security Considerations

- Only SELECT statements are allowed to prevent accidental data modification
- Supports read-only database connections
- Implements proper SQL injection prevention
- Uses parameterized queries where applicable
- Supports enterprise authentication mechanisms

## Development Status

This project is feature-complete and ready for use with proper database configuration.

### Current Status:
- ✅ MCP protocol implementation
- ✅ Tool specifications and schemas
- ✅ Maven build configuration
- ✅ Oracle JDBC driver integration  
- ✅ Multi-environment connection management
- ✅ **🆕 Smart table discovery for AI assistants**
- ✅ **🆕 Detailed table/column information**
- ✅ **🆕 Clean separation: AI Assistant = intelligence, Server = tools**
- ✅ **🆕 Dynamic schema configuration (no hardcoded values)**
- ✅ Query execution with full result formatting
- ✅ EXPLAIN PLAN functionality
- ✅ Comprehensive schema information retrieval
- ✅ Connection pooling and status monitoring
- ✅ Kerberos authentication integration
- ✅ Externalized configuration system

### 🎉 Usage Examples (GitHub Copilot, Claude Desktop, etc.):

**Natural Language Requests** *(Copilot figures out the tables and SQL)*:
- **"Can you please get me the status of Trade ID = 'abc123'?"**
- **"Show me all active users in the development environment"**
- **"Find orders with amount greater than 1000"**
- **"Get customer details for customer_id 12345"**
- **"List all pending trades in production"**

**What AI Assistants Do Automatically**:
1. **Discovers relevant tables** using our search tools
2. **Understands table structure** using our detail tools  
3. **Builds appropriate SQL** using its AI capabilities
4. **Executes queries** using our execution tools
5. **Formats and explains results** for you

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

*This server provides the perfect foundation for AI assistants to intelligently interact with Oracle databases through proper tool-based architecture!* 🚀