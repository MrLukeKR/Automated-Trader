import java.sql.*;
import java.util.ArrayList;

public class DatabaseHandler {
    private Connection connection = null;

    public boolean init(String username, String password) throws SQLException, ClassNotFoundException {
        Class.forName("com.mysql.jdbc.Driver");

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
        else                    System.out.println("Initialised database connection");

        return (connection != null);
    }

    public Boolean executeCommand(String command) throws SQLException {
        Statement statement = connection.createStatement();
        return statement.execute(command);
    }

    public ArrayList<String> executeQuery(String command) throws SQLException{
            Statement query = connection.createStatement();

        ArrayList<String> tempArr = new ArrayList<>();

        ResultSet tempRs = query.executeQuery(command);
        ResultSetMetaData rsmd = tempRs.getMetaData();

        while(tempRs.next()){
            String temp = tempRs.getString(1);
            for(int i = 2; i <= rsmd.getColumnCount(); i++) {
                temp += tempRs.getString(i);
                if(i <= rsmd.getColumnCount() - 1) temp +=",";
            }

            tempArr.add(temp);
        }

        return tempArr;
    }

    public void close() throws SQLException {
        connection.close();
    }
}
