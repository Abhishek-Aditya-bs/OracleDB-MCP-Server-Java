package com.example.oracle.mcp.database;

import com.example.oracle.mcp.config.DatabaseConfig;
import com.example.oracle.mcp.config.DatabaseConfig.Environment;
import com.example.oracle.mcp.config.DatabaseConfig.Schema;

import java.sql.*;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

/**
 * Manages Oracle database connections across multiple environments.
 * Handles connection pooling, schema management, and Kerberos authentication.
 */
public class DatabaseConnectionManager {
    
    private static DatabaseConnectionManager instance;
    private final DatabaseConfig config;
    private final Map<Environment, Connection> connectionPool;
    private Environment currentEnvironment;
    private Schema currentSchema;
    
    private DatabaseConnectionManager() {
        this.config = DatabaseConfig.getInstance();
        this.connectionPool = new ConcurrentHashMap<>();
        this.currentEnvironment = Environment.DEV; // Default environment
        this.currentSchema = config.getDefaultSchema(); // Default schema
        
        // Set up Kerberos system properties
        setupKerberosProperties();
    }
    
    public static synchronized DatabaseConnectionManager getInstance() {
        if (instance == null) {
            instance = new DatabaseConnectionManager();
        }
        return instance;
    }
    
    private void setupKerberosProperties() {
        if (config.isKerberosDebugEnabled()) {
            System.setProperty("sun.security.krb5.debug", "true");
        }
        System.setProperty("java.security.krb5.conf", config.getKerberosConfigPath());
    }
    
    /**
     * Connect to a specific environment
     */
    public synchronized Connection connectToEnvironment(Environment environment) throws SQLException {
        // Check if we already have a valid connection for this environment
        Connection existingConnection = connectionPool.get(environment);
        if (existingConnection != null && !existingConnection.isClosed()) {
            this.currentEnvironment = environment;
            return existingConnection;
        }
        
        // Create new connection
        String url = config.getDatabaseUrl(environment);
        if (url == null) {
            throw new SQLException("No database URL configured for environment: " + environment);
        }
        
        Properties props = config.getConnectionProperties(environment);
        
        try {
            // Set connection timeout to prevent hanging
            DriverManager.setLoginTimeout(30); // 30 seconds timeout
            
            Connection connection = DriverManager.getConnection(url, props);
            
            // Test the connection immediately
            if (connection != null && !connection.isClosed()) {
                // Quick validation query with timeout
                try (Statement testStmt = connection.createStatement()) {
                    testStmt.setQueryTimeout(10); // 10 seconds for validation
                    testStmt.executeQuery("SELECT 1 FROM DUAL").close();
                }
                
                connectionPool.put(environment, connection);
                this.currentEnvironment = environment;
                
                return connection;
            } else {
                throw new SQLException("Connection is null or closed");
            }
        } catch (SQLException e) {
            throw new SQLException("Failed to connect to " + environment + " environment: " + e.getMessage(), e);
        }
    }
    
    /**
     * Get current active connection
     */
    public Connection getCurrentConnection() throws SQLException {
        Connection connection = connectionPool.get(currentEnvironment);
        if (connection == null || connection.isClosed()) {
            return connectToEnvironment(currentEnvironment);
        }
        return connection;
    }
    
    /**
     * Switch to a different environment
     */
    public void switchEnvironment(Environment environment) throws SQLException {
        connectToEnvironment(environment);
    }
    
    /**
     * Switch to a different schema
     */
    public void switchSchema(Schema schema) {
        this.currentSchema = schema;
    }
    
    /**
     * Execute a SELECT query with current environment and schema
     */
    public QueryResult executeQuery(String sql) throws SQLException {
        Connection connection = getCurrentConnection();
        
        // Modify query to include schema if not explicitly specified
        String finalSql = addSchemaToQuery(sql, currentSchema);
        
        try (Statement statement = connection.createStatement()) {
            // Set query timeout to prevent long-running queries (60 seconds)
            statement.setQueryTimeout(60);
            
            try (ResultSet resultSet = statement.executeQuery(finalSql)) {
                return QueryResult.fromResultSet(resultSet, currentEnvironment, currentSchema);
            }
        }
    }
    
    /**
     * Execute a query with specific environment and schema
     */
    public QueryResult executeQuery(String sql, Environment environment, Schema schema) throws SQLException {
        Environment previousEnv = this.currentEnvironment;
        Schema previousSchema = this.currentSchema;
        
        try {
            switchEnvironment(environment);
            switchSchema(schema);
            return executeQuery(sql);
        } finally {
            // Restore previous environment and schema
            this.currentEnvironment = previousEnv;
            this.currentSchema = previousSchema;
        }
    }
    
