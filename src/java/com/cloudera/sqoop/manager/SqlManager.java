/**
 * Licensed to Cloudera, Inc. under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  Cloudera, Inc. licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.cloudera.sqoop.manager;

import com.cloudera.sqoop.SqoopOptions;
import com.cloudera.sqoop.hive.HiveTypes;
import com.cloudera.sqoop.lib.BlobRef;
import com.cloudera.sqoop.lib.ClobRef;
import com.cloudera.sqoop.mapreduce.DataDrivenImportJob;
import com.cloudera.sqoop.mapreduce.JdbcExportJob;
import com.cloudera.sqoop.util.ExportException;
import com.cloudera.sqoop.util.ImportException;
import com.cloudera.sqoop.util.ResultSetPrinter;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.util.StringUtils;

/**
 * ConnManager implementation for generic SQL-compliant database.
 * This is an abstract class; it requires a database-specific
 * ConnManager implementation to actually create the connection.
 */
public abstract class SqlManager extends ConnManager {

  public static final Log LOG = LogFactory.getLog(SqlManager.class.getName());

  protected SqoopOptions options;
  private Statement lastStatement;

  /**
   * Constructs the SqlManager.
   * @param opts the SqoopOptions describing the user's requested action.
   */
  public SqlManager(final SqoopOptions opts) {
    this.options = opts;
  }

  /**
   * @return the SQL query to use in getColumnNames() in case this logic must
   * be tuned per-database, but the main extraction loop is still inheritable.
   */
  protected String getColNamesQuery(String tableName) {
    // adding where clause to prevent loading a big table
    return "SELECT t.* FROM " + escapeTableName(tableName) + " AS t WHERE 1=0";
  }

  @Override
  public String[] getColumnNames(String tableName) {
    String stmt = getColNamesQuery(tableName);

    ResultSet results;
    try {
      results = execute(stmt);
    } catch (SQLException sqlE) {
      LOG.error("Error executing statement: " + sqlE.toString());
      release();
      return null;
    }

    try {
      int cols = results.getMetaData().getColumnCount();
      ArrayList<String> columns = new ArrayList<String>();
      ResultSetMetaData metadata = results.getMetaData();
      for (int i = 1; i < cols + 1; i++) {
        String colName = metadata.getColumnName(i);
        if (colName == null || colName.equals("")) {
          colName = metadata.getColumnLabel(i);
        }
        columns.add(colName);
      }
      return columns.toArray(new String[0]);
    } catch (SQLException sqlException) {
      LOG.error("Error reading from database: " + sqlException.toString());
      return null;
    } finally {
      try {
        results.close();
        getConnection().commit();
      } catch (SQLException sqlE) {
        LOG.warn("SQLException closing ResultSet: " + sqlE.toString());
      }

      release();
    }
  }

  /**
   * @return the SQL query to use in getColumnTypes() in case this logic must
   * be tuned per-database, but the main extraction loop is still inheritable.
   */
  protected String getColTypesQuery(String tableName) {
    return getColNamesQuery(tableName);
  }
  
  @Override
  public Map<String, Integer> getColumnTypes(String tableName) {
    String stmt = getColTypesQuery(tableName);

    ResultSet results;
    try {
      results = execute(stmt);
    } catch (SQLException sqlE) {
      LOG.error("Error executing statement: " + sqlE.toString());
      release();
      return null;
    }

    try {
      Map<String, Integer> colTypes = new HashMap<String, Integer>();

      int cols = results.getMetaData().getColumnCount();
      ResultSetMetaData metadata = results.getMetaData();
      for (int i = 1; i < cols + 1; i++) {
        int typeId = metadata.getColumnType(i);
        String colName = metadata.getColumnName(i);
        if (colName == null || colName.equals("")) {
          colName = metadata.getColumnLabel(i);
        }

        colTypes.put(colName, Integer.valueOf(typeId));
      }

      return colTypes;
    } catch (SQLException sqlException) {
      LOG.error("Error reading from database: " + sqlException.toString());
      return null;
    } finally {
      try {
        results.close();
        getConnection().commit();
      } catch (SQLException sqlE) {
        LOG.warn("SQLException closing ResultSet: " + sqlE.toString());
      }

      release();
    }
  }

  @Override
  public ResultSet readTable(String tableName, String[] columns)
      throws SQLException {
    if (columns == null) {
      columns = getColumnNames(tableName);
    }

    StringBuilder sb = new StringBuilder();
    sb.append("SELECT ");
    boolean first = true;
    for (String col : columns) {
      if (!first) {
        sb.append(", ");
      }
      sb.append(escapeColName(col));
      first = false;
    }
    sb.append(" FROM ");
    sb.append(escapeTableName(tableName));
    sb.append(" AS ");   // needed for hsqldb; doesn't hurt anyone else.
    sb.append(escapeTableName(tableName));

    String sqlCmd = sb.toString();
    LOG.debug("Reading table with command: " + sqlCmd);
    return execute(sqlCmd);
  }

