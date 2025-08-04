package com.example.oracle.mcp.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.List;
import java.util.ArrayList;

/**
 * Database configuration manager for handling multiple environments and schemas.
 */
public class DatabaseConfig {
    
    private static final String CONFIG_FILE = "database.properties";
    private static DatabaseConfig instance;
    private Properties properties;
    
    // Environment types
    public enum Environment {
        DEV("dev"),
        UAT("uat"),
        PROD("prod");
        
        private final String key;
        
        Environment(String key) {
            this.key = key;
        }
        
        public String getKey() {
            return key;
        }
        
        public static Environment fromString(String env) {
            if (env == null) return DEV; // Default to DEV
            
            String envLower = env.toLowerCase().trim();
            switch (envLower) {
                case "dev":
                case "development":
                    return DEV;
                case "uat":
                case "sit":
                case "test":
                case "testing":
                    return UAT;
                case "prod":
                case "production":
                case "live":
                    return PROD;
                default:
                    return DEV; // Default fallback
            }
        }
        
        /**
         * Parse environment from natural language text
         */
        public static Environment parseFromText(String text) {
            if (text == null) return DEV;
            
            String lowerText = text.toLowerCase();
            
            if (lowerText.contains("prod") || lowerText.contains("production") || lowerText.contains("live")) {
                return PROD;
            } else if (lowerText.contains("uat") || lowerText.contains("test") || lowerText.contains("sit")) {
                return UAT;
            } else if (lowerText.contains("dev") || lowerText.contains("development")) {
                return DEV;
            }
            
            return DEV; // Default
        }
    }
    
    // Dynamic Schema Management
    public static class Schema {
        private final String name;
        private final boolean isDefault;
        
        public Schema(String name, boolean isDefault) {
            this.name = name;
            this.isDefault = isDefault;
        }
        
        public String getName() {
            return name;
        }
        
        public boolean isDefault() {
            return isDefault;
        }
        
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            Schema schema = (Schema) obj;
            return name.equals(schema.name);
        }
        
        @Override
        public int hashCode() {
            return name.hashCode();
        }
        
        @Override
        public String toString() {
            return name;
        }
    }
    
    private DatabaseConfig() {
        loadProperties();
    }
    
    public static synchronized DatabaseConfig getInstance() {
        if (instance == null) {
            instance = new DatabaseConfig();
        }
        return instance;
    }
    
    private void loadProperties() {
        properties = new Properties();
        try (InputStream input = DatabaseConfig.class.getClassLoader().getResourceAsStream(CONFIG_FILE)) {
            if (input == null) {
                throw new RuntimeException("Unable to find " + CONFIG_FILE);
            }
            properties.load(input);
        } catch (IOException e) {
            throw new RuntimeException("Error loading database configuration", e);
        }
    }
    
    public String getDatabaseUrl(Environment environment) {
        return properties.getProperty(environment.getKey() + ".url");
    }
    
    public String getKerberosConfigPath() {
        return properties.getProperty("kerberos.config.path");
    }
    
    public String getKerberosCCachePath() {
        return properties.getProperty("kerberos.ccache.path");
    }
    
    public boolean isKerberosDebugEnabled() {
        return Boolean.parseBoolean(properties.getProperty("kerberos.debug", "false"));
    }
    
    public String getConnectionTimeout() {
        return properties.getProperty("connection.timeout", "120000");
    }
    
    public boolean isJdbcTraceEnabled() {
        return Boolean.parseBoolean(properties.getProperty("jdbc.trace.enabled", "false"));
    }
    
    public String getJdbcTraceLogFile() {
        return properties.getProperty("jdbc.trace.logfile", "jdbc_trace.log");
    }
    
    /**
     * Get all configured schemas
     */
    public List<Schema> getAllSchemas() {
        List<Schema> schemas = new ArrayList<>();
        String defaultSchema = properties.getProperty("schema.default", "DEFAULT_SCHEMA");
        String secondarySchema = properties.getProperty("schema.secondary", "SECONDARY_SCHEMA");
        
        schemas.add(new Schema(defaultSchema, true));
        if (!secondarySchema.equals(defaultSchema)) {
            schemas.add(new Schema(secondarySchema, false));
        }
        
        return schemas;
    }
    
    /**
     * Get default schema
     */
    public Schema getDefaultSchema() {
        String defaultSchema = properties.getProperty("schema.default", "DEFAULT_SCHEMA");
        return new Schema(defaultSchema, true);
    }
    
    /**
     * Get secondary schema
     */
    public Schema getSecondarySchema() {
        String secondarySchema = properties.getProperty("schema.secondary", "SECONDARY_SCHEMA");
        return new Schema(secondarySchema, false);
    }
    
    /**
     * Find schema by name (case insensitive)
     */
    public Schema findSchemaByName(String name) {
        if (name == null) return getDefaultSchema();
        
        String upperName = name.toUpperCase().trim();
        for (Schema schema : getAllSchemas()) {
            if (schema.getName().toUpperCase().equals(upperName)) {
                return schema;
            }
        }
        
        return getDefaultSchema(); // Fallback to default
    }
    
    /**
     * Parse schema from natural language text
     */
    public Schema parseSchemaFromText(String text) {
        if (text == null) return getDefaultSchema();
        
        String upperText = text.toUpperCase();
        
        // Try to find any configured schema name in the text
        for (Schema schema : getAllSchemas()) {
            String schemaName = schema.getName().toUpperCase();
            if (upperText.contains(schemaName)) {
                return schema;
            }
            
            // Also check for variations (replace _ with space, etc.)
            String schemaVariation = schemaName.replace("_", " ");
            if (upperText.contains(schemaVariation)) {
                return schema;
            }
        }
        
        // Check for common keywords that might indicate secondary schema
        String secondarySchema = getSecondarySchema().getName().toUpperCase();
        if (upperText.contains("SECONDARY") || upperText.contains("WORKFLOW") || 
            upperText.contains("WF") || upperText.contains("SECOND")) {
            return getSecondarySchema();
        }
        
        return getDefaultSchema();
    }
    
    /**
     * Get database connection properties for a specific environment
     */
    public Properties getConnectionProperties(Environment environment) {
        Properties props = new Properties();
        
        // Kerberos authentication
        props.setProperty("oracle.net.authentication_services", "(KERBEROS5)");
        props.setProperty("oracle.net.kerberos5_cc_name", getKerberosCCachePath());
        
        // Multiple timeout settings for robustness (30 seconds)
        props.setProperty("oracle.net.CONNECT_TIMEOUT", "30000");           // Connection timeout
        props.setProperty("oracle.jdbc.ReadTimeout", "30000");              // Read timeout  
        props.setProperty("oracle.net.READ_TIMEOUT", "30000");              // Network read timeout
        props.setProperty("oracle.net.ns.SQLNetTimeoutConnection", "30");   // SQLNet timeout (seconds)
        
        // JDBC tracing if enabled
        if (isJdbcTraceEnabled()) {
            props.setProperty("oracle.jdbc.Trace", "true");
            props.setProperty("oracle.jdbc.LogFile", getJdbcTraceLogFile());
        }
        
        return props;
    }
    
    /**
     * Get environment-specific display name
     */
    public String getEnvironmentDisplayName(Environment environment) {
        switch (environment) {
            case DEV:
                return "Development";
            case UAT:
                return "UAT/Testing";
            case PROD:
                return "Production";
            default:
                return environment.getKey().toUpperCase();
        }
    }
}