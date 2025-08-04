import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Properties;

/**
 * Test connection to Oracle Database using Kerberos authentication.
 * 
 * This is a standalone test utility to verify database connectivity before
 * integrating with the main MCP server.
 * 
 * To run this test, you need to:
 * 1. Download Oracle JDBC driver (ojdbc11.jar) from Oracle's website
 * 2. Compile: javac -cp ".:ojdbc11.jar" TestJDBC.java
 * 3. Run: java -cp ".:ojdbc11.jar" TestJDBC
 * 
 * Note: The Oracle JDBC driver is automatically managed by Maven in the main project.
 */
public class TestJDBC {
    public static void main(String[] args) {
        try {
            
            // Enable Kerberos debug logging
            System.setProperty("sun.security.krb5.debug", "true");
            System.setProperty("java.security.krb5.conf", "H:\\Kerberos\\config\\krb5.conf");
            
            Properties props = new Properties();
            props.setProperty("oracle.net.authentication_services", "(KERBEROS5)");
            props.setProperty("oracle.net.kerberos5_cc_name", "FILE:C:\\Users\\E931990\\krb5cc_E931990");
            props.setProperty("oracle.net.CONNECT_TIMEOUT", "120000");
            
            // Enable JDBC tracing
            props.setProperty("oracle.jdbc.Trace", "true");
            props.setProperty("oracle.jdbc.LogFile", "I:\\TEST_FOLDER\\oracle-mcp-server\\jdbc_trace.log");
            
            System.out.println("Connecting to database...");
            
            String url = "jdbc:oracle:thin:@(DESCRIPTION = " +
                    "(CONNECT_TIMEOUT=120 sec)" +
                    "(RETRY_COUNT=20)" +
                    "(RETRY_DELAY=3)" +
                    "(TRANSPORT_CONNECT_TIMEOUT=3 sec)" +
                    "(ADDRESS_LIST = " +
                    "(LOAD_BALANCE=on)" +
                    "(ADDRESS = (PROTOCOL = TCP)(HOST = wh-167f1c89de-scan.svr.us.jpmchase.net)(PORT = 6335))" +
                    ")" +
                    "(ADDRESS_LIST = " +
                    "(LOAD_BALANCE=on)" +
                    "(ADDRESS = (PROTOCOL = TCP)(HOST = wh-12a6a718dc-scan.svr.us.jpmchase.net)(PORT = 6335))" +
                    ")" +
                    "(CONNECT_DATA=" +
                    "(SERVICE_NAME = ACCESSFX_PRD_PP085667_300323102705_F_READONLY)" +
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