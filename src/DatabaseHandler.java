import javax.management.Query;
import java.sql.*;
import java.util.ArrayList;

public class DatabaseHandler {
    private Connection connection = null;

    public void init(String username, String password) throws SQLException, ClassNotFoundException {
        Class.forName("com.mysql.jdbc.Driver");
        connection = DriverManager.getConnection("jdbc:mysql://localhost:3306/automated_trader", username, password);
    }

    public Boolean executeCommand(String command) throws SQLException {
        Statement statement = connection.createStatement();
        return statement.execute(command);
    }

    public ArrayList<String> executeQuery(String command) throws SQLException{
        Statement query = connection.createStatement();

        ArrayList<String> tempArr = new ArrayList<String>();

        ResultSet tempRs = query.executeQuery(command);
        ResultSetMetaData rsmd = tempRs.getMetaData();

        while(tempRs.next()){
            String temp = tempRs.getString(1);;
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
