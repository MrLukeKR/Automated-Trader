package Default;

import java.io.*;
import java.sql.*;
import java.util.ArrayList;

/**
 * @author Luke K. Rose <psylr5@nottingham.ac.uk>
 * @version 1.0
 * @since 0.1
 */

public class DatabaseHandler {
    private String user = null;
    private Connection connection = null;
    private final int MAXIMUM_UNCOMMITTED_STATEMENTS = 100000;
    private PrintWriter diskSQL;
    private int uncommittedStatements = 0;
    private boolean WRITE_TO_FILE = false;
    private Statement batchStatement = null;
    private static boolean initialised = false;

    /**
     * Uses a root/admin MySQL/MariaDB account to create the database and initialise all tables, triggers and default values
     *
     * @param adminUser Administrator user account for the MySQL/MariaDB server
     * @param adminPass Administrator password for the MySQL/MariaDB server
     * @throws SQLException Throws SQLException if there is an error with accessing the MySQL/MariaDB database
     */
    static void initialiseDatabase(String adminUser, String adminPass) throws SQLException {
        if (initialised) return;
        Connection conn = DriverManager.getConnection("jdbc:mysql://localhost?useUnicode=true&useJDBCCompliantTimezoneShift=true&useLegacyDatetimeCode=false&serverTimezone=UTC", adminUser, adminPass);

        Main.getController().updateCurrentTask("Initialising Database...", false, false);

        Statement statement = conn.createStatement();
        conn.setAutoCommit(false);

        //Create and use database
        statement.addBatch("CREATE DATABASE IF NOT EXISTS automated_trader;");
        statement.addBatch("USE automated_trader");

        //Primary tables
        statement.addBatch("CREATE TABLE IF NOT EXISTS apimanagement (Name VARCHAR(20) NOT NULL PRIMARY KEY, DailyLimit INT DEFAULT 0, Delay INT UNSIGNED DEFAULT 0);");
        statement.addBatch("CREATE TABLE IF NOT EXISTS indices (Symbol VARCHAR(7) UNIQUE NOT NULL PRIMARY KEY, Name TEXT NOT NULL, Collection VARCHAR(20));");
        statement.addBatch("CREATE TABLE IF NOT EXISTS ngrams (Hash VARCHAR(32) NOT NULL PRIMARY KEY, Gram TEXT NOT NULL, N INT UNSIGNED NOT NULL, Increase INT UNSIGNED DEFAULT 0, Decrease INT UNSIGNED DEFAULT 0, Occurrences INT UNSIGNED DEFAULT 0 NOT NULL, Documents INT UNSIGNED DEFAULT 1 NOT NULL, Blacklisted BIT DEFAULT 0);");
        statement.addBatch("CREATE TABLE IF NOT EXISTS banktransactions (ID INT UNSIGNED AUTO_INCREMENT NOT NULL PRIMARY KEY, TradeDateTime DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, Type VARCHAR(10), Amount DOUBLE SIGNED NOT NULL);");
        statement.addBatch("CREATE TABLE IF NOT EXISTS settings (ID VARCHAR(30) NOT NULL PRIMARY KEY, Value TEXT NOT NULL);");

        //Secondary tables (require foreign keys)
        statement.addBatch("CREATE TABLE IF NOT EXISTS apicalls (Name varchar(20) NOT NULL, Date DATE NOT NULL, Calls INT UNSIGNED DEFAULT 0, PRIMARY KEY (Name, Date), FOREIGN KEY (Name) REFERENCES apimanagement (Name) ON UPDATE CASCADE);");
        statement.addBatch("CREATE TABLE IF NOT EXISTS dailystockprices (Symbol VARCHAR(7) NOT NULL, TradeDate DATE NOT NULL, OpenPrice DOUBLE UNSIGNED NOT NULL, HighPrice DOUBLE UNSIGNED NOT NULL, LowPrice DOUBLE UNSIGNED NOT NULL, ClosePrice DOUBLE UNSIGNED NOT NULL, TradeVolume BIGINT(20) UNSIGNED NOT NULL, PercentChange DOUBLE SIGNED, SmoothedClosePrice DOUBLE UNSIGNED, SMA5 DOUBLE UNSIGNED, SMA10 DOUBLE UNSIGNED, SMA20 DOUBLE UNSIGNED, SMA200 DOUBLE UNSIGNED, EMA5 DOUBLE UNSIGNED, EMA10 DOUBLE UNSIGNED, EMA20 DOUBLE UNSIGNED, EMA200 DOUBLE UNSIGNED, MACD DOUBLE SIGNED, MACDSig DOUBLE SIGNED, MACDHist DOUBLE SIGNED, RSI DOUBLE SIGNED, ADX10 DOUBLE SIGNED, CCI DOUBLE SIGNED, AD DOUBLE SIGNED, OBV DOUBLE SIGNED, StoOscSlowK DOUBLE SIGNED, StoOscSlowD DOUBLE SIGNED, WillR DOUBLE SIGNED, PRIMARY KEY (Symbol,TradeDate), FOREIGN KEY (Symbol) REFERENCES indices(Symbol) ON UPDATE CASCADE ON DELETE CASCADE, INDEX IDX_TradeDate(TradeDate), INDEX IDX_Symbol(Symbol));");
        statement.addBatch("CREATE TABLE IF NOT EXISTS intradaystockprices (Symbol VARCHAR(7) NOT NULL, TradeDateTime DATETIME NOT NULL, OpenPrice DOUBLE UNSIGNED NOT NULL, HighPrice DOUBLE UNSIGNED NOT NULL, LowPrice DOUBLE UNSIGNED NOT NULL, ClosePrice DOUBLE UNSIGNED NOT NULL, TradeVolume BIGINT(20) UNSIGNED NOT NULL, Temporary BIT DEFAULT 0, PRIMARY KEY (Symbol,TradeDateTime), FOREIGN KEY (Symbol) REFERENCES indices(Symbol) ON UPDATE CASCADE ON DELETE CASCADE, INDEX IDX_Symbol(Symbol));");
        statement.addBatch("CREATE TABLE IF NOT EXISTS portfolio (Symbol VARCHAR(7) NOT NULL PRIMARY KEY, Allocation DOUBLE UNSIGNED NOT NULL, Held INT UNSIGNED NOT NULL DEFAULT 0, Investment DOUBLE SIGNED NOT NULL DEFAULT 0, LastUpdated DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, FOREIGN KEY (Symbol) REFERENCES indices(Symbol) ON UPDATE CASCADE ON DELETE CASCADE);");
        statement.addBatch("CREATE TABLE IF NOT EXISTS sentences (Hash VARCHAR(32) NOT NULL PRIMARY KEY, Sentence TEXT NOT NULL, Occurrences INT UNSIGNED DEFAULT 0 NOT NULL, Documents INT UNSIGNED DEFAULT 0 NOT NULL, Blacklisted BIT DEFAULT 0);");
        statement.addBatch("CREATE TABLE IF NOT EXISTS newsarticles (ID INT UNSIGNED AUTO_INCREMENT NOT NULL PRIMARY KEY, Symbol VARCHAR(7) NOT NULL, Headline TEXT NOT NULL, Description TEXT, Content LONGTEXT, Published DATETIME NOT NULL, PublishedDate DATE, URL TEXT, Blacklisted BIT DEFAULT 0 NOT NULL, Redirected BIT DEFAULT 0 NOT NULL, Duplicate BIT DEFAULT 0 NOT NULL, Enumerated BIT DEFAULT 0 NOT NULL, Tokenised BIT DEFAULT 0 NOT NULL, Processed BIT DEFAULT 0 NOT NULL, Mood DOUBLE UNSIGNED DEFAULT 0.5, FOREIGN KEY (Symbol) REFERENCES indices(Symbol) ON UPDATE CASCADE ON DELETE CASCADE, INDEX IDX_Published(Published), INDEX IDX_PublishedDate(PublishedDate), INDEX IDX_Symbol_PublishedDate(Symbol, PublishedDate), INDEX IDX_Symbol_Published(Symbol, Published));");
        statement.addBatch("CREATE TRIGGER date_trigger BEFORE INSERT ON newsarticles FOR EACH ROW SET NEW.PublishedDate = DATE(NEW.Published);");
        statement.addBatch("CREATE TRIGGER blacklisted_insert_trigger BEFORE INSERT ON sentences FOR EACH ROW SET NEW.Blacklisted = NEW.Occurrences >= 5;");
        statement.addBatch("CREATE TRIGGER blacklisted_update_trigger BEFORE UPDATE ON sentences FOR EACH ROW SET NEW.Blacklisted = NEW.Occurrences >= 5;");
        statement.addBatch("CREATE TABLE IF NOT EXISTS tradetransactions (ID INT UNSIGNED AUTO_INCREMENT NOT NULL PRIMARY KEY, TradeDateTime DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, Type VARCHAR(4), Symbol VARCHAR(7) NOT NULL, Volume INT UNSIGNED NOT NULL DEFAULT 0, Price DOUBLE UNSIGNED NOT NULL, Automated BIT NOT NULL DEFAULT 0, FOREIGN KEY (Symbol) REFERENCES indices(Symbol));");
        statement.addBatch("CREATE TABLE IF NOT EXISTS predictors (ID INT UNSIGNED AUTO_INCREMENT NOT NULL PRIMARY KEY, Model TEXT NOT NULL, Type TEXT NOT NULL, Scope TEXT NOT NULL, ModelNumber INT UNSIGNED NOT NULL, Accuracy DOUBLE UNSIGNED NOT NULL, Description TEXT NOT NULL, Filepath TEXT NOT NULL);");
        statement.addBatch("CREATE TABLE IF NOT EXISTS investments (ID INT UNSIGNED AUTO_INCREMENT NOT NULL PRIMARY KEY, Symbol VARCHAR(7) NOT NULL, Amount INT UNSIGNED NOT NULL, EndDate DATE NOT NULL, Period INT UNSIGNED NOT NULL, FOREIGN KEY (Symbol) REFERENCES indices(Symbol) ON UPDATE CASCADE ON DELETE CASCADE)");

        //Insert initial values into relevant databases
        statement.addBatch("INSERT INTO banktransactions(Amount, Type) SELECT 10000, 'DEPOSIT' FROM dual WHERE NOT EXISTS (SELECT 1 FROM banktransactions WHERE Amount = 10000 AND Type='DEPOSIT');");
        statement.addBatch("INSERT INTO apimanagement VALUES ('INTRINIO',500,0),('AlphaVantage',0,1667),('BarChart', 2100,0) ON DUPLICATE KEY UPDATE DailyLimit=VALUES(DailyLimit), Delay=VALUES(Delay);");
        statement.addBatch("INSERT IGNORE INTO settings VALUES('PROFIT_CUTOFF', '11000'), ('LOSS_CUTOFF','9000'), ('BARCHART_API_KEY', 'NULL'), ('INTRINIO_API_KEY', 'NULL'), ('INTRINIO_API_USER', 'NULL'), ('ALPHAVANTAGE_API_KEY','NULL'), ('PREDICTION_MODE','SINGLE'), ('NEWS_ARTICLE_PARALLEL_DOWNLOAD', '1')");

        //Create users
        statement.addBatch("CREATE USER IF NOT EXISTS 'Agent'@'localhost' IDENTIFIED BY '0Y5q0m28pSB9jj2O';");
        statement.addBatch("CREATE USER IF NOT EXISTS 'NaturalLanguageProcessor'@'localhost' IDENTIFIED BY 'p1pONM8zhI6GgCfy';");
        statement.addBatch("CREATE USER IF NOT EXISTS 'TechnicalAnalyser'@'localhost' IDENTIFIED BY 'n6qvdUkFOoFCxPq5';");
        statement.addBatch("CREATE USER IF NOT EXISTS 'NewsDownloader'@'localhost' IDENTIFIED BY 'wu0Ni6YF3yLTVp2A';");
        statement.addBatch("CREATE USER IF NOT EXISTS 'StockQuoteDownloader'@'localhost' IDENTIFIED BY 'j2wbvx19Gg1Be22J';");
        statement.addBatch("CREATE USER IF NOT EXISTS 'PortfolioManager'@'localhost' IDENTIFIED BY 'mAjwa22NdsrRihi4';");
        statement.addBatch("CREATE USER IF NOT EXISTS 'StockPredictor'@'localhost' IDENTIFIED BY 'wfN1XLoW810diEhR';");

        //Assign permissions to users
        statement.addBatch("GRANT ALL ON automated_trader.* TO 'Agent'@'localhost';");
        statement.addBatch("GRANT ALL ON automated_trader.* TO 'NaturalLanguageProcessor'@'localhost';");
        statement.addBatch("GRANT ALL ON automated_trader.* TO 'TechnicalAnalyser'@'localhost';");
        statement.addBatch("GRANT ALL ON automated_trader.* TO 'NewsDownloader'@'localhost';");
        statement.addBatch("GRANT ALL ON automated_trader.* TO 'StockQuoteDownloader'@'localhost';");
        statement.addBatch("GRANT ALL ON automated_trader.* TO 'PortfolioManager'@'localhost';");
        statement.addBatch("GRANT ALL ON automated_trader.* TO 'StockPredictor'@'localhost';");

        statement.executeBatch();
        conn.setAutoCommit(false);

        initialised = true;
        conn.close();
    }

