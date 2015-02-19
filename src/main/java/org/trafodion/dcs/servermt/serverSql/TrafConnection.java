/**
 *(C) Copyright 2015 Hewlett-Packard Development Company, L.P.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.trafodion.dcs.servermt.serverSql;

import java.sql.*;
import java.net.*;
import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.channels.spi.*;
import java.util.*;
import java.util.concurrent.*;
import java.sql.*;

import org.trafodion.dcs.Constants;
import org.trafodion.dcs.util.*;
import org.trafodion.dcs.servermt.serverDriverInputOutput.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class TrafConnection {
    private static  final Log LOG = LogFactory.getLog(TrafConnection.class);
    private Properties prop = null;
    private Connection conn = null;
    private boolean isClosed = true;
    
    private String datasource = "";
    private String catalog = "";
    private String schema = "";
    private String location = "";
    private String userRole = "";

    private short accessMode = 0;
    private short autoCommit = 0;
    private int queryTimeoutSec = 0;
    private int idleTimeoutSec = 0;
    private int loginTimeoutSec = 0;
    private short txnIsolationLevel = 0;
    private short rowSetSize = 0;

    private int diagnosticFlag = 0;
    private int processId = 0;

    private String computerName = "";
    private String windowText = "";

    private int ctxACP = 0;
    private int ctxDataLang = 0;
    private int ctxErrorLang = 0;
    private short ctxCtrlInferNXHAR = 0;
    
    private short cpuToUse = 0;
    private short cpuToUseEnd = 0;

    private String connectOptions = "";
    
    private VersionList clientVersionList = null;
    
    private int dialogueId = 0;
    private long contextOptions1 = 0L;
    private long contextOptions2 = 0L;

    private String sessionName = "";
    private String clientUserName = "";
//----------------------------------------------------------------
    private int batchBinding = 500;
    // character set information
    private int isoMapping = 15;
    private int termCharset = 15;
    private boolean enforceISO = false;

//
//--------------------------------------------------------------
//    
    private ConcurrentHashMap<String, TrafStatement> statements = new ConcurrentHashMap<String, TrafStatement>(); //keeping statements
    
    public TrafConnection(){
        init();
    }
    public TrafConnection(ConnectionContext cc) throws SQLException, ClassNotFoundException {
        init();
        datasource = cc.getDatasource();
        catalog = cc.getCatalog();
        schema = cc.getSchema();
        location = cc.getLocation();
        userRole = cc.getUserRole();
        accessMode = cc.getAccessMode();
        autoCommit = cc.getAutoCommit();
        queryTimeoutSec = cc.getQueryTimeoutSec();
        idleTimeoutSec = cc.getIdleTimeoutSec();
        loginTimeoutSec = cc.getLoginTimeoutSec();
        txnIsolationLevel = cc.getTxnIsolationLevel();
        rowSetSize = cc.getRowSetSize();
        diagnosticFlag = cc.getDiagnosticFlag();
        processId = cc.getProcessId();
        computerName = cc.getComputerName();
        windowText = cc.getWindowText();
        ctxACP = cc.getCtxACP();
        ctxDataLang = cc.getCtxDataLang();
        ctxErrorLang = cc.getCtxErrorLang();
        ctxCtrlInferNXHAR = cc.getCtxCtrlInferNXHAR();
        cpuToUse = cc.getCpuToUse();
        cpuToUseEnd = cc.getCpuToUseEnd();
        connectOptions = cc.getConnectOptions();
        clientVersionList = cc.getClientVersionList();
        dialogueId = cc.getDialogueId();
        contextOptions1 = cc.getContextOptions1();
        contextOptions2 = cc.getContextOptions2();
        sessionName = cc.getSessionName();
        clientUserName = cc.getClientUserName();
/*--------------------------------------------------------------------
T2 Driver properties
    catalog
    schema
    batchBinding
    language
    mploc
    sql_nowait
    Spjrs
    stmtatomicity
    transactionMode
    ISO88591
    contBatchOnError
    maxIdleTime
    maxPoolSize
    minPoolSize
    maxStatements
    initialPoolSize
    blobTableName
    clobTableName
    enableMFC
    compileModuleLocation
    traceFlag
    traceFile
    externalCallHandler
    externalCallPrefix
    queryExecutionTime
    T2QueryExecuteLogFile
    enableLog
    idMapFile
*/
        prop = new Properties(); 
        prop.put("catalog", catalog);
        prop.put("schema", schema);