    /**
     * Get execution plan for a query
     */
    public String getExecutionPlan(String sql) throws SQLException {
        Connection connection = getCurrentConnection();
        
        // Check if database is read-only (Active Data Guard standby)
        if (isReadOnlyDatabase(connection)) {
            return "EXPLAIN PLAN not available on read-only database (Active Data Guard standby).\n" +
                   "This is a read-only environment where plan table operations are not permitted.\n" +
                   "Query: " + sql;
        }
        
        String finalSql = addSchemaToQuery(sql, currentSchema);
        
        // Create a unique plan table name
        String planTable = "PLAN_TABLE_" + System.currentTimeMillis();
        
        try {
            // Create plan table if it doesn't exist
            try (Statement stmt = connection.createStatement()) {
                stmt.setQueryTimeout(30); // 30 seconds timeout for plan table creation
                stmt.execute("CREATE GLOBAL TEMPORARY TABLE " + planTable + " AS SELECT * FROM PLAN_TABLE WHERE 1=0");
            }
            
            // Generate execution plan
            String explainSql = "EXPLAIN PLAN SET STATEMENT_ID = 'MCP_PLAN' INTO " + planTable + " FOR " + finalSql;
            try (Statement stmt = connection.createStatement()) {
                stmt.setQueryTimeout(30); // 30 seconds timeout for explain plan
                stmt.execute(explainSql);
            }
            
            // Retrieve execution plan
            StringBuilder planOutput = new StringBuilder();
            String selectPlan = "SELECT LPAD(' ', 2 * LEVEL) || OPERATION || ' ' || OPTIONS || ' ' || OBJECT_NAME AS PLAN_LINE " +
                               "FROM " + planTable + " " +
                               "WHERE STATEMENT_ID = 'MCP_PLAN' " +
                               "CONNECT BY PRIOR ID = PARENT_ID " +
                               "START WITH ID = 0 " +
                               "ORDER BY ID";
            
            try (Statement stmt = connection.createStatement()) {
                stmt.setQueryTimeout(30); // 30 seconds timeout for plan retrieval
                try (ResultSet rs = stmt.executeQuery(selectPlan)) {
                    while (rs.next()) {
                        planOutput.append(rs.getString("PLAN_LINE")).append("\n");
                    }
                }
            }
            
            return planOutput.toString();
            
        } finally {
            // Clean up plan table
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("DROP TABLE " + planTable);
            } catch (SQLException e) {
                // Ignore cleanup errors
            }
        }
    }
    
    /**
     * Check if the database is read-only (Active Data Guard standby)
     */
    private boolean isReadOnlyDatabase(Connection connection) {
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT DATABASE_ROLE FROM V$DATABASE")) {
            if (rs.next()) {
                String role = rs.getString("DATABASE_ROLE");
                return "PHYSICAL STANDBY".equals(role) || "LOGICAL STANDBY".equals(role);
            }
        } catch (SQLException e) {
            // If we can't determine, assume it might be read-only if we get ORA-16397
            return e.getMessage() != null && e.getMessage().contains("ORA-16397");
        }
        return false;
    }
    

    
    /**
     * Add schema prefix to query if not already present
     */
    private String addSchemaToQuery(String sql, Schema schema) {
        if (sql == null || schema == null) {
            return sql;
        }
        
        String upperSql = sql.toUpperCase().trim();
        String schemaName = schema.getName();
        
        // If query already contains schema reference, return as-is
        if (upperSql.contains(schemaName + ".")) {
            return sql;
        }
        
        // Simple schema prefixing for common patterns
        // This is a basic implementation - more sophisticated parsing could be added
        return sql; // For now, let users specify schema explicitly in their queries
    }
    
    /**
     * Test connection to an environment
     */
    public boolean testConnection(Environment environment) {
        try {
            Connection connection = connectToEnvironment(environment);
            try (Statement stmt = connection.createStatement()) {
                stmt.setQueryTimeout(10); // 10 seconds timeout for test query
                try (ResultSet rs = stmt.executeQuery("SELECT 1 FROM DUAL")) {
                    return rs.next();
                }
            }
        } catch (SQLException e) {
            System.err.println("Connection test failed for " + environment + ": " + e.getMessage());
            return false;
        } catch (Exception e) {
            System.err.println("Unexpected error testing connection for " + environment + ": " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Close all connections
     */
    public void closeAllConnections() {
        for (Connection connection : connectionPool.values()) {
            try {
                if (connection != null && !connection.isClosed()) {
                    connection.close();
                }
            } catch (SQLException e) {
                // Log error but continue closing other connections
                System.err.println("Error closing connection: " + e.getMessage());
            }
        }
        connectionPool.clear();
    }
    
    // Getters
    public Environment getCurrentEnvironment() {
        return currentEnvironment;
    }
    
    public Schema getCurrentSchema() {
        return currentSchema;
    }
    
    public String getCurrentEnvironmentDisplay() {
        return config.getEnvironmentDisplayName(currentEnvironment);
    }
}