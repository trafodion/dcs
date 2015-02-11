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
package org.trafodion.dcs.serverna.serverHandler;

import java.net.*;
import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.channels.spi.*;
import java.sql.SQLException;

import org.trafodion.dcs.Constants;
import org.trafodion.dcs.util.*;
import org.trafodion.dcs.serverna.ServerConstants;
import org.trafodion.dcs.serverna.ServerUtils;
import org.trafodion.dcs.serverna.serverDriverInputOutput.*;
import org.trafodion.dcs.serverna.serverSql.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class ServerApiEndTransaction {
    private static final int odbc_SQLSvc_EndTransaction_ParamError_exn_ = 1;
    private static final int odbc_SQLSvc_EndTransaction_InvalidConnection_exn_ = 2;
    private static final int odbc_SQLSvc_EndTransaction_SQLError_exn_ = 3;
    private static final int odbc_SQLSvc_EndTransaction_SQLInvalidHandle_exn_ = 4;
    private static final int odbc_SQLSvc_EndTransaction_TransactionError_exn_ = 5;

    private static  final Log LOG = LogFactory.getLog(ServerApiEndTransaction.class);
    private int instance;
    private int serverThread;
    private String serverWorkerName;
    private ClientData clientData;

    private int dialogueId;
    private short transactionOpt;

    private ServerException serverException;

    ServerApiEndTransaction(int instance, int serverThread) {  
        this.instance = instance;
        this.serverThread = serverThread;
        serverWorkerName = ServerConstants.SERVER_WORKER_NAME + "_" + instance + "_" + serverThread;
     }
    void init(){
        dialogueId = 0;
        transactionOpt = 0;
        serverException = new ServerException();
    }
    void reset(){
        dialogueId = 0;
        transactionOpt = 0;
        serverException = null;
    }
    ClientData ProcessApi(ClientData clientData) {  
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
            transactionOpt = bbBody.getShort();
            if(LOG.isDebugEnabled()){
                LOG.debug(serverWorkerName + ". dialogueId :" + dialogueId);
                LOG.debug(serverWorkerName + ". transactionOpt :" + transactionOpt);
            }
            if (dialogueId < 1 ) {
                throw new SQLException(serverWorkerName + ". Wrong dialogueId :" + dialogueId);
            }
            if (dialogueId != clientData.getDialogueId() ) {
                throw new SQLException(serverWorkerName + ". Wrong dialogueId sent by the Client [sent/expected] : [" + dialogueId + "/" + clientData.getDialogueId() + "]");
            }
//=====================Process ServerApiEndTransaction==============
//
/*          try {
            } catch (SQLException ex){
                serverException.setServerException (odbc_SQLSvc_EndTransaction_SQLError_exn_, 0, ex);                
            }
*/
//            
//===================calculate length of output ByteBuffer========================
//
            bbHeader.clear();
            bbBody.clear();
//
// check if ByteBuffer is big enough for output
//      
            int dataLength = serverException.lengthOfData();
            int availableBuffer = bbBody.capacity() - bbBody.position();
            
            if(LOG.isDebugEnabled())
                LOG.debug(serverWorkerName + ". dataLength :" + dataLength + " availableBuffer :" + availableBuffer);
        
            if (dataLength > availableBuffer ) {
                bbBody = ByteBufferUtils.increaseCapacity(bbBody, dataLength > ServerConstants.BODY_SIZE ? dataLength : ServerConstants.BODY_SIZE );
                ByteBufferUtils.printBBInfo(bbBody);
                clientData.bbBuf[1] = bbBody;
            }
//===================== build output ==============================================
            serverException.insertIntoByteBuffer(bbBody);
            
            bbBody.flip();
//=========================Update header================================ 
            hdr.setTotalLength(bbBody.limit());
            hdr.insertIntoByteBuffer(bbHeader);
            bbHeader.flip();

            clientData.setByteBufferArray(bbHeader, bbBody);
            clientData.setHdr(hdr);
            clientData.setRequest(ServerConstants.REQUST_WRITE_READ);
            
        } catch (SQLException se){
            LOG.error(serverWorkerName + ". SQLException :" + se);
            clientData.setRequestAndDisconnect();
        } catch (UnsupportedEncodingException ue){
            LOG.error(serverWorkerName + ". UnsupportedEncodingException :" + ue);
            clientData.setRequestAndDisconnect();
        } catch (Exception e){
            LOG.error(serverWorkerName + ". Exception :" + e);
            clientData.setRequestAndDisconnect();
        }
        reset();
        return clientData;
    }
}