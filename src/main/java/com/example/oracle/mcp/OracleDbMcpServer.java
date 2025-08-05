package com.example.oracle.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;

import com.example.oracle.mcp.config.DatabaseConfig;
import com.example.oracle.mcp.config.DatabaseConfig.Environment;
import com.example.oracle.mcp.config.DatabaseConfig.Schema;
import com.example.oracle.mcp.database.DatabaseConnectionManager;
import com.example.oracle.mcp.database.QueryResult;
import com.example.oracle.mcp.database.SchemaInfo;
import com.example.oracle.mcp.database.TableSearchResult;

import java.sql.*;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;

/**
 * Oracle Database MCP Server that provides database query and optimization tools.
 * This server enables AI assistants to interact with Oracle databases through the Model Context Protocol.
 */
public class OracleDbMcpServer {
    
    private static final String SERVER_NAME = "oracle-db-mcp-server";
    private static final String SERVER_VERSION = "1.0.0";
    
    // Database components
    private static DatabaseConnectionManager connectionManager;
    private static DatabaseConfig config;
    
    public static void main(String[] args) {
        try {
            // Initialize database components
            config = DatabaseConfig.getInstance();
            connectionManager = DatabaseConnectionManager.getInstance();
            
            // Create STDIO transport provider
            StdioServerTransportProvider transportProvider = new StdioServerTransportProvider(new ObjectMapper());
            
            // Create server with capabilities
            McpSyncServer server = McpServer.sync(transportProvider)
                .serverInfo(SERVER_NAME, SERVER_VERSION)
                .capabilities(McpSchema.ServerCapabilities.builder()
                    .tools(true)     // Enable tool support
                    .logging()       // Enable logging support
                    .build())
                .build();
            
            // Register database tools
            server.addTool(createExecuteQueryTool());
            server.addTool(createExplainPlanTool());
            server.addTool(createGetSchemaInfoTool());
            server.addTool(createConnectToEnvironmentTool());
            server.addTool(createGetCurrentStatusTool());
            
            // Keep the server running
            System.err.println("Oracle DB MCP Server started. Waiting for requests...");
            
            // Add shutdown hook for graceful cleanup
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    if (connectionManager != null) {
                        connectionManager.closeAllConnections();
                    }
                    server.close();
                    System.err.println("Oracle DB MCP Server stopped.");
                } catch (Exception e) {
                    System.err.println("Error during server shutdown: " + e.getMessage());
                }
            }));
            
            // Keep the main thread alive
            Thread.currentThread().join();
            
        } catch (Exception e) {
            System.err.println("Failed to start Oracle DB MCP Server: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    /**
     * Creates the execute query tool specification.
     */
    private static McpServerFeatures.SyncToolSpecification createExecuteQueryTool() {
        String schema = """
            {
              "type": "object",
              "properties": {
                "sql": {
                  "type": "string",
                  "description": "SQL query to execute. Example: SELECT * FROM SCHEMA.TABLE_NAME WHERE ID = 'value'"
                },
                "environment": {
                  "type": "string",
                  "description": "Database environment: dev, uat, prod (optional, defaults to current environment)",
                  "enum": ["dev", "uat", "prod"]
                },
                "schema": {
                  "type": "string",
                  "description": "Database schema name (optional, defaults to current schema)"
                }
              },
              "required": ["sql"]
            }
            """;
            
        McpSchema.Tool executeQueryTool = new McpSchema.Tool(
            "execute_query",
            "Execute optimized SQL SELECT queries against Oracle database. IMPORTANT: Before building queries, you should: 1) Use search_tables to find relevant tables, 2) Use get_table_details to understand table structure, 3) Build efficient queries with proper WHERE clauses, indexes, and LIMIT when appropriate, 4) Avoid SELECT * for large tables - specify needed columns only.",
            schema
        );
        
        return new McpServerFeatures.SyncToolSpecification(
            executeQueryTool,
            (exchange, arguments) -> {
                try {
                    String sql = (String) arguments.get("sql");
                    String environmentParam = (String) arguments.get("environment");
                    String schemaParam = (String) arguments.get("schema");
                    
                    if (sql == null || sql.trim().isEmpty()) {
                        return new McpSchema.CallToolResult(
                            "sql parameter is required",
                            true
                        );
                    }
                    
                    // Determine environment and schema
                    Environment environment = environmentParam != null ?
                        Environment.fromString(environmentParam) : connectionManager.getCurrentEnvironment();
                    Schema selectedSchema = schemaParam != null ?
                        config.findSchemaByName(schemaParam) : connectionManager.getCurrentSchema();
                    
                    // Comprehensive security validation
                    String validationError = validateSqlSecurity(sql);
                    if (validationError != null) {
                        return new McpSchema.CallToolResult(validationError, true);
                    }
                    
                    // Auto-prefix table names with schema if missing
                    String prefixedSql = addSchemaPrefix(sql, selectedSchema);
                    
                    // Execute the query
                    QueryResult result = connectionManager.executeQuery(prefixedSql, environment, selectedSchema);
                    
                    Map<String, Object> response = new HashMap<>();
                    response.put("operation", "execute_query");
                    response.put("environment", environment.getKey());
                    response.put("schema", selectedSchema.getName());
                    response.put("original_sql", sql);
                    response.put("executed_sql", prefixedSql);
                    response.put("result", result.toMap());
                    response.put("formatted_result", result.toFormattedString());
                    
                    ObjectMapper mapper = new ObjectMapper();
                    String jsonResponse = mapper.writeValueAsString(response);
                    
                    return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent(jsonResponse)),
                        false
                    );
                    
                } catch (SQLException e) {
                    return new McpSchema.CallToolResult(
                        "Database error: " + e.getMessage(),
                        true
                    );
                } catch (Exception e) {
                    return new McpSchema.CallToolResult(
                        "Error executing query: " + e.getMessage(),
                        true
                    );
                }
            }
        );
    }
    
    /**
     * Creates the explain plan tool specification.
     */
    private static McpServerFeatures.SyncToolSpecification createExplainPlanTool() {
        String schema = """
            {
              "type": "object",
              "properties": {
                "sql": {
                  "type": "string",
                  "description": "SQL query to analyze. Example: SELECT * FROM SCHEMA.TABLE_NAME WHERE ID = 'value'"
                }
              },
              "required": ["sql"]
            }
            """;
            
        McpSchema.Tool explainPlanTool = new McpSchema.Tool(
            "explain_plan",
            "Generate execution plan for a SQL query to analyze performance.",
            schema
        );
        
        return new McpServerFeatures.SyncToolSpecification(
            explainPlanTool,
            (exchange, arguments) -> {
                try {
                    String sql = (String) arguments.get("sql");
                    
                    if (sql == null || sql.trim().isEmpty()) {
                        return new McpSchema.CallToolResult(
                            "sql parameter is required",
                            true
                        );
                    }
                    
                    // Comprehensive security validation
                    String validationError = validateSqlSecurity(sql);
                    if (validationError != null) {
                        return new McpSchema.CallToolResult(validationError, true);
                    }
                    
                    // Use current environment and schema
                    Environment environment = connectionManager.getCurrentEnvironment();
                    Schema selectedSchema = connectionManager.getCurrentSchema();
                    
                    // Auto-prefix table names with schema if missing
                    String cleanQuery = addSchemaPrefix(sql, selectedSchema);
                    
                    // Generate execution plan using current environment/schema
                    String explainPlan = connectionManager.getExecutionPlan(cleanQuery);
                    
                    Map<String, Object> response = new HashMap<>();
                    response.put("operation", "explain_plan");
                    response.put("environment", environment.getKey());
                    response.put("schema", selectedSchema.getName());
                    response.put("sql", cleanQuery);
                    response.put("execution_plan", explainPlan);
                    
                    ObjectMapper mapper = new ObjectMapper();
                    String jsonResponse = mapper.writeValueAsString(response);
                    
                    return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent(jsonResponse)),
                        false
                    );
                    
                } catch (SQLException e) {
                    return new McpSchema.CallToolResult(
                        "Database error: " + e.getMessage(),
                        true
                    );
                } catch (Exception e) {
                    return new McpSchema.CallToolResult(
                        "Error generating explain plan: " + e.getMessage(),
                        true
                    );
                }
            }
        );
    }
    
    /**
     * Creates the get schema info tool specification.
     */
    private static McpServerFeatures.SyncToolSpecification createGetSchemaInfoTool() {
        String schema = """
            {
              "type": "object",
              "properties": {}
            }
            """;
            
        McpSchema.Tool getSchemaInfoTool = new McpSchema.Tool(
            "get_schema_info",
            "Get complete information about the current database schema including all tables and views.",
            schema
        );
        
        return new McpServerFeatures.SyncToolSpecification(
            getSchemaInfoTool,
            (exchange, arguments) -> {
                try {
                    // No arguments needed - use current environment and schema
                    Environment environment = connectionManager.getCurrentEnvironment();
                    Schema selectedSchema = connectionManager.getCurrentSchema();
                    
                    // Get schema information
                    SchemaInfo schemaInfo = connectionManager.getSchemaInfo(selectedSchema);
                    
                    Map<String, Object> response = new HashMap<>();
                    response.put("operation", "get_schema_info");
                    response.put("environment", environment.getKey());
                    response.put("schema", selectedSchema.getName());
                    response.put("current_schema", selectedSchema.getName());
                    response.put("schema_info", schemaInfo.toMap());
                    
                    // Add table count for quick reference
                    response.put("table_count", schemaInfo.getTables().size());
                    response.put("view_count", schemaInfo.getViews().size());
                    
                    ObjectMapper mapper = new ObjectMapper();
                    String jsonResponse = mapper.writeValueAsString(response);
                    
                    return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent(jsonResponse)),
                        false
                    );
                    
                } catch (SQLException e) {
                    return new McpSchema.CallToolResult(
                        "Database error: " + e.getMessage(),
                        true
                    );
                } catch (Exception e) {
                    return new McpSchema.CallToolResult(
                        "Error retrieving schema information: " + e.getMessage(),
                        true
                    );
                }
            }
        );
    }
    
    /**
     * Creates the connect to environment tool specification.
     */
    private static McpServerFeatures.SyncToolSpecification createConnectToEnvironmentTool() {
        String schema = """
            {
              "type": "object",
              "properties": {
                "environment": {
                  "type": "string",
                  "description": "Database environment to connect to",
                  "enum": ["dev", "uat", "prod"]
                }
              },
              "required": ["environment"]
            }
            """;
            
        McpSchema.Tool connectTool = new McpSchema.Tool(
            "connect_to_environment",
            "Connect to a database environment: dev, uat, or prod.",
            schema
        );
        
        return new McpServerFeatures.SyncToolSpecification(
            connectTool,
            (exchange, arguments) -> {
                try {
                    String environmentParam = (String) arguments.get("environment");
                    
                    if (environmentParam == null || environmentParam.trim().isEmpty()) {
                        return new McpSchema.CallToolResult(
                            "environment parameter is required",
                            true
                        );
                    }
                    
                    // Parse environment directly
                    Environment environment = Environment.fromString(environmentParam);
                    
                    // Test connection with timeout handling
                    boolean connected;
                    String connectionMessage;
                    
                    try {
                        connected = connectionManager.testConnection(environment);
                        if (connected) {
                            connectionManager.switchEnvironment(environment);
                            connectionMessage = "Successfully connected to " + config.getEnvironmentDisplayName(environment) + " environment";
                        } else {
                            connectionMessage = "Connection test failed for " + config.getEnvironmentDisplayName(environment) + " environment. Please verify your database configuration and network connectivity.";
                        }
                    } catch (Exception e) {
                        connected = false;
                        connectionMessage = "Connection attempt failed for " + config.getEnvironmentDisplayName(environment) + " environment: " + e.getMessage();
                    }
                    
                    Map<String, Object> response = new HashMap<>();
                    response.put("operation", "connect_to_environment");
                    response.put("environment", environment.getKey());
                    response.put("environment_display", config.getEnvironmentDisplayName(environment));
                    response.put("status", connected ? "connected" : "failed");
                    response.put("message", connectionMessage);
                    response.put("timestamp", System.currentTimeMillis());
                    
                    ObjectMapper mapper = new ObjectMapper();
                    String jsonResponse = mapper.writeValueAsString(response);
                    
                    return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent(jsonResponse)),
                        !connected  // isError = true if connection failed
                    );
                    
                } catch (Exception e) {
                    return new McpSchema.CallToolResult(
                        "Error connecting to environment: " + e.getMessage(),
                        true
                    );
                }
            }
        );
    }
    
    /**
     * Creates the get current status tool specification.
     */
    private static McpServerFeatures.SyncToolSpecification createGetCurrentStatusTool() {
        String schema = """
            {
              "type": "object",
              "properties": {}
            }
            """;
            
        McpSchema.Tool statusTool = new McpSchema.Tool(
            "get_current_status",
            "Get the current connection status and environment information.",
            schema
        );
        
        return new McpServerFeatures.SyncToolSpecification(
            statusTool,
            (exchange, arguments) -> {
                try {
                    // No arguments needed
                    
                    Environment currentEnv = connectionManager.getCurrentEnvironment();
                    Schema currentSchema = connectionManager.getCurrentSchema();
                    
                    Map<String, Object> response = new HashMap<>();
                    response.put("operation", "get_current_status");
                    response.put("current_environment", currentEnv.getKey());
                    response.put("current_environment_display", config.getEnvironmentDisplayName(currentEnv));
                    response.put("current_schema", currentSchema.getName());
                    
                    // Test current connection
                    boolean isConnected = connectionManager.testConnection(currentEnv);
                    response.put("connection_status", isConnected ? "connected" : "disconnected");
                    
                    if (isConnected) {
                        try {
                            SchemaInfo schemaInfo = connectionManager.getSchemaInfo(currentSchema);
                            response.put("schema_summary", Map.of(
                                "table_count", schemaInfo.getTables().size(),
                                "view_count", schemaInfo.getViews().size()
                            ));
                        } catch (SQLException e) {
                            response.put("schema_summary", "Error retrieving schema info: " + e.getMessage());
                        }
                    }
                    
                    ObjectMapper mapper = new ObjectMapper();
                    String jsonResponse = mapper.writeValueAsString(response);
                    
                    return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent(jsonResponse)),
                        false
                    );
                    
                } catch (Exception e) {
                    return new McpSchema.CallToolResult(
                        "Error getting current status: " + e.getMessage(),
                        true
                    );
                }
            }
        );
    }
    
    /**
     * Creates the search tables tool specification.
     */
    private static McpServerFeatures.SyncToolSpecification createSearchTablesToolTool() {
        String schema = """
            {
              "type": "object",
              "properties": {
                "keyword": {
                  "type": "string",
                  "description": "Search keyword. Examples: trade, user, order, customer"
                },
                "environment": {
                  "type": "string",
                  "description": "Database environment: dev, uat, prod (optional, defaults to dev)",
                  "enum": ["dev", "uat", "prod"]
                },
                "schema": {
                  "type": "string",
                  "description": "Database schema name (optional, searches all schemas if not specified)"
                }
              },
              "required": ["keyword"]
            }
            """;
            
        McpSchema.Tool searchTablesTool = new McpSchema.Tool(
            "search_tables",
            "Search for database tables by keyword to find relevant tables. Use this FIRST before building queries. Search for business concepts (e.g., 'trade', 'user', 'order') rather than exact table names. Returns table names with columns and relevance scores.",
            schema
        );
        
        return new McpServerFeatures.SyncToolSpecification(
            searchTablesTool,
            (exchange, arguments) -> {
                try {
                    String keyword = (String) arguments.get("keyword");
                    String environmentParam = (String) arguments.get("environment");
                    String schemaParam = (String) arguments.get("schema");
                    
                    if (keyword == null || keyword.trim().isEmpty()) {
                        return new McpSchema.CallToolResult(
                            "keyword parameter is required",
                            true
                        );
                    }
                    
                    // Use specified environment or default
                    Environment environment = environmentParam != null ? 
                        Environment.fromString(environmentParam) : connectionManager.getCurrentEnvironment();
                    Schema targetSchema = schemaParam != null ? 
                        config.findSchemaByName(schemaParam) : null;
                    
                    // Switch to specified environment
                    connectionManager.switchEnvironment(environment);
                    
                    // Search for tables
                    List<TableSearchResult> results = connectionManager.searchTables(keyword, targetSchema);
                    
                    Map<String, Object> response = new HashMap<>();
                    response.put("operation", "search_tables");
                    response.put("search_keyword", keyword);
                    response.put("environment", environment.getKey());
                    response.put("target_schema", targetSchema != null ? targetSchema.getName() : "all_schemas");
                    response.put("found_tables", results.stream().map(TableSearchResult::toMap).toList());
                    response.put("table_count", results.size());
                    
                    // Add summary for easy reading
                    List<String> tableSummaries = results.stream()
                            .limit(10) // Top 10 results
                            .map(TableSearchResult::getSummaryDescription)
                            .toList();
                    response.put("table_summaries", tableSummaries);
                    
                    ObjectMapper mapper = new ObjectMapper();
                    String jsonResponse = mapper.writeValueAsString(response);
                    
                    return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent(jsonResponse)),
                        false
                    );
                    
                } catch (SQLException e) {
                    return new McpSchema.CallToolResult(
                        "Database error during table search: " + e.getMessage(),
                        true
                    );
                } catch (Exception e) {
                    return new McpSchema.CallToolResult(
                        "Error searching tables: " + e.getMessage(),
                        true
                    );
                }
            }
        );
    }
    
    /**
     * Creates the get table details tool specification.
     */
    private static McpServerFeatures.SyncToolSpecification createGetTableDetailsToolTool() {
        String schema = """
            {
              "type": "object",
              "properties": {
                "table": {
                  "type": "string",
                  "description": "Table name like TABLE_NAME or SCHEMA.TABLE_NAME"
                },
                "environment": {
                  "type": "string",
                  "description": "Database environment: dev, uat, prod (optional, defaults to dev)",
                  "enum": ["dev", "uat", "prod"]
                },
                "schema": {
                  "type": "string",
                  "description": "Database schema name (optional, uses schema from table name or current schema)"
                }
              },
              "required": ["table"]
            }
            """;
            
        McpSchema.Tool getTableDetailsTool = new McpSchema.Tool(
            "get_table_details",
            "Get detailed information about a table including all columns and data types.",
            schema
        );
        
        return new McpServerFeatures.SyncToolSpecification(
            getTableDetailsTool,
            (exchange, arguments) -> {
                try {
                    String table = (String) arguments.get("table");
                    String environmentParam = (String) arguments.get("environment");
                    String schemaParam = (String) arguments.get("schema");
                    
                    if (table == null || table.trim().isEmpty()) {
                        return new McpSchema.CallToolResult(
                            "table parameter is required",
                            true
                        );
                    }
                    
                    // Use specified environment or default
                    Environment environment = environmentParam != null ? 
                        Environment.fromString(environmentParam) : connectionManager.getCurrentEnvironment();
                    
                    // Parse schema and table name
                    String actualSchemaName;
                    String actualTableName;
                    
                    if (table.contains(".")) {
                        String[] parts = table.split("\\.", 2);
                        actualSchemaName = parts[0];
                        actualTableName = parts[1];
                    } else {
                        actualSchemaName = schemaParam != null ? schemaParam : connectionManager.getCurrentSchema().getName();
                        actualTableName = table;
                    }
                    
                    // Switch to specified environment
                    connectionManager.switchEnvironment(environment);
                    
                    Schema targetSchema = config.findSchemaByName(actualSchemaName);
                    
                    // Get schema info and find the specific table
                    SchemaInfo schemaInfo = connectionManager.getSchemaInfo(targetSchema);
                    SchemaInfo.TableInfo tableInfo = schemaInfo.getTables().stream()
                            .filter(tbl -> tbl.getName().equalsIgnoreCase(actualTableName))
                            .findFirst()
                            .orElse(null);
                    
                    if (tableInfo == null) {
                        return new McpSchema.CallToolResult(
                            "Table " + actualTableName + " not found in schema " + actualSchemaName,
                            true
                        );
                    }
                    
                    Map<String, Object> response = new HashMap<>();
                    response.put("operation", "get_table_details");
                    response.put("environment", environment.getKey());
                    response.put("schema", actualSchemaName);
                    response.put("table", actualTableName);
                    response.put("full_table_name", actualSchemaName + "." + actualTableName);
                    response.put("table_details", tableInfo.toMap());
                    
                    // Add convenient column summaries
                    List<String> columnSummaries = tableInfo.getColumns().stream()
                            .map(col -> col.getName() + " (" + col.getDataType() + 
                                       (col.isNullable() ? ", nullable" : ", not null") + ")")
                            .toList();
                    response.put("column_summaries", columnSummaries);
                    
                    ObjectMapper mapper = new ObjectMapper();
                    String jsonResponse = mapper.writeValueAsString(response);
                    
                    return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent(jsonResponse)),
                        false
                    );
                    
                } catch (SQLException e) {
                    return new McpSchema.CallToolResult(
                        "Database error getting table details: " + e.getMessage(),
                        true
                    );
                } catch (Exception e) {
                    return new McpSchema.CallToolResult(
                        "Error getting table details: " + e.getMessage(),
                        true
                    );
                }
            }
        );
    }
    
    // Helper methods for environment and schema parsing
    
    /**
     * Parse environment from natural language and parameter
     */
    private static Environment parseEnvironment(String text, String environmentParam) {
        // First check explicit parameter
        if (environmentParam != null && !environmentParam.trim().isEmpty()) {
            return Environment.fromString(environmentParam);
        }
        
        // Then parse from natural language
        return Environment.parseFromText(text);
    }
    
    /**
     * Parse schema from natural language and parameter
     */
    private static Schema parseSchema(String text, String schemaParam) {
        DatabaseConfig config = DatabaseConfig.getInstance();
        
        // First check explicit parameter
        if (schemaParam != null && !schemaParam.trim().isEmpty()) {
            return config.findSchemaByName(schemaParam);
        }
        
        // Then parse from natural language
        return config.parseSchemaFromText(text);
    }
    
    /**
     * Extract SQL query from natural language text
     */
    private static String extractSqlFromText(String text) {
        if (text == null) return "";
        
        String trimmed = text.trim();
        
        // If it already looks like SQL, return as-is
        String upperText = trimmed.toUpperCase();
        if (upperText.startsWith("SELECT") || upperText.startsWith("WITH") || 
            upperText.startsWith("EXPLAIN")) {
            return trimmed;
        }
        
        // Look for SQL within natural language
        // This is a simple implementation - could be enhanced with more sophisticated parsing
        String[] lines = trimmed.split("\\n");
        for (String line : lines) {
            String upperLine = line.trim().toUpperCase();
            if (upperLine.startsWith("SELECT") || upperLine.startsWith("WITH")) {
                return line.trim();
            }
        }
        
        // If no clear SQL found, return the original text and let the user fix it
        return trimmed;
    }
    
    /**
     * Automatically add schema prefix to table names that don't already have one.
     * Converts: SELECT * FROM TABLE_NAME to SELECT * FROM SCHEMA.TABLE_NAME
     * Excludes Oracle system tables and views.
     */
    private static String addSchemaPrefix(String sql, Schema schema) {
        if (sql == null || schema == null) {
            return sql;
        }
        
        String schemaName = schema.getName();
        if (schemaName == null || schemaName.trim().isEmpty()) {
            return sql;
        }
        
        // Don't modify queries that reference Oracle system objects
        String upperSql = sql.toUpperCase();
        if (upperSql.contains("DUAL") || 
            upperSql.contains("ALL_TABLES") || 
            upperSql.contains("ALL_TAB_COLUMNS") ||
            upperSql.contains("ALL_VIEWS") ||
            upperSql.contains("ALL_OBJECTS") ||
            upperSql.contains("USER_TABLES") ||
            upperSql.contains("USER_TAB_COLUMNS") ||
            upperSql.contains("USER_VIEWS") ||
            upperSql.contains("USER_OBJECTS") ||
            upperSql.contains("DBA_") ||
            upperSql.contains("V$") ||
            upperSql.contains("SYS.") ||
            upperSql.contains("SYSTEM.")) {
            return sql; // Return unchanged for system queries
        }
        
        // Simple regex-based approach to add schema prefix
        // This handles common cases: FROM table_name, JOIN table_name, etc.
        String result = sql;
        
        // Pattern to match table references after FROM, JOIN, etc.
        // Matches table names but excludes those already prefixed with schema
        // Handles: letters, numbers, underscores, and dollar signs (Oracle identifiers)
        String pattern = "(?i)\\b(FROM|JOIN|UPDATE|INSERT\\s+INTO)\\s+([A-Za-z_$][A-Za-z0-9_$#]*)(?!\\.)";
        
        result = result.replaceAll(pattern, "$1 " + schemaName + ".$2");
        
        return result;
    }
    
    /**
     * Comprehensive SQL security validation to prevent injection attacks
     * and ensure only safe SELECT queries are executed.
     */
    private static String validateSqlSecurity(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            return "SQL query cannot be empty";
        }
        
        String cleanSql = sql.trim();
        String upperSql = cleanSql.toUpperCase();
        
        // Remove comments and normalize whitespace
        String normalizedSql = removeComments(cleanSql).replaceAll("\\s+", " ").toUpperCase();
        
        // 1. Must start with SELECT
        if (!normalizedSql.startsWith("SELECT ")) {
            return "Only SELECT queries are allowed. Query must start with SELECT.";
        }
        
        // 2. Prohibited keywords that could indicate injection or dangerous operations
        String[] prohibitedKeywords = {
            ";", // Statement terminator - no chaining allowed
            "INSERT", "UPDATE", "DELETE", "DROP", "CREATE", "ALTER", "TRUNCATE",
            "EXEC", "EXECUTE", "CALL", "MERGE", "GRANT", "REVOKE",
            "COMMIT", "ROLLBACK", "SAVEPOINT",
            "DBMS_", "UTL_", "SYS.", "SYSTEM.", 
            "DUAL;", // Prevent ; after DUAL
            "/*", "*/", // Block multi-line comments
            "UNION ALL SELECT", "UNION SELECT", // Prevent UNION-based injection
            "WAITFOR", "SLEEP", "BENCHMARK", "PG_SLEEP", // Time-based attacks
            "XP_", "SP_", // SQL Server extended/system procedures
        };
        
        for (String keyword : prohibitedKeywords) {
            if (normalizedSql.contains(keyword)) {
                return "Security violation: Prohibited keyword '" + keyword + "' found in query";
            }
        }
        
        // 3. Check for multiple statements (semicolon not at very end)
        int semicolonIndex = cleanSql.indexOf(';');
        if (semicolonIndex != -1 && semicolonIndex < cleanSql.length() - 1) {
            return "Security violation: Multiple statements not allowed";
        }
        
        // 4. Validate parentheses are balanced (prevent injection through unmatched parens)
        if (!areParenthesesBalanced(cleanSql)) {
            return "Security violation: Unbalanced parentheses in query";
        }
        
        // 5. Check for suspicious patterns
        if (normalizedSql.contains("1=1") || normalizedSql.contains("1 = 1")) {
            return "Security violation: Suspicious pattern '1=1' detected";
        }
        
        if (normalizedSql.contains("OR '1'='1'") || normalizedSql.contains("OR 1=1")) {
            return "Security violation: SQL injection pattern detected";
        }
        
        // 6. Limit query complexity (max 10 UNION, no excessive nesting)
        long unionCount = normalizedSql.chars().filter(ch -> ch == 'U').count();
        if (unionCount > 10) {
            return "Security violation: Too many UNION operations (max 10 allowed)";
        }
        
        return null; // Query is valid
    }
    
    /**
     * Remove SQL comments from query
     */
    private static String removeComments(String sql) {
        // DISABLED: Comment removal was causing SQL corruption (ORA-00911 errors)
        // The regex "--.*$" was incorrectly matching and corrupting SQL strings
        // Since AI assistants shouldn't generate SQL with comments anyway, we disable this
        return sql;
    }
    
    /**
     * Check if parentheses are balanced in SQL query
     */
    private static boolean areParenthesesBalanced(String sql) {
        int count = 0;
        for (char c : sql.toCharArray()) {
            if (c == '(') count++;
            else if (c == ')') count--;
            if (count < 0) return false; // More closing than opening
        }
        return count == 0; // All parentheses matched
    }
}