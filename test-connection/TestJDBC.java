import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Properties;

/**
 * Test connection to Oracle Database using Kerberos authentication.
 * 
 * ⚠️  IMPORTANT: This file contains placeholder values that MUST be replaced
 *     with your actual configuration before running. Look for "← REPLACE" comments.
 * 
 * This is a standalone test utility to verify database connectivity before
 * integrating with the main MCP server.
 * 
 * To run this test, you need to:
 * 1. Download Oracle JDBC driver (ojdbc11.jar) from Oracle's website
 * 2. Replace ALL placeholder values (marked with "← REPLACE" comments)
 * 3. Compile: javac -cp ".:ojdbc11.jar" TestJDBC.java
 * 4. Run: java -cp ".:ojdbc11.jar" TestJDBC
 * 
 * Note: The Oracle JDBC driver is automatically managed by Maven in the main project.
 */
public class TestJDBC {
    public static void main(String[] args) {
        try {
            
            // Enable Kerberos debug logging
            System.setProperty("sun.security.krb5.debug", "true");
            // ← REPLACE: Update with your actual krb5.conf file path
            System.setProperty("java.security.krb5.conf", "/path/to/your/krb5.conf");
            
            Properties props = new Properties();
            props.setProperty("oracle.net.authentication_services", "(KERBEROS5)");
            // ← REPLACE: Update with your actual Kerberos cache file path
            props.setProperty("oracle.net.kerberos5_cc_name", "FILE:/path/to/your/krb5cc_username");
            props.setProperty("oracle.net.CONNECT_TIMEOUT", "120000");
            
            // Enable JDBC tracing (optional)
            props.setProperty("oracle.jdbc.Trace", "true");
            // ← REPLACE: Update with your desired log file path
            props.setProperty("oracle.jdbc.LogFile", "/path/to/your/jdbc_trace.log");
            
            System.out.println("Connecting to database...");
            
            // ← REPLACE: Update with your actual database connection string
            String url = "jdbc:oracle:thin:@(DESCRIPTION = " +
                    "(CONNECT_TIMEOUT=120 sec)" +
                    "(RETRY_COUNT=20)" +
                    "(RETRY_DELAY=3)" +
                    "(TRANSPORT_CONNECT_TIMEOUT=3 sec)" +
                    "(ADDRESS_LIST = " +
                    "(LOAD_BALANCE=on)" +
                    // ← REPLACE: Update with your actual database hostname and port
                    "(ADDRESS = (PROTOCOL = TCP)(HOST = your-oracle-scan.company.net)(PORT = 1521))" +
                    ")" +
                    "(ADDRESS_LIST = " +
                    "(LOAD_BALANCE=on)" +
                    // ← REPLACE: Update with your actual database hostname and port (if using RAC)
                    "(ADDRESS = (PROTOCOL = TCP)(HOST = your-oracle-scan2.company.net)(PORT = 1521))" +
                    ")" +
                    "(CONNECT_DATA=" +
                    // ← REPLACE: Update with your actual Oracle service name
                    "(SERVICE_NAME = YOUR_DATABASE_SERVICE_NAME)" +
                    ")" +
                    ")";
            
            Connection conn = DriverManager.getConnection(url, props);
            System.out.println("Connected successfully");
            
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT * FROM dual");
            while (rs.next()) {
                System.out.println(rs.getString(1));
            }
            
            conn.close();
            
            // Check Kerberos tickets
            System.out.println("Checking Kerberos tickets after connection attempt...");
            Process process = Runtime.getRuntime().exec("klist");
            java.util.Scanner scanner = new java.util.Scanner(process.getInputStream());
            while (scanner.hasNextLine()) {
                System.out.println(scanner.nextLine());
            }
            scanner.close();
            
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Error details: " + e.getMessage());
            if (e.getMessage() != null && e.getMessage().contains("ORA-01017")) {
                System.err.println("Kerberos authentication failed. Possible causes:");
                System.err.println("- Missing or invalid Kerberos ticket (check with 'klist')");
                System.err.println("- Incorrect krb5.conf configuration");
                System.err.println("- Oracle database not configured for Kerberos");
                System.err.println("- Incorrect service principal for the Oracle database");
            }
        }
    }
}