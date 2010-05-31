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

package com.cloudera.sqoop.tool;

import java.sql.SQLException;
import java.util.Arrays;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.util.StringUtils;
import org.apache.log4j.Category;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import com.cloudera.sqoop.ConnFactory;
import com.cloudera.sqoop.Sqoop;
import com.cloudera.sqoop.SqoopOptions;
import com.cloudera.sqoop.SqoopOptions.InvalidOptionsException;
import com.cloudera.sqoop.cli.RelatedOptions;
import com.cloudera.sqoop.cli.ToolOptions;
import com.cloudera.sqoop.lib.DelimiterSet;
import com.cloudera.sqoop.manager.ConnManager;
import com.cloudera.sqoop.shims.ShimLoader;

/**
 * Layer on top of SqoopTool that provides some basic common code
 * that most SqoopTool implementations will use.
 *
 * Subclasses should call init() at the top of their run() method,
 * and call destroy() at the end in a finally block.
 */
public abstract class BaseSqoopTool extends SqoopTool {

  public static final Log LOG = LogFactory.getLog(
      BaseSqoopTool.class.getName());

  public static final String HELP_STR = "\nTry --help for usage instructions.";

  // Here are all the arguments that are used by the standard sqoop tools.
  // Their names are recorded here so that tools can share them and their
  // use consistently. The argument parser applies the leading '--' to each
  // string.
  public static final String CONNECT_STRING_ARG = "connect";
  public static final String DRIVER_ARG = "driver";
  public static final String USERNAME_ARG = "username";
  public static final String PASSWORD_ARG = "password";
  public static final String PASSWORD_PROMPT_ARG = "P";
  public static final String DIRECT_ARG = "direct";
  public static final String TABLE_ARG = "table";
  public static final String COLUMNS_ARG = "columns";
  public static final String SPLIT_BY_ARG = "split-by";
  public static final String WHERE_ARG = "where";
  public static final String HADOOP_HOME_ARG = "hadoop-home";
  public static final String HIVE_HOME_ARG = "hive-home";
  public static final String WAREHOUSE_DIR_ARG = "warehouse-dir";
  public static final String TARGET_DIR_ARG = "target-dir";
  public static final String APPEND_ARG = "append";  
  
  public static final String FMT_SEQUENCEFILE_ARG = "as-sequencefile";
  public static final String FMT_TEXTFILE_ARG = "as-textfile";
  public static final String HIVE_IMPORT_ARG = "hive-import";
  public static final String HIVE_TABLE_ARG = "hive-table";
  public static final String HIVE_OVERWRITE_ARG = "hive-overwrite";
  public static final String NUM_MAPPERS_ARG = "num-mappers";
  public static final String NUM_MAPPERS_SHORT_ARG = "m";
  public static final String COMPRESS_ARG = "compress";
  public static final String COMPRESS_SHORT_ARG = "z";
  public static final String DIRECT_SPLIT_SIZE_ARG = "direct-split-size";
  public static final String INLINE_LOB_LIMIT_ARG = "inline-lob-limit";
  public static final String EXPORT_PATH_ARG = "export-dir";
  public static final String FIELDS_TERMINATED_BY_ARG = "fields-terminated-by";
  public static final String LINES_TERMINATED_BY_ARG = "lines-terminated-by";
  public static final String OPTIONALLY_ENCLOSED_BY_ARG =
      "optionally-enclosed-by";
  public static final String ENCLOSED_BY_ARG = "enclosed-by";
  public static final String ESCAPED_BY_ARG = "escaped-by";
  public static final String MYSQL_DELIMITERS_ARG = "mysql-delimiters";
  public static final String INPUT_FIELDS_TERMINATED_BY_ARG =
      "input-fields-terminated-by";
  public static final String INPUT_LINES_TERMINATED_BY_ARG =
      "input-lines-terminated-by";
  public static final String INPUT_OPTIONALLY_ENCLOSED_BY_ARG =
      "input-optionally-enclosed-by";
  public static final String INPUT_ENCLOSED_BY_ARG = "input-enclosed-by";
  public static final String INPUT_ESCAPED_BY_ARG = "input-escaped-by";
  public static final String CODE_OUT_DIR_ARG = "outdir";
  public static final String BIN_OUT_DIR_ARG = "bindir";
  public static final String PACKAGE_NAME_ARG = "package-name";
  public static final String CLASS_NAME_ARG = "class-name";
  public static final String JAR_FILE_NAME_ARG = "jar-file";
  public static final String DEBUG_SQL_ARG = "query";
  public static final String DEBUG_SQL_SHORT_ARG = "e";
  public static final String VERBOSE_ARG = "verbose";
  public static final String HELP_ARG = "help";


