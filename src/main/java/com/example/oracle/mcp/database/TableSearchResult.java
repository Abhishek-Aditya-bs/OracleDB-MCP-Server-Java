package com.example.oracle.mcp.database;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * Represents a table found during search with relevance scoring.
 * Used by AI assistants to discover appropriate tables for queries.
 */
public class TableSearchResult {
    
    private final String schemaName;
    private final String tableName;
    private final List<SchemaInfo.ColumnInfo> columns;
    private final int relevanceScore;
    
    public TableSearchResult(String schemaName, String tableName, 
                           List<SchemaInfo.ColumnInfo> columns, int relevanceScore) {
        this.schemaName = schemaName;
        this.tableName = tableName;
        this.columns = columns;
        this.relevanceScore = relevanceScore;
    }
    
    public String getSchemaName() {
        return schemaName;
    }
    
    public String getTableName() {
        return tableName;
    }
    
    public String getFullTableName() {
        return schemaName + "." + tableName;
    }
    
    public List<SchemaInfo.ColumnInfo> getColumns() {
        return columns;
    }
    
    public int getRelevanceScore() {
        return relevanceScore;
    }
    
    /**
     * Get columns that might be relevant for ID searches
     */
    public List<SchemaInfo.ColumnInfo> getIdColumns() {
        return columns.stream()
                .filter(col -> col.getName().toLowerCase().contains("id"))
                .toList();
    }
    
    /**
     * Get columns that might be relevant for status searches
     */
    public List<SchemaInfo.ColumnInfo> getStatusColumns() {
        return columns.stream()
                .filter(col -> col.getName().toLowerCase().contains("status"))
                .toList();
    }
    
    /**
     * Convert to JSON-friendly format for MCP responses
     */
    public Map<String, Object> toMap() {
        Map<String, Object> result = new HashMap<>();
        result.put("schema", schemaName);
        result.put("table", tableName);
        result.put("fullTableName", getFullTableName());
        result.put("relevanceScore", relevanceScore);
        result.put("columns", columns.stream().map(SchemaInfo.ColumnInfo::toMap).toList());
        result.put("idColumns", getIdColumns().stream().map(SchemaInfo.ColumnInfo::toMap).toList());
        result.put("statusColumns", getStatusColumns().stream().map(SchemaInfo.ColumnInfo::toMap).toList());
        result.put("columnCount", columns.size());
        
        return result;
    }
    
    /**
     * Get a summary description for AI assistants
     */
    public String getSummaryDescription() {
        StringBuilder desc = new StringBuilder();
        desc.append("Table: ").append(getFullTableName());
        desc.append(" (").append(columns.size()).append(" columns)");
        
        if (!getIdColumns().isEmpty()) {
            desc.append(" - ID columns: ");
            desc.append(getIdColumns().stream()
                    .map(SchemaInfo.ColumnInfo::getName)
                    .reduce((a, b) -> a + ", " + b).orElse(""));
        }
        
        if (!getStatusColumns().isEmpty()) {
            desc.append(" - Status columns: ");
            desc.append(getStatusColumns().stream()
                    .map(SchemaInfo.ColumnInfo::getName)
                    .reduce((a, b) -> a + ", " + b).orElse(""));
        }
        
        return desc.toString();
    }
}