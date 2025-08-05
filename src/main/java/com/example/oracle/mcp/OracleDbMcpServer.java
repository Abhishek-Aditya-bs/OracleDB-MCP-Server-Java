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


import java.sql.*;
import java.util.Map;
import java.util.HashMap;
import java.util.List;


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
                  "description": "SQL query to execute. Include schema prefixes as needed. Example: SELECT * FROM SCHEMA.TABLE_NAME WHERE ID = 'value'"
                }
              },
              "required": ["sql"]
            }
            """;
            
        McpSchema.Tool executeQueryTool = new McpSchema.Tool(
            "execute_query",
            "Execute SQL SELECT queries against the current Oracle database environment and schema. AI assistants should build complete SQL queries with schema prefixes (e.g., SCHEMA.TABLE_NAME) and pass them to this tool. Use search_tables and get_table_details first to understand the database structure.",
            schema
        );
        
        return new McpServerFeatures.SyncToolSpecification(
            executeQueryTool,
            (exchange, arguments) -> {
                try {
                    String sql = (String) arguments.get("sql");
                    
                    if (sql == null || sql.trim().isEmpty()) {
                        return new McpSchema.CallToolResult(
                            "sql parameter is required",
                            true
                        );
                    }
                    
                    // Use current environment and schema
                    Environment environment = connectionManager.getCurrentEnvironment();
                    Schema selectedSchema = connectionManager.getCurrentSchema();
                    
                    // Security check: only allow SELECT statements
                    String upperSql = sql.trim().toUpperCase();
                    if (!upperSql.startsWith("SELECT")) {
                        return new McpSchema.CallToolResult(
                            "Only SELECT queries are allowed for security reasons. Query must start with SELECT.",
                            true
                        );
                    }
                    
                    // Execute the query directly (no modifications)
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
                            // Get simple table and view counts using direct SQL
                            QueryResult tableCountResult = connectionManager.executeQuery(
                                "SELECT COUNT(*) as table_count FROM all_tables WHERE owner = '" + currentSchema.getName() + "'", 
                                currentEnv, currentSchema);
                            QueryResult viewCountResult = connectionManager.executeQuery(
                                "SELECT COUNT(*) as view_count FROM all_views WHERE owner = '" + currentSchema.getName() + "'", 
                                currentEnv, currentSchema);
                            
                            int tableCount = 0;
                            int viewCount = 0;
                            
                            if (!tableCountResult.getRows().isEmpty()) {
                                Object countObj = tableCountResult.getRows().get(0).get("TABLE_COUNT");
                                tableCount = countObj instanceof Number ? ((Number) countObj).intValue() : 0;
                            }
                            
                            if (!viewCountResult.getRows().isEmpty()) {
                                Object countObj = viewCountResult.getRows().get(0).get("VIEW_COUNT");
                                viewCount = countObj instanceof Number ? ((Number) countObj).intValue() : 0;
                            }
                            
                            response.put("schema_summary", Map.of(
                                "table_count", tableCount,
                                "view_count", viewCount
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
    

}