  public BaseSqoopTool() {
  }

  public BaseSqoopTool(String toolName) {
    super(toolName);
  }

  protected ConnManager manager;

  public ConnManager getManager() {
    return manager;
  }

  protected void setManager(ConnManager mgr) {
    this.manager = mgr;
  }

  /**
   * Should be called at the beginning of the run() method to initialize
   * the connection manager, etc. If this succeeds (returns true), it should
   * be paired with a call to destroy().
   * @return true on success, false on failure.
   */
  protected boolean init(SqoopOptions sqoopOpts) {

    // Make sure shim jar is classloaded early.
    ShimLoader.getHadoopShim(sqoopOpts.getConf());

    // Get the connection to the database.
    try {
      this.manager = new ConnFactory(sqoopOpts.getConf()).getManager(sqoopOpts);
      return true;
    } catch (Exception e) {
      LOG.error("Got error creating database manager: "
          + StringUtils.stringifyException(e));
      if (System.getProperty(Sqoop.SQOOP_RETHROW_PROPERTY) != null) {
        throw new RuntimeException(e);
      }
    }

    return false;
  }

  /**
   * Should be called in a 'finally' block at the end of the run() method.
   */
  protected void destroy(SqoopOptions sqoopOpts) {
    if (null != manager) {
      try {
        manager.close();
      } catch (SQLException sqlE) {
        LOG.warn("Error while closing connection: " + sqlE);
      }
    }
  }

  /**
   * Examines a subset of the arrray presented, and determines if it
   * contains any non-empty arguments. If so, logs the arguments
   * and returns true.
   *
   * @param argv an array of strings to check.
   * @param offset the first element of the array to check
   * @param len the number of elements to check
   * @return true if there are any non-null, non-empty argument strings
   * present.
   */
  protected boolean hasUnrecognizedArgs(String [] argv, int offset, int len) {
    if (argv == null) {
      return false;
    }

    boolean unrecognized = false;
    boolean printedBanner = false;
    for (int i = offset; i < Math.min(argv.length, offset + len); i++) {
      if (argv[i] != null && argv[i].length() > 0) {
        if (!printedBanner) {
          LOG.error("Error parsing arguments for " + getToolName() + ":");
          printedBanner = true;
        }
        LOG.error("Unrecognized argument: " + argv[i]); 
        unrecognized = true;
      }
    }

    return unrecognized;
  }

  protected boolean hasUnrecognizedArgs(String [] argv) {
    if (null == argv) {
      return false;
    }
    return hasUnrecognizedArgs(argv, 0, argv.length);
  }


  /**
   * If argv contains an entry "--", return an array containing all elements
   * after the "--" separator. Otherwise, return null.
   * @param argv a set of arguments to scan for the subcommand arguments.
   */
  protected String [] getSubcommandArgs(String [] argv) {
    if (null == argv) {
      return null;
    }

    for (int i = 0; i < argv.length; i++) {
      if (argv[i].equals("--")) {
        return Arrays.copyOfRange(argv, i + 1, argv.length);
      }
    }

    return null;
  }

