import java.io.*;
import java.sql.*;
import java.util.ArrayList;

public class DatabaseHandler {
    private String user = null;
    private Connection connection = null;
    private final int MAXIMUM_UNCOMMITTED_STATEMENTS = -1;
    private PrintWriter diskSQL;
    private int uncommittedStatements = 0;
    private boolean WRITE_TO_FILE = false;

    private void initialiseDiskSQL() {
        try {
            diskSQL = new PrintWriter("res/" + user + ".sql");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    public void setWriteToFile(boolean wtf) {
        WRITE_TO_FILE = wtf;
    }

    public void sendSQLFileToDatabase() throws SQLException, IOException {
        boolean autoCommit = connection.getAutoCommit();
        setAutoCommit(false);

        diskSQL.close();

        BufferedReader reader = new BufferedReader(new FileReader("res/" + user + ".sql"));
        String line;

        setWriteToFile(false);

        while ((line = reader.readLine()) != null)
            executeCommand(line);

        commit();

        setWriteToFile(true);
        setAutoCommit(autoCommit);

        reader.close();
        initialiseDiskSQL();
    }

    public boolean init(String username, String password) throws ClassNotFoundException, SQLException {
        Class.forName("com.mysql.jdbc.Driver");
        user = username;

        initialiseDiskSQL();

        try {
            connection = DriverManager.getConnection("jdbc:mysql://localhost", username, password);
        } catch (SQLException e){
            System.err.println(e.getMessage());
            if(e.getErrorCode() == 1045) {
                System.out.println("Please create an account on the MySQL Server with these credentials:");
                System.out.println("Username: " + username);
                System.out.println("Password: " + password);
            }

            System.exit(-1);
        }

        if(connection == null)  System.err.println("Failed to initialise database connection!");
        else {
            executeCommand("USE automated_trader");
            System.out.println("Initialised database connection for '" + username + "'");
        }

        return (connection != null);
    }


    public void commit() throws SQLException {
        connection.commit();
    }

    public boolean setAutoCommit(boolean autoCommit) throws SQLException {
        connection.setAutoCommit(autoCommit);

        return connection.getAutoCommit() == autoCommit;
    }

    public Boolean executeCommand(String command) throws SQLException {
        Statement statement = connection.createStatement();

        boolean result;

        if (!WRITE_TO_FILE)
            result = statement.execute(command);
        else {
            result = true;
            diskSQL.println(command);
        }

        if (!connection.getAutoCommit() && !WRITE_TO_FILE && MAXIMUM_UNCOMMITTED_STATEMENTS > 0 && uncommittedStatements++ >= MAXIMUM_UNCOMMITTED_STATEMENTS) {
            commit();
            System.out.println("----COMMITTED UNCOMMITTED STATEMENTS----");
            uncommittedStatements = 0;
        }

        return result;
    }

    public ArrayList<String> executeQuery(String command) throws SQLException{
        Statement query = connection.createStatement();

        ArrayList<String> tempArr = new ArrayList<>();

        ResultSet tempRs = query.executeQuery(command);
        ResultSetMetaData rsmd = tempRs.getMetaData();

        while(tempRs.next()){
            String temp = tempRs.getString(1);
            for(int i = 2; i <= rsmd.getColumnCount(); i++) {
                temp += "," + tempRs.getString(i);
            }

            tempArr.add(temp);
        }

        return tempArr;
    }

    public void close() throws SQLException {
        connection.close();
        System.out.println("Closed database connection for '" + user + "'");
    }
}