  @Override
  public String[] listDatabases() {
    // TODO(aaron): Implement this!
    LOG.error("Generic SqlManager.listDatabases() not implemented.");
    return null;
  }

  @Override
  public String[] listTables() {
    ResultSet results = null;
    String [] tableTypes = {"TABLE"};
    try {
      try {
        DatabaseMetaData metaData = this.getConnection().getMetaData();
        results = metaData.getTables(null, null, null, tableTypes);
      } catch (SQLException sqlException) {
        LOG.error("Error reading database metadata: "
            + sqlException.toString());
        return null;
      }

      if (null == results) {
        return null;
      }

      try {
        ArrayList<String> tables = new ArrayList<String>();
        while (results.next()) {
          String tableName = results.getString("TABLE_NAME");
          tables.add(tableName);
        }

        return tables.toArray(new String[0]);
      } catch (SQLException sqlException) {
        LOG.error("Error reading from database: " + sqlException.toString());
        return null;
      }
    } finally {
      if (null != results) {
        try {
          results.close();
          getConnection().commit();
        } catch (SQLException sqlE) {
          LOG.warn("Exception closing ResultSet: " + sqlE.toString());
        }
      }
    }
  }

  @Override
  public String getPrimaryKey(String tableName) {
    try {
      DatabaseMetaData metaData = this.getConnection().getMetaData();
      ResultSet results = metaData.getPrimaryKeys(null, null, tableName);
      if (null == results) {
        return null;
      }
      
      try {
        if (results.next()) {
          return results.getString("COLUMN_NAME");
        } else {
          return null;
        }
      } finally {
        results.close();
        getConnection().commit();
      }
    } catch (SQLException sqlException) {
      LOG.error("Error reading primary key metadata: "
          + sqlException.toString());
      return null;
    }
  }

  /**
   * Retrieve the actual connection from the outer ConnManager.
   */
  public abstract Connection getConnection() throws SQLException;

  /**
   * Determine what column to use to split the table.
   * @param opts the SqoopOptions controlling this import.
   * @param tableName the table to import.
   * @return the splitting column, if one is set or inferrable, or null
   * otherwise.
   */
  protected String getSplitColumn(SqoopOptions opts, String tableName) {
    String splitCol = opts.getSplitByCol();
    if (null == splitCol) {
      // If the user didn't specify a splitting column, try to infer one.
      splitCol = getPrimaryKey(tableName);
    }

    return splitCol;
  }

  /**
   * Default implementation of importTable() is to launch a MapReduce job
   * via DataDrivenImportJob to read the table with DataDrivenDBInputFormat.
   */
  public void importTable(ImportJobContext context)
      throws IOException, ImportException {
    String tableName = context.getTableName();
    String jarFile = context.getJarFile();
    SqoopOptions opts = context.getOptions();

    DataDrivenImportJob importer =
    	new DataDrivenImportJob(opts, context.getInputFormat(), context);

    String splitCol = getSplitColumn(opts, tableName);
    if (null == splitCol && opts.getNumMappers() > 1) {
      // Can't infer a primary key.
      throw new ImportException("No primary key could be found for table "
          + tableName + ". Please specify one with --split-by or perform "
          + "a sequential import with '-m 1'.");
    }

    importer.runImport(tableName, jarFile, splitCol, opts.getConf());
  }