  /**
   * @return RelatedOptions used by most/all Sqoop tools.
   */
  protected RelatedOptions getCommonOptions() {
    // Connection args (common)
    RelatedOptions commonOpts = new RelatedOptions("Common arguments");
    commonOpts.addOption(OptionBuilder.withArgName("jdbc-uri")
        .hasArg().withDescription("Specify JDBC connect string")
        .withLongOpt(CONNECT_STRING_ARG)
        .create());
    commonOpts.addOption(OptionBuilder.withArgName("class-name")
        .hasArg().withDescription("Manually specify JDBC driver class to use")
        .withLongOpt(DRIVER_ARG)
        .create());
    commonOpts.addOption(OptionBuilder.withArgName("username")
        .hasArg().withDescription("Set authentication username")
        .withLongOpt(USERNAME_ARG)
        .create());
    commonOpts.addOption(OptionBuilder.withArgName("password")
        .hasArg().withDescription("Set authentication password")
        .withLongOpt(PASSWORD_ARG)
        .create());
    commonOpts.addOption(OptionBuilder
        .withDescription("Read password from console")
        .create(PASSWORD_PROMPT_ARG));

    commonOpts.addOption(OptionBuilder.withArgName("dir")
        .hasArg().withDescription("Override $HADOOP_HOME")
        .withLongOpt(HADOOP_HOME_ARG)
        .create());

    // misc (common)
    commonOpts.addOption(OptionBuilder
        .withDescription("Print more information while working")
        .withLongOpt(VERBOSE_ARG)
        .create());
    commonOpts.addOption(OptionBuilder
        .withDescription("Print usage instructions")
        .withLongOpt(HELP_ARG)
        .create());

    return commonOpts;
  }

  /**
   * @param explicitHiveImport true if the user has an explicit --hive-import
   * available, or false if this is implied by the tool.
   * @return options governing interaction with Hive
   */
  protected RelatedOptions getHiveOptions(boolean explicitHiveImport) {
    RelatedOptions hiveOpts = new RelatedOptions("Hive arguments");
    if (explicitHiveImport) {
      hiveOpts.addOption(OptionBuilder
          .withDescription("Import tables into Hive "
          + "(Uses Hive's default delimiters if none are set.)")
          .withLongOpt(HIVE_IMPORT_ARG)
          .create());
    }

    hiveOpts.addOption(OptionBuilder.withArgName("dir")
        .hasArg().withDescription("Override $HIVE_HOME")
        .withLongOpt(HIVE_HOME_ARG)
        .create());
    hiveOpts.addOption(OptionBuilder
        .withDescription("Overwrite existing data in the Hive table")
        .withLongOpt(HIVE_OVERWRITE_ARG)
        .create());
    hiveOpts.addOption(OptionBuilder.withArgName("table-name")
        .hasArg()
        .withDescription("Sets the table name to use when importing to hive")
        .withLongOpt(HIVE_TABLE_ARG)
        .create());

    return hiveOpts;
  }

  /**
   * @return options governing output format delimiters
   */
  protected RelatedOptions getOutputFormatOptions() {
    RelatedOptions formatOpts = new RelatedOptions(
        "Output line formatting arguments");
    formatOpts.addOption(OptionBuilder.withArgName("char")
        .hasArg()
        .withDescription("Sets the field separator character")
        .withLongOpt(FIELDS_TERMINATED_BY_ARG)
        .create());
    formatOpts.addOption(OptionBuilder.withArgName("char")
        .hasArg()
        .withDescription("Sets the end-of-line character")
        .withLongOpt(LINES_TERMINATED_BY_ARG)
        .create());
    formatOpts.addOption(OptionBuilder.withArgName("char")
        .hasArg()
        .withDescription("Sets a field enclosing character")
        .withLongOpt(OPTIONALLY_ENCLOSED_BY_ARG)
        .create());
    formatOpts.addOption(OptionBuilder.withArgName("char")
        .hasArg()
        .withDescription("Sets a required field enclosing character")
        .withLongOpt(ENCLOSED_BY_ARG)
        .create());
    formatOpts.addOption(OptionBuilder.withArgName("char")
        .hasArg()
        .withDescription("Sets the escape character")
        .withLongOpt(ESCAPED_BY_ARG)
        .create());
    formatOpts.addOption(OptionBuilder
        .withDescription("Uses MySQL's default delimiter set: "
        + "fields: ,  lines: \\n  escaped-by: \\  optionally-enclosed-by: '")
        .withLongOpt(MYSQL_DELIMITERS_ARG)
        .create());

    return formatOpts;
  }

