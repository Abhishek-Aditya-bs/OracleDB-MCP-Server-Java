#!/bin/bash

# Oracle DB MCP Server Build Script

set -e

echo "🔨 Building Oracle DB MCP Server..."

# Check if Maven is installed
if ! command -v mvn &> /dev/null; then
    echo "❌ Maven is not installed. Please install Maven 3.6 or higher."
    exit 1
fi

# Check Java version
if ! command -v java &> /dev/null; then
    echo "❌ Java is not installed. Please install Java 17 or higher."
    exit 1
fi

JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
if [ "$JAVA_VERSION" -lt 17 ]; then
    echo "❌ Java 17 or higher is required. Current version: $JAVA_VERSION"
    exit 1
fi

echo "✅ Java version: $(java -version 2>&1 | head -n 1)"
echo "✅ Maven version: $(mvn -version | head -n 1)"

# Clean and compile
echo "🧹 Cleaning previous builds..."
mvn clean

echo "📦 Compiling and packaging..."
mvn package -q

if [ $? -eq 0 ]; then
    echo "✅ Build successful!"
    echo "📁 JAR file created: target/oracle-db-mcp-server-1.0.0.jar"
    echo ""
    echo "⚠️  IMPORTANT: Before running the server:"
    echo "   1. Update src/main/resources/database.properties with your actual database connection details"
    echo "   2. Replace all placeholder values (Kerberos paths, hostnames, service names)"
    echo ""
    echo "🚀 To run the server (after configuration):"
    echo "   java -jar target/oracle-db-mcp-server-1.0.0.jar"
    echo ""
    echo "🧪 To test database connectivity:"
    echo "   cd test-connection"
    echo "   javac -cp \".:ojdbc11.jar\" TestJDBC.java"
    echo "   java -cp \".:ojdbc11.jar\" TestJDBC"
else
    echo "❌ Build failed!"
    exit 1
fi