  /**
   * Executes an arbitrary SQL statement.
   * @param stmt The SQL statement to execute
   * @return A ResultSet encapsulating the results or null on error
   */
  protected ResultSet execute(String stmt, Object... args) throws SQLException {
    // Release any previously-open statement.
    release();

    PreparedStatement statement = null;
    statement = this.getConnection().prepareStatement(stmt,
        ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
    this.lastStatement = statement;
    if (null != args) {
      for (int i = 0; i < args.length; i++) {
        statement.setObject(i + 1, args[i]);
      }
    }

    LOG.info("Executing SQL statement: " + stmt);
    return statement.executeQuery();
  }

  /**
   * Resolve a database-specific type to the Java type that should contain it.
   * @param sqlType
   * @return the name of a Java type to hold the sql datatype, or null if none.
   */
  public String toJavaType(int sqlType) {
    // Mappings taken from:
    // http://java.sun.com/j2se/1.3/docs/guide/jdbc/getstart/mapping.html
    if (sqlType == Types.INTEGER) {
      return "Integer";
    } else if (sqlType == Types.VARCHAR) {
      return "String";
    } else if (sqlType == Types.CHAR) {
      return "String";
    } else if (sqlType == Types.LONGVARCHAR) {
      return "String";
    } else if (sqlType == Types.NUMERIC) {
      return "java.math.BigDecimal";
    } else if (sqlType == Types.DECIMAL) {
      return "java.math.BigDecimal";
    } else if (sqlType == Types.BIT) {
      return "Boolean";
    } else if (sqlType == Types.BOOLEAN) {
      return "Boolean";
    } else if (sqlType == Types.TINYINT) {
      return "Integer";
    } else if (sqlType == Types.SMALLINT) {
      return "Integer";
    } else if (sqlType == Types.BIGINT) {
      return "Long";
    } else if (sqlType == Types.REAL) {
      return "Float";
    } else if (sqlType == Types.FLOAT) {
      return "Double";
    } else if (sqlType == Types.DOUBLE) {
      return "Double";
    } else if (sqlType == Types.DATE) {
      return "java.sql.Date";
    } else if (sqlType == Types.TIME) {
      return "java.sql.Time";
    } else if (sqlType == Types.TIMESTAMP) {
      return "java.sql.Timestamp";
    } else if (sqlType == Types.BINARY
        || sqlType == Types.VARBINARY) {
      return BytesWritable.class.getName();
    } else if (sqlType == Types.CLOB) {
      return ClobRef.class.getName();
    } else if (sqlType == Types.BLOB
        || sqlType == Types.LONGVARBINARY) {
      return BlobRef.class.getName();
    } else {
      // TODO(aaron): Support DISTINCT, ARRAY, STRUCT, REF, JAVA_OBJECT.
      // Return null indicating database-specific manager should return a
      // java data type if it can find one for any nonstandard type.
      return null;
    }
  }

  /**
   * Resolve a database-specific type to Hive data type.
   * @param sqlType     sql type
   * @return            hive type
   */
  public String toHiveType(int sqlType) {
    return HiveTypes.toHiveType(sqlType);
  }

  public void close() throws SQLException {
    release();
  }

  /**
   * Prints the contents of a ResultSet to the specified PrintWriter.
   * The ResultSet is closed at the end of this method.
   * @param results the ResultSet to print.
   * @param pw the location to print the data to.
   */
  protected void formatAndPrintResultSet(ResultSet results, PrintWriter pw) {
    try {
      try {
        int cols = results.getMetaData().getColumnCount();
        pw.println("Got " + cols + " columns back");
        if (cols > 0) {
          ResultSetMetaData rsmd = results.getMetaData();
          String schema = rsmd.getSchemaName(1);
          String table = rsmd.getTableName(1);
          if (null != schema) {
            pw.println("Schema: " + schema);
          }

          if (null != table) {
            pw.println("Table: " + table);
          }
        }
      } catch (SQLException sqlE) {
        LOG.error("SQLException reading result metadata: " + sqlE.toString());
      }

      try {
        new ResultSetPrinter().printResultSet(pw, results);
      } catch (IOException ioe) {
        LOG.error("IOException writing results: " + ioe.toString());
        return;
      }
    } finally {
      try {
        results.close();
        getConnection().commit();
      } catch (SQLException sqlE) {
        LOG.warn("SQLException closing ResultSet: " + sqlE.toString());
      }

      release();
    }
  }

  /**
   * Poor man's SQL query interface; used for debugging.
   * @param s the SQL statement to execute.
   */
  public void execAndPrint(String s) {
    ResultSet results = null;
    try {
      results = execute(s);
    } catch (SQLException sqlE) {
      LOG.error("Error executing statement: "
          + StringUtils.stringifyException(sqlE));
      release();
      return;
    }

    PrintWriter pw = new PrintWriter(System.out, true);
    try {
      formatAndPrintResultSet(results, pw);
    } finally {
      pw.close();
    }
  }

  /**
   * Create a connection to the database; usually used only from within
   * getConnection(), which enforces a singleton guarantee around the
   * Connection object.
   */
  protected Connection makeConnection() throws SQLException {

    Connection connection;
    String driverClass = getDriverClass();

    try {
      Class.forName(driverClass);
    } catch (ClassNotFoundException cnfe) {
      throw new RuntimeException("Could not load db driver class: "
          + driverClass);
    }

    String username = options.getUsername();
    String password = options.getPassword();
    if (null == username) {
      connection = DriverManager.getConnection(options.getConnectString());
    } else {
      connection = DriverManager.getConnection(options.getConnectString(),
          username, password);
    }

    // We only use this for metadata queries. Loosest semantics are okay.
    connection.setTransactionIsolation(Connection.TRANSACTION_READ_UNCOMMITTED);
    connection.setAutoCommit(false);

    return connection;
  }

  /**
   * Export data stored in HDFS into a table in a database.
   */
  public void exportTable(ExportJobContext context)
      throws IOException, ExportException {
    JdbcExportJob exportJob = new JdbcExportJob(context);
    exportJob.runExport();
  }

  public void release() {
    if (null != this.lastStatement) {
      try {
        this.lastStatement.close();
      } catch (SQLException e) {
        LOG.warn("Exception closing executed Statement: " + e);
      }

      this.lastStatement = null;
    }
  }
}
