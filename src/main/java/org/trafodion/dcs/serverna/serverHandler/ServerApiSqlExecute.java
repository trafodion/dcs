/**
 *(C) Copyright 2013 Hewlett-Packard Development Company, L.P.
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
package org.trafodion.dcs.serverna.serverHandler;

import java.lang.reflect.*;
import java.net.*;
import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.channels.spi.*;
import java.sql.*;
import java.util.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.SimpleDateFormat;
import java.text.ParseException;

import org.trafodion.jdbc.t2.*;

import org.trafodion.dcs.Constants;
import org.trafodion.dcs.util.*;
import org.trafodion.dcs.serverna.ServerConstants;
import org.trafodion.dcs.serverna.ServerUtils;
import org.trafodion.dcs.serverna.serverDriverInputOutput.*;
import org.trafodion.dcs.serverna.serverSql.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class ServerApiSqlExecute {
    private static final int odbc_SQLSvc_Execute_ParamError_exn_ = 1;
    private static final int odbc_SQLSvc_Execute_InvalidConnection_exn_ = 2;
    private static final int odbc_SQLSvc_Execute_SQLError_exn_ = 3;
    private static final int odbc_SQLSvc_Execute_SQLInvalidHandle_exn_ = 4;
    private static final int odbc_SQLSvc_ExecuteSQLNeedData_exn_ = 5;
    private static final int odbc_SQLSvc_Execute_SQLRetryCompile_exn_ = 6;
    private static final int odbc_SQLSvc_Execute_SQLStillExecuting_exn_ = 7;
    private static final int odbc_SQLSvc_Execute_SQLQueryCancelled_exn_ = 8;
    private static final int odbc_SQLSvc_Execute_TransactionError_exn_ = 9;

    private static  final Log LOG = LogFactory.getLog(ServerApiSqlExecute.class);
    private int instance;
    private int serverThread;
    private String serverWorkerName;
    private ClientData clientData;
//
    private int dialogueId;
    private int sqlAsyncEnable;
    private int queryTimeout;
    private int inpRowCnt;
    private int maxRowsetSize;
    private int sqlStmtType;
    private int stmtHandle;
    private int stmtType;
    private String sqlString;
    private String cursorName;
    private String stmtLabel;
    private String stmtExplainLabel;
    private SQLDataValue inpDataValue;
    private SQLValueList inpValueList;
    private byte[] txId;
    
    private int holdableCursor;
//======================================================================= 
	private PreparedStatement pstmt;
	private TrafConnection trafConn;
	private TrafStatement trafStmt;
//------------------input-------------------------------------	
    private int inpNumberParams;
    private long inpParamLength;
    private int inpValuesLength;
    private Descriptor2List inpDescList;
	private int iret;

    private int outNumberParams;
    private Descriptor2List outDescList;
    private ResultSet rs;
    private ResultSetMetaData rsMD;
//-------------------output-------------------------------------    
    private int returnCode;
    private SQLWarningOrErrorList errorList;
    private long rowsAffected;
    private int sqlQueryType;
    private int estimatedCost;
    private byte[] outValues;
    
    private int numResultSets;
    private Descriptor2List resultSetDescList;
    private String[] stmtLabels;
    private String[] proxySyntax;
    private String singleSyntax;
//---------------------------------------------------------------
    ServerApiSqlExecute(int instance, int serverThread) {  
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
        inpRowCnt = 0;
        maxRowsetSize = 0;
        sqlStmtType = 0;
        stmtHandle = 0;
        stmtType = 0;
        sqlString = null;
        cursorName = null;
        stmtLabel = null;
        stmtExplainLabel = null;
        inpDataValue = null;
        inpValuesLength = 0;
        txId = null;     // JDBC is the only one that will use this to join a transaction

		holdableCursor = 0; //default
//==================================================================
		trafConn = null;
		trafStmt = null;
//-----------------------------input--------------------------------------
        inpNumberParams = 0;
        inpParamLength = 0;
        inpValuesLength = 0;
        inpDescList = null;
        iret = 0;
//------------------------------output------------------------------------        
        returnCode = 0;
        errorList = null;
        rowsAffected = 0;
        sqlQueryType = 0;
        estimatedCost = 0;
        outNumberParams = 0;
        outDescList = null;
        rs = null;
        rsMD = null;

        outValues = null;
        numResultSets = 0;
        resultSetDescList = null;
        stmtLabels = null;
        singleSyntax = "";
        proxySyntax = null;
	}
	ClientData processApi(ClientData clientData) {  
		init();
		this.clientData = clientData;
//        
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
			holdableCursor =  bbBody.getInt();
			queryTimeout =  bbBody.getInt();
			inpRowCnt =  bbBody.getInt();
			maxRowsetSize =  bbBody.getInt();
			sqlStmtType =  bbBody.getInt();
			stmtHandle =  bbBody.getInt();
			stmtType =  bbBody.getInt();
			sqlString = ByteBufferUtils.extractStringWithCharset(bbBody);
			cursorName = ByteBufferUtils.extractStringWithCharset(bbBody);
			stmtLabel = ByteBufferUtils.extractStringWithCharset(bbBody);
			stmtExplainLabel = ByteBufferUtils.extractString(bbBody);
//            txId = ByteBufferUtils.extractByteArray(bbBody);
			if(LOG.isDebugEnabled()) {
//				ByteBufferUtils.toHexString("inputValueList", bbBody);
				System.out.println(serverWorkerName + ". dialogueId :" + dialogueId);
				System.out.println(serverWorkerName + ". holdableCursor :" + holdableCursor);
				System.out.println(serverWorkerName + ". queryTimeout :" + queryTimeout);
				System.out.println(serverWorkerName + ". inpRowCnt :" + inpRowCnt);
				System.out.println(serverWorkerName + ". maxRowsetSize :" + maxRowsetSize);
				System.out.println(serverWorkerName + ". sqlStmtType :" + sqlStmtType);
				System.out.println(serverWorkerName + ". stmtHandle :" + stmtHandle);
				System.out.println(serverWorkerName + ". stmtType :" + stmtType);
				System.out.println(serverWorkerName + ". sqlString :" + sqlString);
				System.out.println(serverWorkerName + ". cursorName :" + cursorName);
				System.out.println(serverWorkerName + ". stmtLabel :" + stmtLabel);
				System.out.println(serverWorkerName + ". stmtExplainLabel :" + stmtExplainLabel);
//				System.out.println(serverWorkerName + ". txId :" + txId);
			}
			if (dialogueId < 1 ) {
				throw new SQLException(serverWorkerName + ". Wrong dialogueId :" + dialogueId);
			}
			if (dialogueId != clientData.getDialogueId() ) {
				throw new SQLException(serverWorkerName + ". Wrong dialogueId sent by the Client [sent/expected] : [" + dialogueId + "/" + clientData.getDialogueId() + "]");
			}
//=============================================================================
            trafConn = clientData.getTrafConnection();
			trafStmt = trafConn.getTrafStatement(stmtLabel);
			pstmt = (PreparedStatement)trafStmt.getStatement();
			
            boolean isResultSet = trafStmt.getIsResultSet();
//			
//=====================Process ServerApiSqlExecute===========================
//
			try {
				int addedBatches = 0;
				inpNumberParams = trafStmt.getInpNumberParams();
				if (inpNumberParams > 0){
				    if(LOG.isDebugEnabled())
					    ByteBufferUtils.toHexString("inputValueList", bbBody, 10);
					inpDescList = trafStmt.getInpDescList();
					inpParamLength = trafStmt.getInpParamLength();
					inpValuesLength = bbBody.getInt();
                    if(LOG.isDebugEnabled()){
					    System.out.println("inpValuesLength :" + inpValuesLength);
					    System.out.println("inpParamLength :" + inpParamLength);
                    }
					if (inpValuesLength > 0){
						long startOffset = bbBody.position();
						int paramRowCount = inpRowCnt;
						int paramCount = inpNumberParams;
						for (int row = 0; row < inpRowCnt; row++) {
							for (int col = 0; col < inpNumberParams; col++) {
								bbBody = setInpParams(pstmt, inpDescList, paramRowCount, col, row, bbBody, startOffset);
							}
							if (inpRowCnt > 1){
								pstmt.addBatch();
								addedBatches++;
							}
						}
					}
				}
				int txIdLen = bbBody.limit() - bbBody.position();
                if (txIdLen > 0){
                    txId = new byte[txIdLen];
                    bbBody.get(txId);
                }
                if(LOG.isDebugEnabled())
                    System.out.println(serverWorkerName + ". txId :" + Arrays.toString(txId));
               
				if (addedBatches > 1){
                    
					int[] numStatus = pstmt.executeBatch();
					rowsAffected = numStatus.length;
					
                    if(LOG.isDebugEnabled())
                        System.out.println(serverWorkerName + ". Execute.executeBatch rowsAffected :" + rowsAffected);
				}
				else {
		            boolean status = pstmt.execute();
		            if(status)
		                rs = pstmt.getResultSet();
		            else
		            	rowsAffected = pstmt.getUpdateCount();
		            
                    if(LOG.isDebugEnabled())
                        System.out.println(serverWorkerName + ". Execute.execute status :" + status);
				}
			} catch (BatchUpdateException bex){
			    System.out.println(serverWorkerName + ". Execute.BatchUpdateException " + bex);
		        errorList = new SQLWarningOrErrorList(bex, bex.getUpdateCounts()); 
                returnCode = errorList.getReturnCode();
                
			} catch (SQLException ex){
			    System.out.println(serverWorkerName + ". Execute.SQLException " + ex);
                errorList = new SQLWarningOrErrorList(ex); 
                returnCode = errorList.getReturnCode();
			}
			sqlQueryType = SqlUtils.getSqlStmtType(sqlString);
//
//===================calculate length of output ByteBuffer========================
//
			bbHeader.clear();
			bbBody.clear();
//
// check if ByteBuffer is big enough for output
//  
            int dataLength = Constants.INT_FIELD_SIZE; 				//returnCode
            if (errorList != null)
                dataLength += errorList.lengthOfData();
            else
                dataLength += Constants.INT_FIELD_SIZE; 			//totalErrorLength = 0
            
            if (outDescList != null)
                dataLength += outDescList.lengthOfData();
            else
                dataLength += Constants.INT_FIELD_SIZE;             //outDescLength = 0
            
            dataLength += Constants.INT_FIELD_SIZE; 				//rowsAffected
            dataLength += Constants.INT_FIELD_SIZE; 				//sqlQueryType
            dataLength += Constants.INT_FIELD_SIZE; 				//estimatedCost
            dataLength += ByteBufferUtils.lengthOfByteArray(outValues); //outValues
            
            dataLength += Constants.INT_FIELD_SIZE; 				//numResultSets
			if (numResultSets > 0) {
				for (int i = 0; i < numResultSets; i++) {
					dataLength += Constants.INT_FIELD_SIZE; 		//stmt_handle
					dataLength += ByteBufferUtils.lengthOfString(stmtLabels[i]); //stmtLabels
					dataLength += Constants.INT_FIELD_SIZE; 		//stmt_label_charset
					
		            if (resultSetDescList != null)
		                dataLength += resultSetDescList.lengthOfData();
		            else
	                    dataLength += Constants.INT_FIELD_SIZE;
		            
					dataLength += ByteBufferUtils.lengthOfString(proxySyntax[i]); //proxySyntax[i]
				}
			}
			dataLength += ByteBufferUtils.lengthOfString(singleSyntax);
			
            int availableBuffer = bbBody.capacity() - bbBody.position();
			if(LOG.isDebugEnabled())
				System.out.println(serverWorkerName + ". dataLength :" + dataLength + " availableBuffer :" + availableBuffer);
            if (dataLength > availableBuffer )
                bbBody = ByteBufferUtils.increaseCapacity(bbBody, dataLength > ServerConstants.BODY_SIZE ? dataLength : ServerConstants.BODY_SIZE );

//===================== build output ==============================================
    		bbBody.putInt(returnCode);
            if (errorList != null)
                errorList.insertIntoByteBuffer(bbBody);
            else
                bbBody.putInt(0);

            if (outDescList != null){
                outDescList.insertIntoByteBuffer(bbBody);
            } else
                bbBody.putInt(0);
                
			ByteBufferUtils.insertUInt(rowsAffected, bbBody);
			bbBody.putInt(sqlQueryType);
			bbBody.putInt(estimatedCost);
			ByteBufferUtils.insertByteArray(outValues, bbBody);     //outValues
			bbBody.putInt(numResultSets);

			if (numResultSets > 0) {
				for (int i = 0; i < numResultSets; i++) {
					bbBody.putInt(0); // int stmt_handle - ignored
					ByteBufferUtils.insertString(stmtLabels[i], bbBody);
					bbBody.putInt(0); // long stmt_label_charset - ignored
					
                    if (resultSetDescList != null)
                         resultSetDescList.insertIntoByteBuffer(bbBody);
                    else
                        bbBody.putInt(0);
                    
 					ByteBufferUtils.insertString(proxySyntax[i], bbBody);
				}
			}
			ByteBufferUtils.insertString(singleSyntax, bbBody);

            bbBody.flip();
//=========================Update header================================ 
            hdr.setTotalLength(bbBody.limit());
            hdr.insertIntoByteBuffer(bbHeader);
            bbHeader.flip();

            clientData.setByteBufferArray(bbHeader, bbBody);
            clientData.setHdr(hdr);
            clientData.setRequest(ServerConstants.REQUST_WRITE_READ);
            
		} catch (UnsupportedEncodingException ue){
            System.out.println(serverWorkerName + ". Execute.UnsupportedEncodingException :" + ue);
            clientData.setRequestAndDisconnect();
        } catch (Exception e){
            System.out.println(serverWorkerName + ". Execute.Exception :" + e);
            clientData.setRequestAndDisconnect();
        }
        reset();
        return clientData;
    }
//===================================================================================
// get the column value data from Execute2 in String format
    ByteBuffer setInpParams(PreparedStatement pstmt, Descriptor2List inpDescList, int paramRowCount, int paramNumber, int rowNumber, ByteBuffer bbBody, long startOffset) throws SQLException, ParseException, UnsupportedEncodingException {
        int param = paramNumber + 1;
        Descriptor2[] descArray = null;
        Descriptor2 desc = null;
        int paramLength = 0;

        String tmpStr = "";
        Short tmps = 0;
        Integer tmpi = 0;
        Long tmpl = 0L;
        Float tmpf = 0f;
        Double tmpd = 0d;
        BigInteger tmpbi;
        BigDecimal tmpbd;
        boolean isSigne = true;

        Object retObj;
        byte[] tbuffer = null;
        int year, month, day, hour, minute, second;
        long nanoSeconds;
        String charSet = "";

        descArray = inpDescList.getDescriptors2();
        desc = descArray[paramNumber];

        int precision = desc.getPrecision();
        int scale = desc.getScale();
        int datetimeCode = desc.getDatetimeCode();
        int FSDataType = desc.getFsDataType();
        int OdbcDataType = desc.getOdbcDataType();
        int dataCharSet = desc.getSqlCharset();
        int dataLength = desc.getMaxLen();
        int dataType = desc.getDataType();

        // setup the offsets
        int noNullValue = desc.getNoNullValue();
        int nullValue = desc.getNullValue();
        long nullOffset = 0L;
        long noNullOffset = 0L;
        
		if (dataType == ServerConstants.SQLTYPECODE_VARCHAR_WITH_LENGTH) {
			dataLength += 2;

			if (dataLength % 2 != 0)
				dataLength++;
		}
        if (nullValue != -1){
            nullValue = (nullValue * paramRowCount) + (rowNumber * 2);
            nullOffset = startOffset + nullValue;
            bbBody.position((int)nullOffset);
            short isNull = bbBody.getShort();
            if ( isNull == -1){
            	pstmt.setObject(param, null);
            	return bbBody;
            }
        }
        noNullValue = (noNullValue * paramRowCount) + (rowNumber * dataLength);
        noNullOffset = startOffset + noNullValue;
        bbBody.position((int)noNullOffset);
        
        System.out.println("[" + param + "] noNullOffset :"+ noNullOffset + " noNullValue :" + noNullValue);

        if(dataCharSet == SqlUtils.SQLCHARSETCODE_UNICODE)
            charSet = "UTF-16LE";
        else
            charSet = SqlUtils.getCharsetName(dataCharSet);
        
        switch (dataType) {
        case ServerConstants.SQLTYPECODE_CHAR:
            System.out.println("["+ param + "] dataType :SQLTYPECODE_CHAR");
            tbuffer = new byte[dataLength];
            bbBody.get(tbuffer, 0, dataLength);
            retObj = new String(tbuffer, charSet);
            System.out.println("["+ param + "] dataType :SQLTYPECODE_CHAR length :" + dataLength + " scale :" + scale + "  retObj :" + retObj);
            break;
        case ServerConstants.SQLTYPECODE_VARCHAR:
        case ServerConstants.SQLTYPECODE_VARCHAR_LONG:
        	ByteBufferUtils.toHexString("VARCHAR/VARCHAR_LONG", bbBody, (dataLength > 100 ? 100 : dataLength));
			tbuffer = new byte[dataLength];
			bbBody.get(tbuffer, 0, dataLength);
			retObj = new String(tbuffer, charSet);
			System.out.println("["+ param + "] dataType :SQLTYPECODE_VARCHAR len :" + dataLength + " scale :" + scale + "  retObj :" + retObj);
			System.out.println(" retObj :" + Arrays.toString(((String)retObj).getBytes()));
        	break;
        case ServerConstants.SQLTYPECODE_VARCHAR_WITH_LENGTH:
        	ByteBufferUtils.toHexString("VARCHAR_WITH_LENGTH", bbBody, (dataLength > 100 ? 100 : dataLength));
            boolean shortLength = precision < Math.pow(2, 15);
            int dataOffset = (shortLength) ? 2 : 4;
            dataLength = (shortLength) ? bbBody.getShort() : bbBody.getInt();
            tbuffer = new byte[dataLength];
            bbBody.get(tbuffer, 0, dataLength);
            retObj = new String(tbuffer, charSet);
            System.out.println("["+ param + "] dataType :SQLTYPECODE_VARCHAR len :" + dataLength + " scale :" + scale + "  retObj :" + retObj);
            System.out.println(" retObj :" + Arrays.toString(((String)retObj).getBytes()));
            break;
        case ServerConstants.SQLTYPECODE_INTERVAL:
            tbuffer = new byte[dataLength];
            bbBody.get(tbuffer);
            retObj =  new String(tbuffer);
            System.out.println("["+ param + "] dataType :SQLTYPECODE_INTERVAL dataLength :" + dataLength + " scale :" + scale + "  retObj :" + retObj);
            break;
        case ServerConstants.SQLTYPECODE_DATETIME:
            switch (datetimeCode) {
            case ServerConstants.SQLDTCODE_DATE:
                tbuffer = new byte[dataLength];
                bbBody.get(tbuffer);
                retObj = java.sql.Date.valueOf((new String(tbuffer)).trim());
                System.out.println("["+ param + "] dataType :SQLDTCODE_DATE dataLength :" + dataLength + " scale :" + scale + "  retObj :" + retObj);
                break;
            case ServerConstants.SQLDTCODE_TIMESTAMP:
                tbuffer = new byte[dataLength];
                bbBody.get(tbuffer);
                retObj = Timestamp.valueOf((new String(tbuffer)).trim());
                System.out.println("["+ param + "] dataType :SQLDTCODE_TIMESTAMP dataLength :" + dataLength + " scale :" + scale + "  retObj :" + retObj);
                break;
            case ServerConstants.SQLDTCODE_TIME:
                if (OdbcDataType == java.sql.Types.OTHER) // For Desc.SQLDTCODE_HOUR_TO_FRACTION
                {
                    tbuffer = new byte[dataLength];
                    bbBody.get(tbuffer);
                    retObj = new String((new String(tbuffer)).trim());
                } else {
                	dataLength = ServerConstants.timeLength;
                    tbuffer = new byte[dataLength];
                    bbBody.get(tbuffer);
                    retObj = Time.valueOf((new String(tbuffer)).trim());
                }
                System.out.println("["+ param + "] dataType :SQLDTCODE_TIME dataLength :" + dataLength + " scale :" + scale + "  retObj :" + retObj);
                break;
            default:
                tbuffer = new byte[dataLength];
                bbBody.get(tbuffer);
                retObj = new String(tbuffer);
                System.out.println("["+ param + "] dataType :default dataLength :" + dataLength + " scale :" + scale + "  retObj :" + retObj);
                break;
            }
            break;
        case ServerConstants.SQLTYPECODE_SMALLINT:
            short sValue = bbBody.getShort();
            retObj = new Short(sValue);
            if (scale > 0) {
                retObj = new BigDecimal(new BigInteger(retObj.toString()), scale);
            }
            System.out.println("["+ param + "] dataType :SQLTYPECODE_SMALLINT dataLength :" + dataLength + " scale :" + scale + "  retObj :" + retObj);
            break;
        case ServerConstants.SQLTYPECODE_SMALLINT_UNSIGNED:
            int signedSValue = ByteBufferUtils.extractUShort(bbBody);
            if (scale > 0) {
                tmpbd = new BigDecimal(new BigInteger(String.valueOf(signedSValue)), (int) scale);
            } else {
                tmpbd = new BigDecimal(String.valueOf(signedSValue));
            }
            retObj = tmpbd;
            System.out.println("["+ param + "] dataType :SQLTYPECODE_SMALLINT_UNSIGNED dataLength :" + dataLength + " scale :" + scale + "  retObj :" + retObj);
            break;
        case ServerConstants.SQLTYPECODE_INTEGER:
            retObj = new Integer(bbBody.getInt());
            if (scale > 0) {
                retObj = new BigDecimal(new BigInteger(retObj.toString()), scale);
            }
            System.out.println("["+ param + "] dataType :SQLTYPECODE_INTEGER dataLength :" + dataLength + " scale :" + scale + "  retObj :" + retObj);
            break;
        case ServerConstants.SQLTYPECODE_INTEGER_UNSIGNED:
            retObj = new Long(ByteBufferUtils.extractUInt(bbBody));
            if (scale > 0) {
                retObj = new BigDecimal(new BigInteger(retObj.toString()), scale);
            }
            System.out.println("["+ param + "] dataType :SQLTYPECODE_INTEGER_UNSIGNED dataLength :" + dataLength + " scale :" + scale + " retObj :" + retObj);
            break;
        case ServerConstants.SQLTYPECODE_LARGEINT:
            retObj = new Long(bbBody.getLong());
            if (scale > 0) {
                retObj = new BigDecimal(new BigInteger(retObj.toString()), scale);
            }
            System.out.println("["+ param + "] dataType :SQLTYPECODE_LARGEINT + dataLength :" + dataLength + " scale :" + scale + " retObj :" + retObj);
            break;
//--------------------------------- SQLTYPECODE_NUMERIC/SQLTYPECODE_NUMERIC_UNSIGNED converted to INTEGER using fsDataType_ (Descriptor2) --------- 
        case ServerConstants.SQLTYPECODE_NUMERIC:
        case ServerConstants.SQLTYPECODE_NUMERIC_UNSIGNED:
            if(LOG.isDebugEnabled())
    		    ByteBufferUtils.toHexString("SQLTYPECODE_NUMERIC", bbBody, (dataLength > 100 ? 100 : dataLength));
            throw new SQLException("restricted_data_type");
        case ServerConstants.SQLTYPECODE_DECIMAL:
        case ServerConstants.SQLTYPECODE_DECIMAL_LARGE:
        case ServerConstants.SQLTYPECODE_DECIMAL_UNSIGNED:
        case ServerConstants.SQLTYPECODE_DECIMAL_LARGE_UNSIGNED:
            String retStr;
        
            tbuffer = new byte[dataLength];
            bbBody.get(tbuffer);

            byte firstByte = tbuffer[0];
            byte sign = (byte) (firstByte & (byte) (0x80));
            if (sign == (byte) (0x80)) {
                tbuffer[0] = (byte) (tbuffer[0] - (byte) (0x80));
                retStr = "-" + new String(tbuffer);
            } else {
                retStr = new String(tbuffer);
            }
            retObj = new BigDecimal(new BigInteger(retStr), scale);
            System.out.println("["+ param + "] dataType :SQLTYPECODE_DECIMAL dataLength :" + dataLength + " scale :" + scale + " retObj :" + retObj);
            break;
        case ServerConstants.SQLTYPECODE_REAL:
            retObj = new Float(Float.intBitsToFloat(bbBody.getInt()));
            System.out.println("["+ param + "] dataType :SQLTYPECODE_REAL dataLength :" + dataLength + " scale :" + scale + " retObj :" + retObj);
            break;
        case ServerConstants.SQLTYPECODE_DOUBLE:
        case ServerConstants.SQLTYPECODE_FLOAT:
            retObj = new Double(Double.longBitsToDouble(bbBody.getLong()));
            System.out.println("["+ param + "] dataType :SQLTYPECODE_DOUBLE dataLength :" + dataLength + " scale :" + scale + "  retObj :" + retObj);
            break;
        case ServerConstants.SQLTYPECODE_BIT:
        case ServerConstants.SQLTYPECODE_BITVAR:
        case ServerConstants.SQLTYPECODE_BPINT_UNSIGNED:
        default:
            throw new SQLException("restricted_data_type");
        }
        pstmt.setObject(param,retObj);
        return bbBody;
    } // end getExecute2FetchString
}