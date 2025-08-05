package com.example.oracle.mcp.database;

import com.example.oracle.mcp.config.DatabaseConfig.Schema;

import java.sql.*;
import java.util.*;
import java.util.Date;

/**
 * Represents schema information including tables, views, and other database objects.
 */
public class SchemaInfo {
    
    private final Schema schema;
    private final List<TableInfo> tables;
    private final List<ViewInfo> views;
    private final List<IndexInfo> indexes;
    private final Map<String, Object> metadata;
    
    public SchemaInfo(Schema schema, List<TableInfo> tables, List<ViewInfo> views, 
                     List<IndexInfo> indexes, Map<String, Object> metadata) {
        this.schema = schema;
        this.tables = tables != null ? tables : new ArrayList<>();
        this.views = views != null ? views : new ArrayList<>();
        this.indexes = indexes != null ? indexes : new ArrayList<>();
        this.metadata = metadata != null ? metadata : new HashMap<>();
    }
    
    /**
     * Load schema information from database connection
     */
    public static SchemaInfo loadSchemaInfo(Connection connection, Schema schema) throws SQLException {
        List<TableInfo> tables = loadTables(connection, schema);
        List<ViewInfo> views = loadViews(connection, schema);
        List<IndexInfo> indexes = loadIndexes(connection, schema);
        Map<String, Object> metadata = loadSchemaMetadata(connection, schema);
        
        return new SchemaInfo(schema, tables, views, indexes, metadata);
    }
    
    private static List<TableInfo> loadTables(Connection connection, Schema schema) throws SQLException {
        List<TableInfo> tables = new ArrayList<>();
        
        String sql = "SELECT table_name, num_rows, last_analyzed " +
                    "FROM all_tables " +
                    "WHERE owner = ? " +
                    "ORDER BY table_name";
        
        // Debug logging
        System.err.println("DEBUG: Loading tables for schema: '" + schema.getName() + "'");
        System.err.println("DEBUG: SQL query: " + sql);
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, schema.getName().toUpperCase()); // Force uppercase for Oracle
            
