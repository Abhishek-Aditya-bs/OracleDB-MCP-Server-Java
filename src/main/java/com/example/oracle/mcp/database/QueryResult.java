package com.example.oracle.mcp.database;

import com.example.oracle.mcp.config.DatabaseConfig.Environment;
import com.example.oracle.mcp.config.DatabaseConfig.Schema;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Date;
import java.sql.Clob;
import java.sql.Blob;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents the result of a database query execution.
 */
public class QueryResult {
    
    private final List<String> columnNames;
    private final List<String> columnTypes;
    private final List<Map<String, Object>> rows;
    private final int rowCount;
    private final long executionTimeMs;
    private final Environment environment;
    private final Schema schema;
    private final String query;
    
    private QueryResult(List<String> columnNames, List<String> columnTypes, 
                       List<Map<String, Object>> rows, Environment environment, 
                       Schema schema, String query, long executionTimeMs) {
        this.columnNames = columnNames;
        this.columnTypes = columnTypes;
        this.rows = rows;
        this.rowCount = rows.size();
        this.environment = environment;
        this.schema = schema;
        this.query = query;
        this.executionTimeMs = executionTimeMs;
    }
    
    /**
     * Create QueryResult from ResultSet
     */
    public static QueryResult fromResultSet(ResultSet resultSet, Environment environment, Schema schema) 
            throws SQLException {
        return fromResultSet(resultSet, environment, schema, null);
    }
    
    public static QueryResult fromResultSet(ResultSet resultSet, Environment environment, 
                                          Schema schema, String query) throws SQLException {
        long startTime = System.currentTimeMillis();
        
        ResultSetMetaData metaData = resultSet.getMetaData();
        int columnCount = metaData.getColumnCount();
        
        // Extract column information
        List<String> columnNames = new ArrayList<>();
        List<String> columnTypes = new ArrayList<>();
        
        for (int i = 1; i <= columnCount; i++) {
            columnNames.add(metaData.getColumnName(i));
            columnTypes.add(metaData.getColumnTypeName(i));
        }
        
        // Extract rows
        List<Map<String, Object>> rows = new ArrayList<>();
        while (resultSet.next()) {
            Map<String, Object> row = new HashMap<>();
            for (int i = 1; i <= columnCount; i++) {
                String columnName = columnNames.get(i - 1);
                Object value = resultSet.getObject(i);
                // Convert Oracle-specific types to JSON-serializable types
                Object convertedValue = convertOracleType(value);
                row.put(columnName, convertedValue);
            }
            rows.add(row);
        }
        
        long executionTime = System.currentTimeMillis() - startTime;
        
        return new QueryResult(columnNames, columnTypes, rows, environment, schema, query, executionTime);
    }
    
    /**
     * Convert to JSON-friendly format
     */
    public Map<String, Object> toMap() {
        Map<String, Object> result = new HashMap<>();
        
        result.put("environment", environment.getKey());
        result.put("schema", schema.getName());
        result.put("columnNames", columnNames);
        result.put("columnTypes", columnTypes);
        result.put("rows", rows);
        result.put("rowCount", rowCount);
        result.put("executionTimeMs", executionTimeMs);
        
        if (query != null) {
            result.put("query", query);
        }
        
        return result;
    }
    