    /**
     * Opens a new file in which to buffer SQL commands (prevents loss of data if the application is terminated unexpectedly)
     *
     * @throws IOException Throws IOException if the file cannot be created or accessed
     */
    private void initialiseDiskSQL() throws IOException {
        diskSQL = new PrintWriter(System.getProperty("user.dir") + "/res/" + user + ".sql");
    }

    /**
     * Toggles whether or not the SQL should be written to the database or buffered in an SQL file
     *
     * @param wtf Write To File - True if all SQL commands should be sent to an intermediary file before being sent to the database, False otherwise
     */
    void setWriteToFile(boolean wtf) {
        WRITE_TO_FILE = wtf;
    }

    /**
     * Sends the contents of the SQL buffer file to the database and clears the file if necessary
     *
     * @param flush True if the file should be cleared after database import, False otherwise (Setting as False is useful when debugging errors with a given SQL file)
     * @throws SQLException Throws SQLException if there is an error with accessing the MySQL/MariaDB database
     * @throws IOException  Throws IOException if the file cannot be created or accessed
     */
    public void sendSQLFileToDatabase(boolean flush) throws SQLException, IOException {
        File file = new File(System.getProperty("user.dir") + "/res/" + user + ".sql");

        if (!file.exists()) return;

        Main.getController().updateCurrentTask("Flushing '" + user + "' SQL file to database...", false, false);

        setAutoCommit(false);

        if (diskSQL != null) diskSQL.close();

        BufferedReader reader = new BufferedReader(new FileReader(file));
        String line;

        while ((line = reader.readLine()) != null) addBatchCommand(line);

        reader.close();
        executeBatch();
        setAutoCommit(true);

        if (flush) initialiseDiskSQL();
    }

