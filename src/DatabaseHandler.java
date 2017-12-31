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
    private Statement batchStatement = null;

    private void initialiseDiskSQL() throws IOException {
            diskSQL = new PrintWriter("res/" + user + ".sql");
    }

    public void setWriteToFile(boolean wtf) {
        WRITE_TO_FILE = wtf;
    }

    public void sendSQLFileToDatabase(boolean flush) throws SQLException, IOException {
        File file = new File("res/" + user + ".sql");

        if (!file.exists())
            return;

        System.out.println("Flushing '" + user + "' SQL file to database...");

        setAutoCommit(false);

        if (diskSQL != null)
            diskSQL.close();

        BufferedReader reader = new BufferedReader(new FileReader(file));
        String line;

        while ((line = reader.readLine()) != null)
            addBatchCommand(line);

        reader.close();

        executeBatch();

        setAutoCommit(true);

        if (!flush)
            initialiseDiskSQL();
    }

    public boolean init(String username, String password) throws ClassNotFoundException, SQLException, IOException {
        Class.forName("com.mysql.jdbc.Driver");
        user = username;

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
            sendSQLFileToDatabase(true);
            initialiseDiskSQL();
        }

        return (connection != null);
    }


    public void commit() {
        if (uncommittedStatements == 0)
            return;

        System.out.println("COMMITTING " + uncommittedStatements + " UNCOMMITTED STATEMENTS...");

        try {
            connection.commit();
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(-1);
        }

        uncommittedStatements = 0;
        System.out.println("COMMITTED!");
    }

    public boolean setAutoCommit(boolean autoCommit) throws SQLException {
        connection.setAutoCommit(autoCommit);

        return connection.getAutoCommit() == autoCommit;
    }

    public void addBatchCommand(String command) throws SQLException {
        if (batchStatement == null)
            batchStatement = connection.createStatement();

        batchStatement.addBatch(command);
    }

    public void executeBatch() throws SQLException {
        if (batchStatement == null) return;
        System.out.println("Executing batch command...");
        batchStatement.executeBatch();
        connection.commit();
        batchStatement.clearBatch();

    }

    public Boolean executeCommand(String command) throws SQLException {
        Statement statement = connection.createStatement();

        boolean result;

        if (connection.getAutoCommit() && !WRITE_TO_FILE) {
            result = statement.execute(command);
            statement.close();
        } else if (!connection.getAutoCommit() && !WRITE_TO_FILE) {
            addBatchCommand(command);
            result = true;
        } else {
            result = true;
            diskSQL.println(command);
        }

        if (!connection.getAutoCommit() && !WRITE_TO_FILE) {
            uncommittedStatements++;
            if (MAXIMUM_UNCOMMITTED_STATEMENTS > 0 && uncommittedStatements >= MAXIMUM_UNCOMMITTED_STATEMENTS)
                executeBatch();
        }

        return result;
    }

    public ArrayList<String> executeQuery(String command) throws SQLException{
        Statement query = connection.createStatement();

        ArrayList<String> tempArr = new ArrayList();

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