  /**
   * @return options governing input format delimiters.
   */
  protected RelatedOptions getInputFormatOptions() {
    RelatedOptions inputFormatOpts =
        new RelatedOptions("Input parsing arguments");
    inputFormatOpts.addOption(OptionBuilder.withArgName("char")
        .hasArg()
        .withDescription("Sets the input field separator")
        .withLongOpt(INPUT_FIELDS_TERMINATED_BY_ARG)
        .create());
    inputFormatOpts.addOption(OptionBuilder.withArgName("char")
        .hasArg()
        .withDescription("Sets the input end-of-line char")
        .withLongOpt(INPUT_LINES_TERMINATED_BY_ARG)
        .create());
    inputFormatOpts.addOption(OptionBuilder.withArgName("char")
        .hasArg()
        .withDescription("Sets a field enclosing character")
        .withLongOpt(INPUT_OPTIONALLY_ENCLOSED_BY_ARG)
        .create());
    inputFormatOpts.addOption(OptionBuilder.withArgName("char")
        .hasArg()
        .withDescription("Sets a required field encloser")
        .withLongOpt(INPUT_ENCLOSED_BY_ARG)
        .create());
    inputFormatOpts.addOption(OptionBuilder.withArgName("char")
        .hasArg()
        .withDescription("Sets the input escape character")
        .withLongOpt(INPUT_ESCAPED_BY_ARG)
        .create());

    return inputFormatOpts;
  }

  /**
   * @param multiTable true if these options will be used for bulk code-gen.
   * @return options related to code generation.
   */
  protected RelatedOptions getCodeGenOpts(boolean multiTable) {
    RelatedOptions codeGenOpts =
        new RelatedOptions("Code generation arguments");
    codeGenOpts.addOption(OptionBuilder.withArgName("dir")
        .hasArg()
        .withDescription("Output directory for generated code")
        .withLongOpt(CODE_OUT_DIR_ARG)
        .create());
    codeGenOpts.addOption(OptionBuilder.withArgName("dir")
        .hasArg()
        .withDescription("Output directory for compiled objects")
        .withLongOpt(BIN_OUT_DIR_ARG)
        .create());
    codeGenOpts.addOption(OptionBuilder.withArgName("name")
        .hasArg()
        .withDescription("Put auto-generated classes in this package")
        .withLongOpt(PACKAGE_NAME_ARG)
        .create());
    if (!multiTable) {
      codeGenOpts.addOption(OptionBuilder.withArgName("name")
          .hasArg()
          .withDescription("Sets the generated class name. "
          + "This overrides --" + PACKAGE_NAME_ARG + ". When combined "
          + "with --" + JAR_FILE_NAME_ARG + ", sets the input class.")
          .withLongOpt(CLASS_NAME_ARG)
          .create());
    }
    return codeGenOpts;
  }

  
  /**
   * Apply common command-line to the state.
   */
  protected void applyCommonOptions(CommandLine in, SqoopOptions out)
      throws InvalidOptionsException {

    // common options.
    if (in.hasOption(VERBOSE_ARG)) {
      // Immediately switch into DEBUG logging.
      Category sqoopLogger = Logger.getLogger(
          Sqoop.class.getName()).getParent();
      sqoopLogger.setLevel(Level.DEBUG);
      LOG.debug("Enabled debug logging.");
    }

    if (in.hasOption(HELP_ARG)) {
      ToolOptions toolOpts = new ToolOptions();
      configureOptions(toolOpts);
      printHelp(toolOpts);
      throw new InvalidOptionsException("");
    }

    if (in.hasOption(CONNECT_STRING_ARG)) {
      out.setConnectString(in.getOptionValue(CONNECT_STRING_ARG));
    }

    if (in.hasOption(DRIVER_ARG)) {
      out.setDriverClassName(in.getOptionValue(DRIVER_ARG));
    }

    if (in.hasOption(USERNAME_ARG)) {
      out.setUsername(in.getOptionValue(USERNAME_ARG));
      if (null == out.getPassword()) {
        // Set password to empty if the username is set first,
        // to ensure that they're either both null or neither is.
        out.setPassword("");
      }
    }

    if (in.hasOption(PASSWORD_ARG)) {
      LOG.warn("Setting your password on the command-line is insecure. "
          + "Consider using -" + PASSWORD_PROMPT_ARG + " instead.");
      out.setPassword(in.getOptionValue(PASSWORD_ARG));
    }

    if (in.hasOption(PASSWORD_PROMPT_ARG)) {
      out.setPasswordFromConsole();
    }

    if (in.hasOption(HADOOP_HOME_ARG)) {
      out.setHadoopHome(in.getOptionValue(HADOOP_HOME_ARG));
    }

  }