    /**
     * Commits all uncommitted SQL commands
     */
    public void commit() {
        if (uncommittedStatements == 0) return;

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

    /**
     * Sets whether or not the connection should automatically commit commands or buffer them as a transaction
     *
     * @param autoCommit True if commands should be sent straight to the database, False if they should be buffered
     * @throws SQLException Throws SQLException if there is an error with accessing the MySQL/MariaDB database
     */
    public void setAutoCommit(boolean autoCommit) throws SQLException {
        connection.setAutoCommit(autoCommit);
    }

    /**
     * Adds a command to the batch command buffer (speeds up SQL execution)
     *
     * @param command SQL command to add to the buffer
     * @throws SQLException Throws SQLException if there is an error with accessing the MySQL/MariaDB database
     */
    public void addBatchCommand(String command) throws SQLException {
        if (batchStatement == null) batchStatement = connection.createStatement();

        batchStatement.addBatch(command);

        if (uncommittedStatements++ >= MAXIMUM_UNCOMMITTED_STATEMENTS) {
            boolean previousSetting = connection.getAutoCommit();
            executeBatch();
            setAutoCommit(previousSetting);
        }
    }

    /**
     * Sends all buffered commands to the database as one large command (faster execution than single commands)
     *
     * @throws SQLException Throws SQLException if there is an error with accessing the MySQL/MariaDB database
     */
    public void executeBatch() throws SQLException {
        if (batchStatement == null) return;
        Main.getController().updateCurrentTask("Executing batch command...", false, false);
        batchStatement.executeBatch();
        connection.commit();
        batchStatement.clearBatch();
        uncommittedStatements = 0;
        Main.getController().updateCurrentTask("Batch command committed successfully!", false, false);
    }

    /**
     * Executes a given SQL command by either executing immediately, buffering to a batch command or sending to an SQL file
     *
     * @param command SQL command to execute
     * @throws SQLException Throws SQLException if there is an error with accessing the MySQL/MariaDB database
     */
    public void executeCommand(String command) throws SQLException {
        Statement statement = connection.createStatement();

        if (connection.getAutoCommit() && !WRITE_TO_FILE) {
            statement.execute(command);
            statement.close();
        } else if (!connection.getAutoCommit() && !WRITE_TO_FILE)
            addBatchCommand(command);
        else
            diskSQL.println(command);

        if (!connection.getAutoCommit() && !WRITE_TO_FILE) {
            uncommittedStatements++;
            if (uncommittedStatements >= MAXIMUM_UNCOMMITTED_STATEMENTS)
                executeBatch();
        }
    }

    /**
     * Executes a given SQL query and returns the result
     * @param command SQL query to execute
     * @return A list of records that are returned from the database based on the given query
     * @throws SQLException Throws SQLException if there is an error with accessing the MySQL/MariaDB database
     */
    public ArrayList<String> executeQuery(String command) throws SQLException{
        Statement query = connection.createStatement();

        ArrayList<String> tempArr = new ArrayList<>();

        ResultSet tempRs = query.executeQuery(command);
        ResultSetMetaData rsmd = tempRs.getMetaData();

        while (tempRs.next()) {
            StringBuilder temp = new StringBuilder(tempRs.getString(1)); //Index starts at 1, not 0
            for (int i = 2; i <= rsmd.getColumnCount(); i++) temp.append(",").append(tempRs.getString(i));

            tempArr.add(temp.toString());
        }

        query.close();
        return tempArr;
    }

    /**
     * Closes the database connection
     * @throws SQLException Throws SQLException if there is an error with accessing the MySQL/MariaDB database
     */
    public void close() throws SQLException {
        connection.close();
        Main.getController().updateCurrentTask("Closed database connection for '" + user + "'", false, false);
    }

    /**
     * Connects to the database using the given username and password
     *
     * @param username Username to login to the MySQL/MariaDB server with
     * @param password Password to login to the MySQL/MariaDB server with
     * @throws SQLException Throws SQLException if there is an error with accessing the MySQL/MariaDB database
     * @throws IOException  Throws IOException if the request fails due to server unavailability or connection refusal
     */
    void init(String username, String password) throws SQLException, IOException {
        user = username;

        try {
            connection = DriverManager.getConnection("jdbc:mysql://localhost?useUnicode=true&useJDBCCompliantTimezoneShift=true&useLegacyDatetimeCode=false&serverTimezone=UTC", username, password);
        } catch (SQLException e) {
            System.err.println(e.getMessage());
            if (e.getErrorCode() == 1045) {
                System.out.println("Please create an account on the MySQL Server with these credentials:");
                System.out.println("Username: " + username);
                System.out.println("Password: " + password);
            }

            System.exit(-1);
        }

        if (connection == null)
            Main.getController().updateCurrentTask("Failed to setDatabaseHandler database connection!", true, true);
        else {
            executeCommand("USE automated_trader");
            Main.getController().updateCurrentTask("Initialised database connection for '" + username + "'", false, false);
            sendSQLFileToDatabase(true);
            initialiseDiskSQL();
        }
    }
}