//      prop.put("traceFlag", "3");
//      prop.put("traceFile", "/opt/home/zomanski/mt/T2trace");
//        prop.put("batchBinding", batchBinding);
        if(LOG.isDebugEnabled()){
            LOG.debug(" catalog :" + catalog + " schema :" + schema);
            String[] envs = {"LD_LIBRARY_PATH", "LD_PRELOAD"};
            for (String env: envs) {
                 String value = System.getenv(env);
                 if (value != null) {
                     LOG.debug( env + " = " + value);
                 } else {
                     LOG.debug( env + " is not assigned.");
                 }
             }
        }
        Class.forName(Constants.T2_DRIVER_CLASS_NAME);
        conn = DriverManager.getConnection(Constants.T2_DRIVER_URL, prop);
        isClosed = false;

// isoMapping, termCharset and enforceISO must be set by properties?
        if (isoMapping == SqlUtils.getCharsetValue("ISO8859_1")) {
            setTerminalCharset(SqlUtils.getCharsetValue("ISO8859_1"));
            this.ctxDataLang = 0;
            this.ctxErrorLang = 0;
        } else {
            setTerminalCharset(SqlUtils.getCharsetValue("UTF-8"));
        }
    }
    void init(){
        reset();
    }
    void reset(){
        prop = null;
        conn = null;
    
        datasource = "";
        catalog = "";
        schema = "";
        location = "";
        userRole = "";
        accessMode = 0;
        autoCommit = 0;
        queryTimeoutSec = 0;
        idleTimeoutSec = 0;
        loginTimeoutSec = 0;
        txnIsolationLevel = 0;
        rowSetSize = 0;
        diagnosticFlag = 0;
        processId = 0;
        computerName = "";
        windowText = "";
        ctxACP = 0;
        ctxDataLang = 0;
        ctxErrorLang = 0;
        ctxCtrlInferNXHAR = 0;
        cpuToUse = 0;
        cpuToUseEnd = 0;
        connectOptions = "";
        VersionList clientVersionList = new VersionList();
        dialogueId = 0;
        contextOptions1 = 0L;
        contextOptions2 = 0L;
        sessionName = "";
        clientUserName = "";
    }
    public void closeTConnection() {
        TrafStatement tstmt;
        
        Iterator<String> keySetIterator = statements.keySet().iterator();

        while(keySetIterator.hasNext()){
          String key = keySetIterator.next();
          tstmt = statements.get(key);
          tstmt.closeTStatement();
        }
        statements.clear();
        try {
            if(isClosed == false){
                isClosed = true;
                conn.close();
             }
        } catch (SQLException sql){}
        reset();
    }
    public TrafStatement createTrafStatement(String cursorName, boolean isResultSet) throws SQLException {
        TrafStatement trafStatement = null;
        
        if (statements.containsKey(cursorName) == false){
            trafStatement = new TrafStatement(conn, null);
            trafStatement.setIsResultSet(isResultSet);
            statements.put(cursorName, trafStatement);
        }
        else{
            trafStatement = getTrafStatement(cursorName);
            trafStatement.setStatement(conn, null);
            trafStatement.setIsResultSet(isResultSet);
        }
        return trafStatement;
    }
    public TrafStatement prepareTrafStatement(String cursorName, String sqlString, boolean isResultSet) throws SQLException {
        TrafStatement trafStatement = null;
        
        if (statements.containsKey(cursorName) == false){
            trafStatement = new TrafStatement(conn,sqlString);
            trafStatement.setIsResultSet(isResultSet);
            statements.put(cursorName, trafStatement);
        }
        else{
            trafStatement = getTrafStatement(cursorName);
            trafStatement.setStatement(conn, sqlString);
            trafStatement.setIsResultSet(isResultSet);
        }
        return trafStatement;
    }

    public TrafStatement getTrafStatement(String cursorName) throws SQLException {
        TrafStatement trafStatement = null;
        trafStatement = statements.get(cursorName);
        if (trafStatement == null) throw new SQLException("getTrafStatement for cursor :" +  cursorName + " returns null");
        return trafStatement;
    }
    public void setDatasource(String datasource) {
        this.datasource = datasource;
    }
    public void setCatalog(String catalog) {
        this.catalog = catalog;
    }
    public void setSchema(String schema) {
        this.schema = schema;
    }
    public void setLocation(String location) {
        this.location = location;
    }
    public void setUserRole(String userRole) {
        this.userRole = userRole;
    }
    public void setAccessMode(short accessMode) {
        this.accessMode = accessMode;
    }
    public void setAutoCommit(short autoCommit) {
        this.autoCommit = autoCommit;
    }
    public void setQueryTimeoutSec(int queryTimeoutSec) {
        this.queryTimeoutSec = queryTimeoutSec;
    }
    public void setIdleTimeoutSec(int idleTimeoutSec) {
        this.idleTimeoutSec = idleTimeoutSec;
    }
    public void setLoginTimeoutSec(int loginTimeoutSec) {
        this.loginTimeoutSec = loginTimeoutSec;
    }
    public void setTxnIsolationLevel(short txnIsolationLevel) {
        this.txnIsolationLevel = txnIsolationLevel;
    }
    public void setRowSetSize(short rowSetSize) {
        this.rowSetSize = rowSetSize;
    }
    public void setDiagnosticFlag(int diagnosticFlag) {
        this.diagnosticFlag = diagnosticFlag;
    }
    public void setProcessId(int processId) {
        this.processId = processId;
    }
    public void setComputerName(String computerName) {
        this.computerName = computerName;
    }
    public void setWindowText(String windowText) {
        this.windowText = windowText;
    }
    public void setCtxACP(int ctxACP) {
        this.ctxACP = ctxACP;
    }
    public void setCtxDataLang(int ctxDataLang) {
        this.ctxDataLang = ctxDataLang;
    }
    public void setCtxErrorLang(int ctxErrorLang) {
        this.ctxErrorLang = ctxErrorLang;
    }
    public void setCtxCtrlInferNXHAR(short ctxCtrlInferNXHAR) {
        this.ctxCtrlInferNXHAR = ctxCtrlInferNXHAR;
    }
    public void setCpuToUse(short cpuToUse) {
        this.cpuToUse = cpuToUse;
    }
    public void setCpuToUseEnd(short cpuToUseEnd) {
        this.cpuToUseEnd = cpuToUseEnd;
    }
    public void setConnectOptions(String connectOptions) {
        this.connectOptions = connectOptions;
    }
    public void setClientVersionList(VersionList clientVersionList) {
        this.clientVersionList = clientVersionList;
    }
    public void setDialogueId(int dialogueId) {
        this.dialogueId = dialogueId;
    }
    public void setContextOptions1(long contextOptions1) {
        this.contextOptions1 = contextOptions1;
    }
    public void setContextOptions2(long contextOptions2) {
        this.contextOptions2 = contextOptions2;
    }
    public void setSessionName(String sessionName) {
        this.sessionName = sessionName;
    }
    public void setClientUserName(String clientUserName) {
        this.clientUserName = clientUserName;
    }
    public void setConnection(Connection conn){
        this.conn = conn;
    }
    public void setISOMapping(int isoMapping){
        this.isoMapping = isoMapping;
    }
    public void setTerminalCharset(int termCharset){
        this.termCharset = termCharset;
    }
    public void setEnforceISO( boolean enforceISO){
        this.enforceISO = enforceISO;
    }