  protected void applyHiveOptions(CommandLine in, SqoopOptions out)
      throws InvalidOptionsException {

    if (in.hasOption(HIVE_HOME_ARG)) {
      out.setHiveHome(in.getOptionValue(HIVE_HOME_ARG));
    }

    if (in.hasOption(HIVE_IMPORT_ARG)) {
      out.setHiveImport(true);
    }

    if (in.hasOption(HIVE_OVERWRITE_ARG)) {
      out.setOverwriteHiveTable(true);
    }

    if (in.hasOption(HIVE_TABLE_ARG)) {
      out.setHiveTableName(in.getOptionValue(HIVE_TABLE_ARG));
    }
  }

  protected void applyOutputFormatOptions(CommandLine in, SqoopOptions out)
      throws InvalidOptionsException {
    if (in.hasOption(FIELDS_TERMINATED_BY_ARG)) {
      out.setFieldsTerminatedBy(SqoopOptions.toChar(
          in.getOptionValue(FIELDS_TERMINATED_BY_ARG)));
      out.setExplicitDelims(true);
    }

    if (in.hasOption(LINES_TERMINATED_BY_ARG)) {
      out.setLinesTerminatedBy(SqoopOptions.toChar(
          in.getOptionValue(LINES_TERMINATED_BY_ARG)));
      out.setExplicitDelims(true);
    }

    if (in.hasOption(OPTIONALLY_ENCLOSED_BY_ARG)) {
      out.setEnclosedBy(SqoopOptions.toChar(
          in.getOptionValue(OPTIONALLY_ENCLOSED_BY_ARG)));
      out.setOutputEncloseRequired(false);
      out.setExplicitDelims(true);
    }

    if (in.hasOption(ENCLOSED_BY_ARG)) {
      out.setEnclosedBy(SqoopOptions.toChar(
          in.getOptionValue(ENCLOSED_BY_ARG)));
      out.setOutputEncloseRequired(true);
      out.setExplicitDelims(true);
    }

    if (in.hasOption(ESCAPED_BY_ARG)) {
      out.setEscapedBy(SqoopOptions.toChar(
          in.getOptionValue(ESCAPED_BY_ARG)));
      out.setExplicitDelims(true);
    }
    
    if (in.hasOption(MYSQL_DELIMITERS_ARG)) {
      out.setOutputEncloseRequired(false);
      out.setFieldsTerminatedBy(',');
      out.setLinesTerminatedBy('\n');
      out.setEscapedBy('\\');
      out.setEnclosedBy('\'');
      out.setExplicitDelims(true);
    }
  }

