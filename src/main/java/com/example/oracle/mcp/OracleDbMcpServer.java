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
                "query": {
                  "type": "string",
                  "description": "Natural language request or SQL query. Examples: 'get trade status for trade_id xyz', 'connect to dev DB and show me user data', 'SELECT * FROM table'. The system can intelligently build queries from natural language."
                },
                "environment": {
                  "type": "string",
                  "description": "Database environment: dev, uat, or prod (optional, can be inferred from query text)",
                  "enum": ["dev", "uat", "prod"]
                },
                "schema": {
                  "type": "string",
                  "description": "Database schema name (optional, can be inferred from query text or will use default schema)"
                },
                "use_intelligent_query": {
                  "type": "boolean",
                  "description": "Whether to use intelligent query building for natural language requests (default: true)"
                }
              },
              "required": ["query"]
            }
            """;
            
        McpSchema.Tool executeQueryTool = new McpSchema.Tool(
            "execute_query",
            "Execute queries against Oracle database with intelligent natural language processing. Can build SQL from requests like 'get trade status for trade_id xyz' or execute direct SQL. Supports multi-environment and multi-schema operations.",
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
                            "SQL parameter is required and cannot be empty",
                            true
                        );
                    }
                    
                    // Parse environment and schema
                    Environment environment = environmentParam != null ? 
                        Environment.fromString(environmentParam) : connectionManager.getCurrentEnvironment();
                    Schema selectedSchema = schemaParam != null ? 
                        config.findSchemaByName(schemaParam) : connectionManager.getCurrentSchema();
                    
                    // Security check: only allow SELECT statements
                    String upperSql = sql.trim().toUpperCase();
                    if (!upperSql.startsWith("SELECT")) {
                        return new McpSchema.CallToolResult(
                            "Only SELECT queries are allowed for security reasons. Query must start with SELECT.",
                            true
                        );
                    }
                    
                    // Execute the query
                    QueryResult result = connectionManager.executeQuery(sql, environment, selectedSchema);
                    
                    Map<String, Object> response = new HashMap<>();
                    response.put("operation", "execute_query");
                    response.put("environment", environment.getKey());
                    response.put("schema", selectedSchema.getName());
                    response.put("executed_sql", sql);
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
                "query": {
                  "type": "string",
                  "description": "The SQL query to analyze with EXPLAIN PLAN. You can specify environment and schema in natural language."
                },
                "environment": {
                  "type": "string",
                  "description": "Database environment: dev, uat, or prod (optional)",
                  "enum": ["dev", "uat", "prod"]
                },
                "schema": {
                  "type": "string",
                  "description": "Database schema: AFX (default) or AFX_WF (optional)",
                  "enum": ["AFX", "AFX_WF"]
                }
              },
              "required": ["query"]
            }
            """;
            
        McpSchema.Tool explainPlanTool = new McpSchema.Tool(
            "explain_plan",
            "Generate and analyze the execution plan for a SQL query to help with optimization. Supports environment and schema selection through natural language.",
            schema
        );
        
        return new McpServerFeatures.SyncToolSpecification(
            explainPlanTool,
            (exchange, arguments) -> {
                try {
                    String query = (String) arguments.get("query");
                    String environmentParam = (String) arguments.get("environment");
                    String schemaParam = (String) arguments.get("schema");
                    
                    if (query == null || query.trim().isEmpty()) {
                        return new McpSchema.CallToolResult(
                            "Query parameter is required and cannot be empty",
                            true
                        );
                    }
                    
                    // Parse environment and schema from natural language
                    Environment environment = parseEnvironment(query, environmentParam);
                    Schema selectedSchema = parseSchema(query, schemaParam);
                    
                    // Extract SQL from natural language if needed
                    String cleanQuery = extractSqlFromText(query);
                    
                    // Switch to the specified environment
                    connectionManager.switchEnvironment(environment);
                    connectionManager.switchSchema(selectedSchema);
                    
                    // Generate execution plan
                    String executionPlan = connectionManager.getExecutionPlan(cleanQuery);
                    
                    Map<String, Object> response = new HashMap<>();
                    response.put("operation", "explain_plan");
                    response.put("environment", environment.getKey());
                    response.put("schema", selectedSchema.getName());
                    response.put("query", cleanQuery);
                    response.put("execution_plan", executionPlan);
                    
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
              "properties": {
                "request": {
                  "type": "string",
                  "description": "Natural language request for schema information. Examples: 'show AFX tables', 'list AFX_WF views', 'describe schema in prod environment'"
                },
                "environment": {
                  "type": "string",
                  "description": "Database environment: dev, uat, or prod (optional)",
                  "enum": ["dev", "uat", "prod"]
                },
                "schema": {
                  "type": "string",
                  "description": "Database schema: AFX (default) or AFX_WF (optional)",
                  "enum": ["AFX", "AFX_WF"]
                },
                "object_type": {
                  "type": "string",
                  "description": "Type of database objects to list",
                  "enum": ["TABLE", "VIEW", "INDEX", "ALL"]
                },
                "table_pattern": {
                  "type": "string",
                  "description": "Pattern to filter table names (optional)"
                }
              },
              "required": ["request"]
            }
            """;
            
        McpSchema.Tool getSchemaInfoTool = new McpSchema.Tool(
            "get_schema_info",
            "Get information about database schema objects (tables, views, indexes, etc.). Supports natural language requests and can work across different environments and schemas.",
            schema
        );
        
        return new McpServerFeatures.SyncToolSpecification(
            getSchemaInfoTool,
            (exchange, arguments) -> {
                try {
                    String request = (String) arguments.get("request");
                    String environmentParam = (String) arguments.get("environment");
                    String schemaParam = (String) arguments.get("schema");
                    String objectType = (String) arguments.get("object_type");
                    String tablePattern = (String) arguments.get("table_pattern");
                    
                    if (request == null || request.trim().isEmpty()) {
                        return new McpSchema.CallToolResult(
                            "Request parameter is required and cannot be empty",
                            true
                        );
                    }
                    
                    // Parse environment and schema from natural language
                    Environment environment = parseEnvironment(request, environmentParam);
                    Schema selectedSchema = parseSchema(request, schemaParam);
                    
                    // Switch to the specified environment
                    connectionManager.switchEnvironment(environment);
                    
                    // Get schema information
                    SchemaInfo schemaInfo = connectionManager.getSchemaInfo(selectedSchema);
                    
                    Map<String, Object> response = new HashMap<>();
                    response.put("operation", "get_schema_info");
                    response.put("environment", environment.getKey());
                    response.put("schema", selectedSchema.getName());
                    response.put("request", request);
                    response.put("schema_info", schemaInfo.toMap());
                    
                    // Filter results if pattern provided
                    if (tablePattern != null && !tablePattern.trim().isEmpty()) {
                        List<SchemaInfo.TableInfo> filteredTables = schemaInfo.findTables(tablePattern);
                        response.put("filtered_tables", filteredTables.stream().map(SchemaInfo.TableInfo::toMap).toList());
                    }
                    
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
                "request": {
                  "type": "string",
                  "description": "Natural language request to connect to an environment. Examples: 'connect to dev', 'switch to production', 'use UAT database'"
                },
                "environment": {
                  "type": "string",
                  "description": "Specific environment to connect to (optional if specified in request)",
                  "enum": ["dev", "uat", "prod"]
                }
              },
              "required": ["request"]
            }
            """;
            
        McpSchema.Tool connectTool = new McpSchema.Tool(
            "connect_to_environment",
            "Connect to a specific database environment (dev, uat, prod). Supports natural language requests.",
            schema
        );
        
        return new McpServerFeatures.SyncToolSpecification(
            connectTool,
            (exchange, arguments) -> {
                try {
                    String request = (String) arguments.get("request");
                    String environmentParam = (String) arguments.get("environment");
                    
                    if (request == null || request.trim().isEmpty()) {
                        return new McpSchema.CallToolResult(
                            "Request parameter is required and cannot be empty",
                            true
                        );
                    }
                    
                    // Parse environment from natural language
                    Environment environment = parseEnvironment(request, environmentParam);
                    
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
              "properties": {
                "include_schema_info": {
                  "type": "boolean",
                  "description": "Whether to include current schema information (default: false)"
                }
              }
            }
            """;
            
        McpSchema.Tool statusTool = new McpSchema.Tool(
            "get_current_status",
            "Get the current connection status including environment, schema, and connection health.",
            schema
        );
        
        return new McpServerFeatures.SyncToolSpecification(
            statusTool,
            (exchange, arguments) -> {
                try {
                    Boolean includeSchemaInfo = (Boolean) arguments.get("include_schema_info");
                    if (includeSchemaInfo == null) includeSchemaInfo = false;
                    
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
                    
                    if (includeSchemaInfo && isConnected) {
                        try {
                            SchemaInfo schemaInfo = connectionManager.getSchemaInfo(currentSchema);
                            response.put("schema_summary", Map.of(
                                "table_count", schemaInfo.getMetadata().get("table_count"),
                                "view_count", schemaInfo.getMetadata().get("view_count")
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
                "search_pattern": {
                  "type": "string",
                  "description": "Search pattern to find relevant tables. Examples: 'trade', 'user', 'order', 'transaction'"
                },
                "environment": {
                  "type": "string",
                  "description": "Database environment: dev, uat, or prod (optional)",
                  "enum": ["dev", "uat", "prod"]
                },
                "schema": {
                  "type": "string",
                  "description": "Database schema name (optional, searches all schemas if not specified)"
                }
              },
              "required": ["search_pattern"]
            }
            """;
            
        McpSchema.Tool searchTablesTool = new McpSchema.Tool(
            "search_tables",
            "Search for database tables that match a pattern. Use this to discover which tables might contain the data you need before building SQL queries.",
            schema
        );
        
        return new McpServerFeatures.SyncToolSpecification(
            searchTablesTool,
            (exchange, arguments) -> {
                try {
                    String searchPattern = (String) arguments.get("search_pattern");
                    String environmentParam = (String) arguments.get("environment");
                    String schemaParam = (String) arguments.get("schema");
                    
                    if (searchPattern == null || searchPattern.trim().isEmpty()) {
                        return new McpSchema.CallToolResult(
                            "search_pattern parameter is required and cannot be empty",
                            true
                        );
                    }
                    
                    // Parse environment and schema
                    Environment environment = environmentParam != null ? 
                        Environment.fromString(environmentParam) : connectionManager.getCurrentEnvironment();
                    Schema targetSchema = schemaParam != null ? 
                        config.findSchemaByName(schemaParam) : null;
                    
                    // Switch to specified environment
                    connectionManager.switchEnvironment(environment);
                    
                    // Search for tables
                    List<TableSearchResult> results = connectionManager.searchTables(searchPattern, targetSchema);
                    
                    Map<String, Object> response = new HashMap<>();
                    response.put("operation", "search_tables");
                    response.put("search_pattern", searchPattern);
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
                "table_name": {
                  "type": "string",
                  "description": "Full table name in format SCHEMA.TABLE_NAME or just TABLE_NAME"
                },
                "environment": {
                  "type": "string",
                  "description": "Database environment: dev, uat, or prod (optional)",
                  "enum": ["dev", "uat", "prod"]
                },
                "schema": {
                  "type": "string",
                  "description": "Database schema name (optional if included in table_name)"
                }
              },
              "required": ["table_name"]
            }
            """;
            
        McpSchema.Tool getTableDetailsTool = new McpSchema.Tool(
            "get_table_details",
            "Get detailed information about a specific table including all columns, data types, and constraints. Use this after search_tables to get full column information for building SQL queries.",
            schema
        );
        
        return new McpServerFeatures.SyncToolSpecification(
            getTableDetailsTool,
            (exchange, arguments) -> {
                try {
                    String tableName = (String) arguments.get("table_name");
                    String environmentParam = (String) arguments.get("environment");
                    String schemaParam = (String) arguments.get("schema");
                    
                    if (tableName == null || tableName.trim().isEmpty()) {
                        return new McpSchema.CallToolResult(
                            "table_name parameter is required and cannot be empty",
                            true
                        );
                    }
                    
                    // Parse environment
                    Environment environment = environmentParam != null ? 
                        Environment.fromString(environmentParam) : connectionManager.getCurrentEnvironment();
                    
                    // Parse schema and table name
                    String actualSchemaName;
                    String actualTableName;
                    
                    if (tableName.contains(".")) {
                        String[] parts = tableName.split("\\\\.", 2);
                        actualSchemaName = parts[0];
                        actualTableName = parts[1];
                    } else {
                        actualSchemaName = schemaParam != null ? schemaParam : connectionManager.getCurrentSchema().getName();
                        actualTableName = tableName;
                    }
                    
                    Schema targetSchema = config.findSchemaByName(actualSchemaName);
                    
                    // Switch to specified environment
                    connectionManager.switchEnvironment(environment);
                    
                    // Get schema info and find the specific table
                    SchemaInfo schemaInfo = connectionManager.getSchemaInfo(targetSchema);
                    SchemaInfo.TableInfo tableInfo = schemaInfo.getTables().stream()
                            .filter(table -> table.getName().equalsIgnoreCase(actualTableName))
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
}