//=============================================================================
    public String getDatasource() {
        return datasource;
    }
    public String getCatalog() {
        return catalog;
    }
    public String getSchema() {
        return schema;
    }
    public String getLocation() {
        return location;
    }
    public String getUserRole() {
        return userRole;
    }
    public short getAccessMode() {
        return accessMode;
    }
    public short getAutoCommit() {
        return autoCommit;
    }
    public int getQueryTimeoutSec() {
        return queryTimeoutSec;
    }
    public int getIdleTimeoutSec() {
        return idleTimeoutSec;
    }
    public int getLoginTimeoutSec() {
        return loginTimeoutSec;
    }
    public short getTxnIsolationLevel() {
        return txnIsolationLevel;
    }
    public short getRowSetSize() {
        return rowSetSize;
    }
    public int getDiagnosticFlag() {
        return diagnosticFlag;
    }
    public int getProcessId() {
        return processId;
    }
    public String getComputerName() {
        return computerName;
    }
    public String getWindowText() {
        return windowText;
    }
    public int getCtxACP() {
        return ctxACP;
    }
    public int getCtxDataLang() {
        return ctxDataLang;
    }
    public int getCtxErrorLang() {
        return ctxErrorLang;
    }
    public short getCtxCtrlInferNXHAR() {
        return ctxCtrlInferNXHAR;
    }
    public short getCpuToUse() {
        return cpuToUse;
    }
    public short getCpuToUseEnd() {
        return cpuToUseEnd;
    }
    public String getConnectOptions() {
        return connectOptions;
    }
    public VersionList getClientVersionList() {
        return clientVersionList;
    }
    public int getDialogueId() {
        return dialogueId;
    }
    public long getContextOptions1() {
        return contextOptions1;
    }
    public long getContextOptions2() {
        return contextOptions2;
    }
    public String getSessionName() {
        return sessionName;
    }
    public String getClientUserName() {
        return clientUserName;
    }
    public Connection getConnection(){
        return conn;
    }
    public int getISOMapping(int isoMapping){
        return isoMapping;
    }
    public int getTerminalCharset(){
        return termCharset;
    }
    public boolean setEnforceISO(){
        return enforceISO;
    }
}