  protected void applyInputFormatOptions(CommandLine in, SqoopOptions out)
      throws InvalidOptionsException {
    if (in.hasOption(INPUT_FIELDS_TERMINATED_BY_ARG)) {
      out.setInputFieldsTerminatedBy(SqoopOptions.toChar(
          in.getOptionValue(INPUT_FIELDS_TERMINATED_BY_ARG)));
    }

    if (in.hasOption(INPUT_LINES_TERMINATED_BY_ARG)) {
      out.setInputLinesTerminatedBy(SqoopOptions.toChar(
          in.getOptionValue(INPUT_LINES_TERMINATED_BY_ARG)));
    }

    if (in.hasOption(INPUT_OPTIONALLY_ENCLOSED_BY_ARG)) {
      out.setInputEnclosedBy(SqoopOptions.toChar(
          in.getOptionValue(INPUT_OPTIONALLY_ENCLOSED_BY_ARG)));
      out.setInputEncloseRequired(false);
    }

    if (in.hasOption(INPUT_ENCLOSED_BY_ARG)) {
      out.setInputEnclosedBy(SqoopOptions.toChar(
          in.getOptionValue(INPUT_ENCLOSED_BY_ARG)));
      out.setInputEncloseRequired(true);
    }

    if (in.hasOption(INPUT_ESCAPED_BY_ARG)) {
      out.setInputEscapedBy(SqoopOptions.toChar(
          in.getOptionValue(INPUT_ESCAPED_BY_ARG)));
    }
  }

  protected void applyCodeGenOptions(CommandLine in, SqoopOptions out,
      boolean multiTable) throws InvalidOptionsException {
    if (in.hasOption(CODE_OUT_DIR_ARG)) {
      out.setCodeOutputDir(in.getOptionValue(CODE_OUT_DIR_ARG));
    }

    if (in.hasOption(BIN_OUT_DIR_ARG)) {
      out.setJarOutputDir(in.getOptionValue(BIN_OUT_DIR_ARG));
    }

    if (in.hasOption(PACKAGE_NAME_ARG)) {
      out.setPackageName(in.getOptionValue(PACKAGE_NAME_ARG));
    }

    if (!multiTable && in.hasOption(CLASS_NAME_ARG)) {
      out.setClassName(in.getOptionValue(CLASS_NAME_ARG));
    }
  }

  protected void validateCommonOptions(SqoopOptions options)
      throws InvalidOptionsException {
    if (options.getConnectString() == null) {
      throw new InvalidOptionsException(
          "Error: Required argument --connect is missing."
          + HELP_STR);
    }
  }

  protected void validateCodeGenOptions(SqoopOptions options)
      throws InvalidOptionsException {
    if (options.getClassName() != null && options.getPackageName() != null) {
      throw new InvalidOptionsException(
          "--class-name overrides --package-name. You cannot use both."
          + HELP_STR);
    }
  }

  protected void validateOutputFormatOptions(SqoopOptions options)
      throws InvalidOptionsException {
    if (options.doHiveImport()) {
      if (!options.explicitDelims()) {
        // user hasn't manually specified delimiters, and wants to import
        // straight to Hive. Use Hive-style delimiters.
        LOG.info("Using Hive-specific delimiters for output. You can override");
        LOG.info("delimiters with --fields-terminated-by, etc.");
        options.setOutputDelimiters(DelimiterSet.HIVE_DELIMITERS);
      }

      if (options.getOutputEscapedBy() != DelimiterSet.NULL_CHAR) {
        LOG.warn("Hive does not support escape characters in fields;");
        LOG.warn("parse errors in Hive may result from using --escaped-by.");
      }

      if (options.getOutputEnclosedBy() != DelimiterSet.NULL_CHAR) {
        LOG.warn("Hive does not support quoted strings; parse errors");
        LOG.warn("in Hive may result from using --enclosed-by.");
      }
    }
  }

  protected void validateHiveOptions(SqoopOptions options) {
    // Empty; this method is present to maintain API consistency, and
    // is reserved for future constraints on Hive options.
  }

}

