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
package org.trafodion.dcs.servermt.serverHandler;

import java.sql.*;
import java.net.*;
import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.channels.spi.*;
import java.util.*;

import org.trafodion.jdbc.t2.*;

import org.trafodion.dcs.Constants;
import org.trafodion.dcs.util.*;
import org.trafodion.dcs.servermt.ServerConstants;
import org.trafodion.dcs.servermt.ServerUtils;
import org.trafodion.dcs.servermt.serverDriverInputOutput.*;
import org.trafodion.dcs.servermt.serverSql.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class ServerApiSqlPrepare {
    private static final int odbc_SQLSvc_Prepare_ParamError_exn_ = 1;
    private static final int odbc_SQLSvc_Prepare_InvalidConnection_exn_ = 2;
    private static final int odbc_SQLSvc_Prepare_SQLError_exn_ = 3;
    private static final int odbc_SQLSvc_Prepare_SQLStillExecuting_exn_ = 4;
    private static final int odbc_SQLSvc_Prepare_SQLQueryCancelled_exn_ = 5;
    private static final int odbc_SQLSvc_Prepare_TransactionError_exn_ = 6;
    
    private static  final Log LOG = LogFactory.getLog(ServerApiSqlPrepare.class);
    private int instance;
    private int serverThread;
    private String serverWorkerName;
    private ClientData clientData;
//----------------------------------------------
    private TrafConnection trafConn;
    private TrafStatement trafStmt;
    private PreparedStatement pstmt;

    private int dialogueId;
    private int sqlAsyncEnable;
    private int queryTimeout;
    private short stmtType;
    private int sqlStmtType;
    private String stmtLabel;
    private String cursorName;
    private String moduleName;
    private long moduleTimestamp;
    private String sqlString;
    private String stmtOptions;
    private String stmtExplainLabel;
    private int maxRowsetSize;
    private String txId;
//-----------------------------------------------    
    private int returnCode;
    private SQLWarningOrErrorList errorList;
    private int sqlQueryType;
    private int stmtHandle;
    private int estimatedCost;
//-----------for params -------------------------
    private ParameterMetaData pmd = null;
    private SQLMXParameterMetaData spmtd = null;
//-----------for input params -------------------
    private int inpNumberParams;
    private Descriptor2List inpDescList;
//-----------for output params ------------------    
    private int outNumberParams;
    private Descriptor2List outDescList;
//-----------for result set --------------------
    private ResultSetMetaData rsmd = null;
    private SQLMXResultSetMetaData strsmd;
//-------------T2 desc fields-------------------
    private int        sqlCharset_;
    private int        odbcCharset_;
    private int        sqlDataType_;
    private int        dataType_;
    private short    sqlPrecision_;
    private short    sqlDatetimeCode_;
    private int        sqlOctetLength_;
    private int        isNullable_;
    private String    name_;
    private int        scale_;
    private int        precision_;
    private boolean    isSigned_;
    private boolean    isCurrency_;
    private boolean    isCaseSensitive_;
    private String     catalogName_;
    private String    schemaName_;
    private String    tableName_;
    private int        fsDataType_;
    private int        intLeadPrec_;
    private int        paramMode_;
    private int        paramIndex_;
    private int        paramPos_;

    private int        odbcPrecision_;
    private int        maxLen_;

    private int     displaySize_;
    private String  label_;

    ServerApiSqlPrepare(int instance, int serverThread) {  
        this.instance = instance;
        this.serverThread = serverThread;
        serverWorkerName = ServerConstants.SERVER_WORKER_NAME + "_" + instance + "_" + serverThread;
    }
    void init(){
        reset();
    }
    void reset(){
        dialogueId = 0;
        sqlAsyncEnable = 0;
        queryTimeout = 0;
        stmtType = 0;
        sqlStmtType = 0;
        stmtLabel = "";
        cursorName = "";
        moduleName = "";
        moduleTimestamp = 0;
        sqlString = "";
        stmtOptions = "";
        stmtExplainLabel = "";
        maxRowsetSize = 32000;
        String txId = "";
    
        returnCode = ServerConstants.SQL_SUCCESS;
        errorList = null;
        sqlQueryType = 0;
        stmtHandle = 0;
        estimatedCost = 0;
        inpNumberParams = 0;
        inpDescList = null;
        outNumberParams = 0;
        outDescList = null;
        
        pmd = null;
        spmtd = null;
        rsmd = null;
        strsmd = null;;
    }
    ClientData processApi(ClientData clientData) {  
        this.clientData = clientData;
        init();
//        
// ==============process input ByteBuffer===========================
// 
        ByteBuffer bbHeader = clientData.bbHeader;
        ByteBuffer bbBody = clientData.bbBody;
        Header hdr = clientData.hdr;

        bbHeader.flip();
        bbBody.flip();
        
        try {

            hdr.extractFromByteArray(bbHeader);
            
            dialogueId =  bbBody.getInt();
            sqlAsyncEnable =  bbBody.getInt();
            queryTimeout =  bbBody.getInt();
            stmtType =  bbBody.getShort();
            sqlStmtType =  bbBody.getInt();
            stmtLabel = ByteBufferUtils.extractStringWithCharset(bbBody);
            cursorName = ByteBufferUtils.extractStringWithCharset(bbBody);
            moduleName = ByteBufferUtils.extractStringWithCharset(bbBody);
            if (moduleName != null && moduleName.length() > 0) {
                moduleTimestamp = bbBody.getLong();
            }
            sqlString = ByteBufferUtils.extractStringWithCharset(bbBody);
            stmtOptions = ByteBufferUtils.extractString(bbBody);
            stmtExplainLabel = ByteBufferUtils.extractString(bbBody);
            maxRowsetSize =  bbBody.getInt();
            txId = ByteBufferUtils.extractString(bbBody);

            if(LOG.isDebugEnabled()){
                LOG.debug(serverWorkerName + ". dialogueId :" + dialogueId);
                LOG.debug(serverWorkerName + ". sqlAsyncEnable :" + sqlAsyncEnable);
                LOG.debug(serverWorkerName + ". queryTimeout :" + queryTimeout);
                LOG.debug(serverWorkerName + ". stmtType :" + stmtType);
                LOG.debug(serverWorkerName + ". sqlStmtType :" + sqlStmtType);
                LOG.debug(serverWorkerName + ". stmtLabel :" + stmtLabel);
                LOG.debug(serverWorkerName + ". cursorName :" + cursorName);
                LOG.debug(serverWorkerName + ". moduleName :" + moduleName);
                LOG.debug(serverWorkerName + ". sqlString :" + sqlString);
                LOG.debug(serverWorkerName + ". stmtOptions :" + stmtOptions);
                LOG.debug(serverWorkerName + ". stmtExplainLabel :" + stmtExplainLabel);
                LOG.debug(serverWorkerName + ". maxRowsetSize :" + maxRowsetSize);
                LOG.debug(serverWorkerName + ". txId :" + txId);
            }
            if (dialogueId < 1 ) {
                throw new SQLException(serverWorkerName + ". Wrong dialogueId :" + dialogueId);
            }
            if (dialogueId != clientData.getDialogueId() ) {
                throw new SQLException(serverWorkerName + ". Wrong dialogueId sent by the Client [sent/expected] : [" + dialogueId + "/" + clientData.getDialogueId() + "]");
            }
            boolean isResultSet = false;
            
            switch (sqlStmtType){
                case ServerConstants.TYPE_SELECT:
                    isResultSet = true;
                    break;
                case ServerConstants.TYPE_UPDATE:
                case ServerConstants.TYPE_DELETE:
                case ServerConstants.TYPE_INSERT:
                case ServerConstants.TYPE_INSERT_PARAM:
                case ServerConstants.TYPE_EXPLAIN:
                case ServerConstants.TYPE_CREATE:
                case ServerConstants.TYPE_GRANT:
                case ServerConstants.TYPE_DROP:
                case ServerConstants.TYPE_CALL:
                case ServerConstants.TYPE_CONTROL:
                default:
            }
//=====================Process ServerApiSqlPrepare===========================
            try {

                trafConn = clientData.getTrafConnection();
                trafStmt = trafConn.prepareTrafStatement(stmtLabel, sqlString, isResultSet);
                pstmt = (PreparedStatement)trafStmt.getStatement();
                pstmt.setFetchSize(125);                //????????????????????????????????
//-------------------------------------------------------------
                if(isResultSet == true){
                    rsmd = pstmt.getMetaData();
                    outNumberParams = rsmd.getColumnCount();
                }
//-------------------------------------------------------------                
                pmd = pstmt.getParameterMetaData();
                if(pmd != null)
                    inpNumberParams = pmd.getParameterCount();
                
                if(LOG.isDebugEnabled()){
                    LOG.debug(serverWorkerName + ".outNumberParams :" + outNumberParams);
                    LOG.debug(serverWorkerName + ".inpNumberParams :" + inpNumberParams);
                }
                if (outNumberParams > 0){
                    strsmd = (SQLMXResultSetMetaData)rsmd;
                    outDescList = new Descriptor2List(outNumberParams);
                    
                    for (int column = 1; column <= outNumberParams; column++){
                        sqlCharset_ = strsmd.getSqlCharset(column);
                        odbcCharset_ = strsmd.getOdbcCharset(column);
                        sqlDataType_ = strsmd.getSqlDataType(column);
                        dataType_ = strsmd.getDataType(column);
                        sqlPrecision_ = strsmd.getSqlPrecision(column);
                        sqlDatetimeCode_ = strsmd.getSqlDatetimeCode(column);
                        sqlOctetLength_ = strsmd.getSqlOctetLength(column);
                        isNullable_ = strsmd.getIsNullable(column);
                        name_ = strsmd.getName(column);
                        scale_ = strsmd.getScale(column);
                        precision_ = strsmd.getPrecision(column);
                        isSigned_ = strsmd.getIsSigned(column);
                        isCurrency_ = strsmd.getIsCurrency(column);
                        isCaseSensitive_ = strsmd.getIsCaseSensitive(column);
                        catalogName_ = strsmd.getCatalogName(column);
                        schemaName_ = strsmd.getSchemaName(column);
                        tableName_ = strsmd.getTableName(column);
                        fsDataType_ = strsmd.getFsDataType(column);
                        intLeadPrec_ = strsmd.getIntLeadPrec(column);
                        paramMode_ = strsmd.getMode(column);
                        paramIndex_ = strsmd.getIndex(column);
                        paramPos_ = strsmd.getPos(column);
                        
                        odbcPrecision_ = strsmd.getOdbcPrecision(column);
                        maxLen_ = strsmd.getMaxLen(column);
                        
                        displaySize_ = strsmd.getDisplaySize(column);
                        label_ = strsmd.getLabel(column);
 
                        Descriptor2 outDesc = new Descriptor2(sqlCharset_,odbcCharset_,sqlDataType_,dataType_,sqlPrecision_,sqlDatetimeCode_,
                            sqlOctetLength_,isNullable_,name_,scale_,precision_,isSigned_,
                            isCurrency_,isCaseSensitive_,catalogName_,schemaName_,tableName_,
                            fsDataType_,intLeadPrec_,paramMode_,paramIndex_,paramPos_,
                            odbcPrecision_,maxLen_,displaySize_,label_);
                        outDescList.addDescriptor(column,outDesc);
                    }
                    if(LOG.isDebugEnabled()){
                        for (int column = 1; column <= outNumberParams; column++){
                            Descriptor2 dsc = outDescList.getDescriptors2()[column-1];
                            LOG.debug(serverWorkerName + ".Column :" + column);
                            LOG.debug(serverWorkerName + ".out_noNullValue" + column + " :" + dsc.getNoNullValue());
                            LOG.debug(serverWorkerName + ".out_nullValue" + column + " :" + dsc.getNullValue());
                            LOG.debug(serverWorkerName + ".out_version" + column + " :" + dsc.getVersion());
                            LOG.debug(serverWorkerName + ".out_dataType " + column + " :" + SqlUtils.getDataType(dsc.getDataType()));
                            LOG.debug(serverWorkerName + ".out_datetimeCode " + column + " :" + dsc.getDatetimeCode());
                            LOG.debug(serverWorkerName + ".out_maxLen " + column + " :" + dsc.getMaxLen());
                            LOG.debug(serverWorkerName + ".out_precision " + column + " :" + dsc.getPrecision());
                            LOG.debug(serverWorkerName + ".out_scale " + column + " :" + dsc.getScale());
                            LOG.debug(serverWorkerName + ".out_nullInfo " + column + " :" + dsc.getNullInfo());
                            LOG.debug(serverWorkerName + ".out_signed " + column + " :" + dsc.getSigned());
                            LOG.debug(serverWorkerName + ".out_odbcDataType " + column + " :" + dsc.getOdbcDataType());
                            LOG.debug(serverWorkerName + ".out_odbcPrecision " + column + " :" + dsc.getOdbcPrecision());
                            LOG.debug(serverWorkerName + ".out_sqlCharset " + column + " :" + SqlUtils.getCharsetName(dsc.getSqlCharset()) + "[" + dsc.getSqlCharset() + "]");
                            LOG.debug(serverWorkerName + ".out_odbcCharset " + column + " :" + SqlUtils.getCharsetName(dsc.getOdbcCharset()) + "[" + dsc.getOdbcCharset() + "]");
                            LOG.debug(serverWorkerName + ".out_colHeadingNm " + column + " :" + dsc.getColHeadingNm());
                            LOG.debug(serverWorkerName + ".out_tableName " + column + " :" + dsc.getTableName());
                            LOG.debug(serverWorkerName + ".out_schemaName " + column + " :" + dsc.getSchemaName());
                            LOG.debug(serverWorkerName + ".out_headingName " + column + " :" + dsc.getHeadingName());
                            LOG.debug(serverWorkerName + ".out_intLeadPrec " + column + " :" + dsc.getParamMode());
                            LOG.debug(serverWorkerName + ".out_paramMode " + column + " :" + dsc.getColHeadingNm());
                            LOG.debug(serverWorkerName + ".out_memAlignOffset " + column + " :" + dsc.getMemAlignOffset());
                            LOG.debug(serverWorkerName + ".out_allocSize " + column + " :" + dsc.getAllocSize());
                            LOG.debug(serverWorkerName + ".out_varLayout " + column + " :" + dsc.getVarLayout());
                        }
                       }
                }
                if (inpNumberParams > 0){
                    SQLMXParameterMetaData spmtd = (SQLMXParameterMetaData)pmd;
                    inpDescList = new Descriptor2List(inpNumberParams);
                    
                    for(int param = 1; param <= inpNumberParams; param++){
                        sqlCharset_ = spmtd.getSqlCharset(param);
                        odbcCharset_ = spmtd.getOdbcCharset(param);
                        sqlDataType_ = spmtd.getSqlDataType(param);
                        dataType_ = spmtd.getDataType(param);
                        sqlPrecision_ = spmtd.getSqlPrecision(param);
                        sqlDatetimeCode_ = spmtd.getSqlDatetimeCode(param);
                        sqlOctetLength_ = spmtd.getSqlOctetLength(param);
                        isNullable_ = spmtd.isNullable(param);
                        name_ = spmtd.getName(param);
                        scale_ = spmtd.getScale(param);
                        precision_ = spmtd.getPrecision(param);
                        isSigned_ = spmtd.isSigned(param);
                        isCurrency_ = spmtd.getIsCurrency(param);
                        isCaseSensitive_ = spmtd.getIsCaseSensitive(param);
                        catalogName_ = spmtd.getCatalogName(param);
                        schemaName_ = spmtd.getSchemaName(param);
                        tableName_ = spmtd.getTableName(param);
                        fsDataType_ = spmtd.getFsDataType(param);
                        intLeadPrec_ = spmtd.getIntLeadPrec(param);
                        paramMode_ = spmtd.getMode(param);
                        paramIndex_ = spmtd.getIndex(param);
                        paramPos_ = spmtd.getPos(param);
                        odbcPrecision_ = spmtd.getOdbcPrecision(param);
                        maxLen_ = spmtd.getMaxLen(param);
                        displaySize_ = spmtd.getDisplaySize(param);
                        label_ = spmtd.getLabel(param);
                        
                        Descriptor2 inpDesc = new Descriptor2(sqlCharset_,odbcCharset_,sqlDataType_,dataType_,sqlPrecision_,sqlDatetimeCode_,
                            sqlOctetLength_,isNullable_,name_,scale_,precision_,isSigned_,
                            isCurrency_,isCaseSensitive_,catalogName_,schemaName_,tableName_,
                            fsDataType_,intLeadPrec_,paramMode_,paramIndex_,paramPos_,odbcPrecision_,
                            maxLen_,displaySize_,label_);
                        inpDescList.addDescriptor(param,inpDesc);
                    }
                    if(LOG.isDebugEnabled()){
                        for (int param = 1; param <= inpNumberParams; param++){
                            Descriptor2 dsc = inpDescList.getDescriptors2()[param-1];
                            LOG.debug(serverWorkerName + ".Param :" + param);
                            LOG.debug(serverWorkerName + ".inp_noNullValue" + param + " :" + dsc.getNoNullValue());
                            LOG.debug(serverWorkerName + ".inp_nullValue" + param + " :" + dsc.getNullValue());
                            LOG.debug(serverWorkerName + ".inp_version" + param + " :" + dsc.getVersion());
                            LOG.debug(serverWorkerName + ".inp_dataType " + param + " :" + SqlUtils.getDataType(dsc.getDataType()));
                            LOG.debug(serverWorkerName + ".inp_datetimeCode " + param + " :" + dsc.getDatetimeCode());
                            LOG.debug(serverWorkerName + ".inp_maxLen " + param + " :" + dsc.getMaxLen());
                            LOG.debug(serverWorkerName + ".inp_precision " + param + " :" + dsc.getPrecision());
                            LOG.debug(serverWorkerName + ".inp_scale " + param + " :" + dsc.getScale());
                            LOG.debug(serverWorkerName + ".inp_nullInfo " + param + " :" + dsc.getNullInfo());
                            LOG.debug(serverWorkerName + ".inp_signed " + param + " :" + dsc.getSigned());
                            LOG.debug(serverWorkerName + ".inp_odbcDataType " + param + " :" + dsc.getOdbcDataType());
                            LOG.debug(serverWorkerName + ".inp_odbcPrecision " + param + " :" + dsc.getOdbcPrecision());
                            LOG.debug(serverWorkerName + ".inp_sqlCharset " + param + " :" + SqlUtils.getCharsetName(dsc.getSqlCharset()) + "[" + dsc.getSqlCharset() + "]");
                            LOG.debug(serverWorkerName + ".inp_odbcCharset " + param + " :" + SqlUtils.getCharsetName(dsc.getOdbcCharset()) + "[" + dsc.getOdbcCharset() + "]");
                            LOG.debug(serverWorkerName + ".inp_colHeadingNm " + param + " :" + dsc.getColHeadingNm());
                            LOG.debug(serverWorkerName + ".inp_tableName " + param + " :" + dsc.getTableName());
                            LOG.debug(serverWorkerName + ".inp_schemaName " + param + " :" + dsc.getSchemaName());
                            LOG.debug(serverWorkerName + ".inp_headingName " + param + " :" + dsc.getHeadingName());
                            LOG.debug(serverWorkerName + ".inp_intLeadPrec " + param + " :" + dsc.getParamMode());
                            LOG.debug(serverWorkerName + ".inp_paramMode " + param + " :" + dsc.getColHeadingNm());
                            LOG.debug(serverWorkerName + ".inp_memAlignOffset " + param + " :" + dsc.getMemAlignOffset());
                            LOG.debug(serverWorkerName + ".inp_allocSize " + param + " :" + dsc.getAllocSize());
                            LOG.debug(serverWorkerName + ".inp_varLayout " + param + " :" + dsc.getVarLayout());
                        }
                       }
                }
            } catch (SQLException ex){
                LOG.error(serverWorkerName + ". Prepare.SQLException " + ex);
                errorList = new SQLWarningOrErrorList(ex); 
                returnCode = errorList.getReturnCode();
            }
            sqlQueryType = sqlStmtType;
            
            if (returnCode == ServerConstants.SQL_SUCCESS || returnCode == ServerConstants.SQL_SUCCESS_WITH_INFO) {
                trafStmt.setInpNumberParams(inpNumberParams);
                if (inpNumberParams > 0){
                    trafStmt.setInpParamLength(inpDescList.getParamLength());
                    trafStmt.setInpDescList(inpDescList);
                }
                trafStmt.setOutNumberParams(outNumberParams);
                if (outNumberParams > 0){
                    trafStmt.setOutParamLength(outDescList.getParamLength());
                    trafStmt.setOutDescList(outDescList);
                }
            }
//
//===================calculate length of output ByteBuffer========================
//
            bbHeader.clear();
            bbBody.clear();
//
// check if ByteBuffer is big enough for output
//      
            int dataLength = ServerConstants.INT_FIELD_SIZE;        //returnCode
            if (returnCode == ServerConstants.SQL_SUCCESS || returnCode == ServerConstants.SQL_SUCCESS_WITH_INFO) {
                if (returnCode == ServerConstants.SQL_SUCCESS_WITH_INFO) {
                    if (errorList != null)
                        dataLength += errorList.lengthOfData();
                    else
                        dataLength += ServerConstants.INT_FIELD_SIZE;             //totalErrorLength = 0
                }
                dataLength += ServerConstants.INT_FIELD_SIZE;    //sqlQueryType
                dataLength += ServerConstants.INT_FIELD_SIZE;    //stmtHandle
                dataLength += ServerConstants.INT_FIELD_SIZE;    //estimatedCost

                 if (inpNumberParams > 0)
                    dataLength += inpDescList.lengthOfData();
                 else
                    dataLength += ServerConstants.INT_FIELD_SIZE;
                 
                if (outNumberParams > 0)
                    dataLength += outDescList.lengthOfData();
                else
                    dataLength += ServerConstants.INT_FIELD_SIZE;
             }
            else {
                if (errorList != null)
                    dataLength += errorList.lengthOfData();
                else
                    dataLength += ServerConstants.INT_FIELD_SIZE;             //totalErrorLength = 0
            }
            int availableBuffer = bbBody.capacity() - bbBody.position();
            if(LOG.isDebugEnabled())
                LOG.debug(serverWorkerName + ". dataLength :" + dataLength + " availableBuffer :" + availableBuffer);
            if (dataLength > availableBuffer )
                bbBody = ByteBufferUtils.increaseCapacity(bbBody, dataLength > ServerConstants.BODY_SIZE ? dataLength : ServerConstants.BODY_SIZE );
//===================== build output ==============================================
            bbBody.putInt(returnCode);
            if (returnCode == ServerConstants.SQL_SUCCESS || returnCode == ServerConstants.SQL_SUCCESS_WITH_INFO) {
                if (returnCode == ServerConstants.SQL_SUCCESS_WITH_INFO) {
                    if (errorList != null)
                        errorList.insertIntoByteBuffer(bbBody);
                    else
                        bbBody.putInt(0);
                }
                bbBody.putInt(sqlQueryType);
                bbBody.putInt(stmtHandle);
                bbBody.putInt(estimatedCost);

                if (inpNumberParams > 0)
                    inpDescList.insertIntoByteBuffer(bbBody);
                else
                    bbBody.putInt(0);

                if (outNumberParams > 0)
                    outDescList.insertIntoByteBuffer(bbBody);
                else
                    bbBody.putInt(0);
            }
            else {
                if (errorList != null)
                    errorList.insertIntoByteBuffer(bbBody);
                else
                    bbBody.putInt(0);
            }
            bbBody.flip();
//=========================Update header================================ 
            hdr.setTotalLength(bbBody.limit());
            hdr.insertIntoByteBuffer(bbHeader);
            bbHeader.flip();

            clientData.setByteBufferArray(bbHeader, bbBody);
            clientData.setHdr(hdr);
            clientData.setRequest(ServerConstants.REQUST_WRITE_READ);
            
        } catch (UnsupportedEncodingException ue){
            LOG.error(serverWorkerName + ". Prepare.UnsupportedEncodingException :" + ue);
            clientData.setRequestAndDisconnect();
        } catch (Exception e){
            LOG.error(serverWorkerName + ". Prepare.Exception :" + e);
            clientData.setRequestAndDisconnect();
        }
        reset();
        return clientData;
    }
}