    /**
     * Convert to formatted string for display
     */
    public String toFormattedString() {
        StringBuilder sb = new StringBuilder();
        
        sb.append("Query Results\n");
        sb.append("=============\n");
        sb.append("Environment: ").append(environment.getKey().toUpperCase()).append("\n");
        sb.append("Schema: ").append(schema.getName()).append("\n");
        sb.append("Rows: ").append(rowCount).append("\n");
        sb.append("Execution Time: ").append(executionTimeMs).append("ms\n\n");
        
        if (rowCount == 0) {
            sb.append("No rows returned.\n");
            return sb.toString();
        }
        
        // Create table format
        List<Integer> columnWidths = new ArrayList<>();
        for (String columnName : columnNames) {
            int maxWidth = columnName.length();
            for (Map<String, Object> row : rows) {
                Object value = row.get(columnName);
                if (value != null) {
                    maxWidth = Math.max(maxWidth, value.toString().length());
                }
            }
            columnWidths.add(Math.min(maxWidth, 50)); // Cap at 50 characters
        }
        
        // Header
        for (int i = 0; i < columnNames.size(); i++) {
            String columnName = columnNames.get(i);
            int width = columnWidths.get(i);
            sb.append(String.format("%-" + width + "s", columnName));
            if (i < columnNames.size() - 1) {
                sb.append(" | ");
            }
        }
        sb.append("\n");
        
        // Separator
        for (int i = 0; i < columnNames.size(); i++) {
            int width = columnWidths.get(i);
            sb.append("-".repeat(width));
            if (i < columnNames.size() - 1) {
                sb.append("-+-");
            }
        }
        sb.append("\n");
        
        // Data rows (limit to first 100 rows for display)
        int displayRows = Math.min(rows.size(), 100);
        for (int r = 0; r < displayRows; r++) {
            Map<String, Object> row = rows.get(r);
            for (int i = 0; i < columnNames.size(); i++) {
                String columnName = columnNames.get(i);
                Object value = row.get(columnName);
                String displayValue = (value != null) ? value.toString() : "NULL";
                int width = columnWidths.get(i);
                
                // Truncate if too long
                if (displayValue.length() > width) {
                    displayValue = displayValue.substring(0, width - 3) + "...";
                }
                
                sb.append(String.format("%-" + width + "s", displayValue));
                if (i < columnNames.size() - 1) {
                    sb.append(" | ");
                }
            }
            sb.append("\n");
        }
        
        if (rows.size() > 100) {
            sb.append("\n... and ").append(rows.size() - 100).append(" more rows\n");
        }
        
        return sb.toString();
    }
    
    // Getters
    public List<String> getColumnNames() {
        return columnNames;
    }
    
    public List<String> getColumnTypes() {
        return columnTypes;
    }
    
    public List<Map<String, Object>> getRows() {
        return rows;
    }
    
    public int getRowCount() {
        return rowCount;
    }
    
    public long getExecutionTimeMs() {
        return executionTimeMs;
    }
    
    public Environment getEnvironment() {
        return environment;
    }
    
    public Schema getSchema() {
        return schema;
    }
    
    public String getQuery() {
        return query;
    }
    
    /**
     * Convert Oracle-specific data types to JSON-serializable Java types
     */
    private static Object convertOracleType(Object value) {
        if (value == null) {
            return null;
        }
        
        // Handle Oracle TIMESTAMP types
        if (value instanceof oracle.sql.TIMESTAMP) {
            try {
                oracle.sql.TIMESTAMP timestamp = (oracle.sql.TIMESTAMP) value;
                return timestamp.timestampValue().toString();
            } catch (SQLException e) {
                return value.toString();
            }
        }
        
        // Handle Oracle DATE types
        if (value instanceof oracle.sql.DATE) {
            oracle.sql.DATE date = (oracle.sql.DATE) value;
            return date.timestampValue().toString();
        }
        
        // Handle standard SQL TIMESTAMP
        if (value instanceof Timestamp) {
            return value.toString();
        }
        
        // Handle standard SQL DATE
        if (value instanceof Date) {
            return value.toString();
        }
        
        // Handle CLOB (Character Large Object)
        if (value instanceof Clob) {
            try {
                Clob clob = (Clob) value;
                return clob.getSubString(1, (int) clob.length());
            } catch (SQLException e) {
                return "[CLOB: " + e.getMessage() + "]";
            }
        }
        
        // Handle BLOB (Binary Large Object) - convert to hex or base64
        if (value instanceof Blob) {
            try {
                Blob blob = (Blob) value;
                byte[] bytes = blob.getBytes(1, (int) blob.length());
                return "[BLOB: " + bytes.length + " bytes]";
            } catch (SQLException e) {
                return "[BLOB: " + e.getMessage() + "]";
            }
        }
        
        // Handle Oracle NUMBER types
        if (value instanceof oracle.sql.NUMBER) {
            try {
                oracle.sql.NUMBER number = (oracle.sql.NUMBER) value;
                return number.bigDecimalValue();
            } catch (SQLException e) {
                return value.toString();
            }
        }
        
        // Handle other Oracle-specific types by converting to string
        if (value.getClass().getName().startsWith("oracle.sql.")) {
            return value.toString();
        }
        
        // Return as-is for standard Java types
        return value;
    }
}