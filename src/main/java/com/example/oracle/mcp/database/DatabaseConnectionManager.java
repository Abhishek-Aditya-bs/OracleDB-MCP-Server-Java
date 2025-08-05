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
        
        try (Statement statement = connection.createStatement()) {
            // Set query timeout to prevent long-running queries (60 seconds)
            statement.setQueryTimeout(60);
            
            try (ResultSet resultSet = statement.executeQuery(sql)) {
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