            try (ResultSet rs = stmt.executeQuery()) {
                int count = 0;
                while (rs.next()) {
                    String tableName = rs.getString("table_name");
                    Long numRows = rs.getLong("num_rows");
                    if (rs.wasNull()) numRows = null;
                    java.util.Date lastAnalyzed = rs.getDate("last_analyzed");
                    
                    List<ColumnInfo> columns = loadTableColumns(connection, schema, tableName);
                    tables.add(new TableInfo(tableName, numRows, lastAnalyzed, columns));
                    count++;
                }
                System.err.println("DEBUG: Found " + count + " tables for schema '" + schema.getName() + "'");
            }
        } catch (SQLException e) {
            System.err.println("ERROR: Failed to load tables for schema '" + schema.getName() + "': " + e.getMessage());
            throw e;
        }
        
        return tables;
    }
    
    private static List<ColumnInfo> loadTableColumns(Connection connection, Schema schema, String tableName) 
            throws SQLException {
        List<ColumnInfo> columns = new ArrayList<>();
        
        String sql = "SELECT column_name, data_type, data_length, data_precision, data_scale, " +
                    "nullable, column_id " +
                    "FROM all_tab_columns " +
                    "WHERE owner = ? AND table_name = ? " +
                    "ORDER BY column_id";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, schema.getName().toUpperCase()); // Force uppercase for Oracle
            stmt.setString(2, tableName);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    columns.add(new ColumnInfo(
                        rs.getString("column_name"),
                        rs.getString("data_type"),
                        rs.getInt("data_length"),
                        rs.getInt("data_precision"),
                        rs.getInt("data_scale"),
                        "Y".equals(rs.getString("nullable")),
                        rs.getInt("column_id")
                    ));
                }
            }
        }
        
        return columns;
    }
    
    private static List<ViewInfo> loadViews(Connection connection, Schema schema) throws SQLException {
        List<ViewInfo> views = new ArrayList<>();
        
        String sql = "SELECT view_name, text " +
                    "FROM all_views " +
                    "WHERE owner = ? " +
                    "ORDER BY view_name";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, schema.getName());
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String viewName = rs.getString("view_name");
                    String text = rs.getString("text");
                    List<ColumnInfo> columns = loadTableColumns(connection, schema, viewName);
                    views.add(new ViewInfo(viewName, text, columns));
                }
            }
        }
        
        return views;
    }
    
    private static List<IndexInfo> loadIndexes(Connection connection, Schema schema) throws SQLException {
        List<IndexInfo> indexes = new ArrayList<>();
        
        String sql = "SELECT index_name, table_name, uniqueness, index_type " +
                    "FROM all_indexes " +
                    "WHERE owner = ? " +
                    "ORDER BY table_name, index_name";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, schema.getName());
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    indexes.add(new IndexInfo(
                        rs.getString("index_name"),
                        rs.getString("table_name"),
                        "UNIQUE".equals(rs.getString("uniqueness")),
                        rs.getString("index_type")
                    ));
                }
            }
        }
        
        return indexes;
    }
    
    private static Map<String, Object> loadSchemaMetadata(Connection connection, Schema schema) throws SQLException {
        Map<String, Object> metadata = new HashMap<>();
        
        // Get table count
        String tableSql = "SELECT COUNT(*) as table_count FROM all_tables WHERE owner = ?";
        try (PreparedStatement stmt = connection.prepareStatement(tableSql)) {
            stmt.setString(1, schema.getName());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    metadata.put("table_count", rs.getInt("table_count"));
                }
            }
        }
        
        // Get view count
        String viewSql = "SELECT COUNT(*) as view_count FROM all_views WHERE owner = ?";
        try (PreparedStatement stmt = connection.prepareStatement(viewSql)) {
            stmt.setString(1, schema.getName());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    metadata.put("view_count", rs.getInt("view_count"));
                }
            }
        }
        
        return metadata;
    }
    
    /**
     * Find tables matching a pattern
     */
    public List<TableInfo> findTables(String pattern) {
        if (pattern == null || pattern.trim().isEmpty()) {
            return tables;
        }
        
        String upperPattern = pattern.toUpperCase();
        return tables.stream()
                .filter(table -> table.getName().toUpperCase().contains(upperPattern))
                .toList();
    }
    
    /**
     * Convert to JSON-friendly format
     */
    public Map<String, Object> toMap() {
        Map<String, Object> result = new HashMap<>();
        result.put("schema", schema.getName());
        result.put("metadata", metadata);
        result.put("tables", tables.stream().map(TableInfo::toMap).toList());
        result.put("views", views.stream().map(ViewInfo::toMap).toList());
        result.put("indexes", indexes.stream().map(IndexInfo::toMap).toList());
        return result;
    }
    
    // Getters
    public Schema getSchema() { return schema; }
    public List<TableInfo> getTables() { return tables; }
    public List<ViewInfo> getViews() { return views; }
    public List<IndexInfo> getIndexes() { return indexes; }
    public Map<String, Object> getMetadata() { return metadata; }
    
    // Inner classes for database objects
    public static class TableInfo {
        private final String name;
        private final Long numRows;
        private final java.util.Date lastAnalyzed;
        private final List<ColumnInfo> columns;
        
        public TableInfo(String name, Long numRows, java.util.Date lastAnalyzed, List<ColumnInfo> columns) {
            this.name = name;
            this.numRows = numRows;
            this.lastAnalyzed = lastAnalyzed;
            this.columns = columns != null ? columns : new ArrayList<>();
        }
        
        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("name", name);
            map.put("numRows", numRows);
            map.put("lastAnalyzed", lastAnalyzed);
            map.put("columns", columns.stream().map(ColumnInfo::toMap).toList());
            return map;
        }
        
        // Getters
        public String getName() { return name; }
        public Long getNumRows() { return numRows; }
        public java.util.Date getLastAnalyzed() { return lastAnalyzed; }
        public List<ColumnInfo> getColumns() { return columns; }
    }
    
    public static class ViewInfo {
        private final String name;
        private final String definition;
        private final List<ColumnInfo> columns;
        
        public ViewInfo(String name, String definition, List<ColumnInfo> columns) {
            this.name = name;
            this.definition = definition;
            this.columns = columns != null ? columns : new ArrayList<>();
        }
        
        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("name", name);
            map.put("definition", definition);
            map.put("columns", columns.stream().map(ColumnInfo::toMap).toList());
            return map;
        }
        
        // Getters
        public String getName() { return name; }
        public String getDefinition() { return definition; }
        public List<ColumnInfo> getColumns() { return columns; }
    }
    
    public static class IndexInfo {
        private final String name;
        private final String tableName;
        private final boolean unique;
        private final String type;
        
        public IndexInfo(String name, String tableName, boolean unique, String type) {
            this.name = name;
            this.tableName = tableName;
            this.unique = unique;
            this.type = type;
        }
        
        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("name", name);
            map.put("tableName", tableName);
            map.put("unique", unique);
            map.put("type", type);
            return map;
        }
        
        // Getters
        public String getName() { return name; }
        public String getTableName() { return tableName; }
        public boolean isUnique() { return unique; }
        public String getType() { return type; }
    }
    
    public static class ColumnInfo {
        private final String name;
        private final String dataType;
        private final int length;
        private final int precision;
        private final int scale;
        private final boolean nullable;
        private final int position;
        
        public ColumnInfo(String name, String dataType, int length, int precision, int scale, 
                         boolean nullable, int position) {
            this.name = name;
            this.dataType = dataType;
            this.length = length;
            this.precision = precision;
            this.scale = scale;
            this.nullable = nullable;
            this.position = position;
        }
        
        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("name", name);
            map.put("dataType", dataType);
            map.put("length", length);
            map.put("precision", precision);
            map.put("scale", scale);
            map.put("nullable", nullable);
            map.put("position", position);
            return map;
        }
        
        // Getters
        public String getName() { return name; }
        public String getDataType() { return dataType; }
        public int getLength() { return length; }
        public int getPrecision() { return precision; }
        public int getScale() { return scale; }
        public boolean isNullable() { return nullable; }
        public int getPosition() { return position; }
    }
}