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

import java.sql.Connection;
import java.sql.SQLException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.util.StringUtils;
import org.junit.After;
import org.junit.Before;

import com.cloudera.sqoop.SqoopOptions;
import com.cloudera.sqoop.TestExport;

/**
 * Test the OracleManager implementation's exportJob() functionality.
 * This tests the OracleExportOutputFormat (which subclasses
 * ExportOutputFormat with Oracle-specific SQL statements).
 */
public class OracleExportTest extends TestExport {

  public static final Log LOG = LogFactory.getLog(
      OracleExportTest.class.getName());

  static final String TABLE_PREFIX = "EXPORT_ORACLE_";

  // instance variables populated during setUp, used during tests.
  private OracleManager manager;
  private Connection conn;

  @Override
  protected Connection getConnection() {
    return conn;
  }

  // Oracle allows multi-row inserts (with its own syntax).
  @Override
  protected int getMaxRowsPerStatement() {
    return 1000;
  }

  @Override
  protected boolean useHsqldbTestServer() {
    return false;
  }

  @Override
  protected String getConnectString() {
    return OracleUtils.CONNECT_STRING;
  }

  @Override
  protected String getTablePrefix() {
    return TABLE_PREFIX;
  }

  @Override
  protected String getDropTableStatement(String tableName) {
    return OracleUtils.getDropTableStatement(tableName);
  }

  @Before
  public void setUp() {
    super.setUp();

    SqoopOptions options = new SqoopOptions(OracleUtils.CONNECT_STRING,
        getTableName());
    OracleUtils.setOracleAuth(options);
    this.manager = new OracleManager(options);
    try {
      this.conn = manager.getConnection();
      this.conn.setAutoCommit(false);
    } catch (SQLException sqlE) {
      LOG.error(StringUtils.stringifyException(sqlE));
      fail("Failed with sql exception in setup: " + sqlE);
    }
  }

  @After
  public void tearDown() {
    super.tearDown();

    if (null != this.conn) {
      try {
        this.conn.close();
      } catch (SQLException sqlE) {
        LOG.error("Got SQLException closing conn: " + sqlE.toString());
      }
    }

    if (null != manager) {
      try {
        manager.close();
        manager = null;
      } catch (SQLException sqlE) {
        LOG.error("Got SQLException: " + sqlE.toString());
        fail("Got SQLException: " + sqlE.toString());
      }
    }
  }

  @Override
  protected String [] getCodeGenArgv(String... extraArgs) {
    String [] moreArgs = new String[extraArgs.length + 4];
    int i = 0;
    for (i = 0; i < extraArgs.length; i++) {
      moreArgs[i] = extraArgs[i];
    }

    // Add username and password args.
    moreArgs[i++] = "--username";
    moreArgs[i++] = OracleUtils.ORACLE_USER_NAME;
    moreArgs[i++] = "--password";
    moreArgs[i++] = OracleUtils.ORACLE_USER_PASS;

    return super.getCodeGenArgv(moreArgs);
  }

  @Override
  protected String [] getArgv(boolean includeHadoopFlags,
      int rowsPerStatement, int statementsPerTx, String... additionalArgv) {

    String [] subArgv = newStrArray(additionalArgv,
        "--username", OracleUtils.ORACLE_USER_NAME,
        "--password", OracleUtils.ORACLE_USER_PASS);
    return super.getArgv(includeHadoopFlags, rowsPerStatement,
        statementsPerTx, subArgv);
  }

  @Override
  protected ColumnGenerator getDateColumnGenerator() {
    // Return a TIMESTAMP generator that has increasing date values.
    // We currently do not support export of DATE columns since
    // Oracle informs us that they are actually TIMESTAMP; this messes
    // up Sqoop's parsing of values as we have no way of knowing they're
    // really supposed to be dates based on the Oracle Jdbc Metadata.
    return new ColumnGenerator() {
      public String getExportText(int rowNum) {
        int day = rowNum + 1;
        return "2009-10-" + pad(day) + " 00:00:00.0";
      }
      public String getVerifyText(int rowNum) {
        int day = rowNum + 1;
        return "2009-10-" + day + " 0:0:0. 0";
      }
      public String getType() {
        return "TIMESTAMP"; // TODO: Support DATE more intelligently.
      }
    };
  }

  @Override
  protected ColumnGenerator getTimeColumnGenerator() {
    // Return a TIMESTAMP generator that has increasing time values.
    // We currently do not support the export of DATE columns with
    // only a time component set (because Oracle reports these column
    // types to Sqoop as TIMESTAMP, and we parse the user's text
    // incorrectly based on this result).
    return new ColumnGenerator() {
      public String getExportText(int rowNum) {
        return "1970-01-01 10:01:" + pad(rowNum) + ".0";
      }
      public String getVerifyText(int rowNum) {
        return "1970-1-1 10:1:" + rowNum + ". 0";
      }
      public String getType() {
        return "TIMESTAMP";
      }
    };
  }

  @Override
  protected String getBigIntType() {
    // Oracle stores everything in NUMERIC columns.
    return "NUMERIC(12,0)";
  }
}
