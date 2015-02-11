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

import java.sql.*;
import java.net.*;
import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.channels.spi.*;
import java.util.Properties;

import org.trafodion.dcs.Constants;
import org.trafodion.dcs.util.*;
import org.trafodion.dcs.serverna.ServerConstants;
import org.trafodion.dcs.serverna.ServerUtils;
import org.trafodion.dcs.serverna.serverDriverInputOutput.*;
import org.trafodion.dcs.serverna.serverSql.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class ServerApiSqlConnect {
    static final int odbc_SQLSvc_InitializeDialogue_ParamError_exn_ = 1;
    static final int odbc_SQLSvc_InitializeDialogue_InvalidConnection_exn_ = 2;
    static final int odbc_SQLSvc_InitializeDialogue_SQLError_exn_ = 3;
    static final int odbc_SQLSvc_InitializeDialogue_SQLInvalidHandle_exn_ = 4;
    static final int odbc_SQLSvc_InitializeDialogue_SQLNeedData_exn_ = 5;
    static final int odbc_SQLSvc_InitializeDialogue_InvalidUser_exn_ = 6;

    private static  final Log LOG = LogFactory.getLog(ServerApiSqlConnect.class);
    private int instance;
    private int serverThread;
    private String serverWorkerName;
    private ClientData clientData;
//
    private ConnectionContext connectionContext;
    private UserDesc userDesc;
    private byte[] cert;
// 
    private ServerException serverException;
    private OutConnectionContext outConnectionContext;
    private TrafConnection trafConnection;
    
    ServerApiSqlConnect(int instance, int serverThread, byte[] cert) {  
        this.instance = instance;
        this.serverThread = serverThread;
        serverWorkerName = ServerConstants.SERVER_WORKER_NAME + "_" + instance + "_" + serverThread;
        this.cert = cert;
    }
    void init (){
        connectionContext = new ConnectionContext();
        userDesc = new UserDesc();
        serverException = new ServerException();
        outConnectionContext = new OutConnectionContext(cert);
        trafConnection = null;
    }
    void reset(){
        connectionContext = null;
        userDesc = null;
        serverException = null;
        outConnectionContext = null;
    }
    ClientData processApi(ClientData clientData) {  
        this.clientData = clientData;
        init();
//        
// ==============process input ByteBuffer===========================
// hdr + userDesc + connectionContext
//
        ByteBuffer bbHeader = clientData.bbHeader;
        ByteBuffer bbBody = clientData.bbBody;
        Header hdr = clientData.hdr;

        bbHeader.flip();
        bbBody.flip();
        
        try {

            hdr.extractFromByteArray(bbHeader);
            userDesc.extractFromByteBuffer(bbBody);
            connectionContext.extractFromByteBuffer(bbBody);
//            
//=====================Display input data=========================================
//            
            if(LOG.isDebugEnabled())
                System.out.println(serverWorkerName + ". threadRegisteredData :" + clientData.getThreadRegisteredData());
            
            String[] st = clientData.getThreadRegisteredData().split(":");
            clientData.setDialogueId(Integer.parseInt(st[2]));
            clientData.setNodeNumber(Integer.parseInt(st[3]));
            clientData.setProcessId(Integer.parseInt(st[4]));
            clientData.setProcessName(st[5]);
            clientData.setHostName(st[6]);
            clientData.setPortNumber(Integer.parseInt(st[7]));
            clientData.setClientHostName(st[8]);
            clientData.setClientIpAddress(st[9]);
            clientData.setClientPortNumber(Integer.parseInt(st[10]));
            clientData.setClientApplication(st[11]);
            
            if(LOG.isDebugEnabled()){
                System.out.println(serverWorkerName + ". dialogueId :" + clientData.getDialogueId());
                System.out.println(serverWorkerName + ". nodeNumber :" + clientData.getNodeNumber());
                System.out.println(serverWorkerName + ". processId :" + clientData.getProcessId());
                System.out.println(serverWorkerName + ". processName :" + clientData.getProcessName());
                System.out.println(serverWorkerName + ". hostName :" + clientData.getHostName());
                System.out.println(serverWorkerName + ". portNumber :" + clientData.getPortNumber());
                System.out.println(serverWorkerName + ". clientHostName :" + clientData.getClientHostName());
                System.out.println(serverWorkerName + ". clientIpAddress :" + clientData.getClientIpAddress());
                System.out.println(serverWorkerName + ". clientPortNumber :" + clientData.getClientPortNumber());
                System.out.println(serverWorkerName + ". clientApplication :" + clientData.getClientApplication());
            }
            if (connectionContext.getDialogueId() < 1 ) {
                throw new SQLException(serverWorkerName + ". Wrong dialogueId :" + connectionContext.getDialogueId());
            }
            if (connectionContext.getDialogueId() != clientData.getDialogueId() ) {
                throw new SQLException(serverWorkerName + ". Wrong dialogueId sent by the Client [sent/expected] : [" + connectionContext.getDialogueId() + "/" + clientData.getDialogueId() + "]");
            }
//=====================Process SqlConnect===========================
            
            try {
                trafConnection = new TrafConnection(connectionContext);
 
                outConnectionContext.getVersionList().getList()[0].setComponentId((short)4);       //ODBC_SRVR_COMPONENT
                outConnectionContext.getVersionList().getList()[0].setMajorVersion((short)3);
                outConnectionContext.getVersionList().getList()[0].setMinorVersion((short)5);
                outConnectionContext.getVersionList().getList()[0].setBuildId(1);
                
                outConnectionContext.getVersionList().getList()[1].setComponentId((short)3);       //SQL_COMPONENT
                outConnectionContext.getVersionList().getList()[1].setMajorVersion((short)1);
                outConnectionContext.getVersionList().getList()[1].setMinorVersion((short)1);
                outConnectionContext.getVersionList().getList()[1].setBuildId(1);
                
                outConnectionContext.setNodeId((short)1);
                outConnectionContext.setProcessId(Integer.valueOf(ServerUtils.processId()));
                outConnectionContext.setComputerName(clientData.getHostName());
                outConnectionContext.setCatalog("");
                outConnectionContext.setSchema("");
    
                outConnectionContext.setOptionFlags1(0);
                outConnectionContext.setOptionFlags2(0);
    
                outConnectionContext.setRoleName("");
                
            } catch (SQLException ex){
                System.out.println(serverWorkerName + ". ServerApiSqlConnect.SQLException :" + ex);
                serverException.setServerException (odbc_SQLSvc_InitializeDialogue_SQLError_exn_, 0, ex);                
            }
//
//===================calculate length of output ByteBuffer========================
//
            bbHeader.clear();
            bbBody.clear();
//
// check if ByteBuffer is big enough for serverException + outConnectionContext
//        
            int dataLength = serverException.lengthOfData();
            dataLength += outConnectionContext.lengthOfData();
            int availableBuffer = bbBody.capacity() - bbBody.position();
            
            if(LOG.isDebugEnabled())
                System.out.println("dataLength :" + dataLength + " availableBuffer :" + availableBuffer);
        
            if (dataLength > availableBuffer ) {
                bbBody = ByteBufferUtils.increaseCapacity(bbBody, dataLength > ServerConstants.BODY_SIZE ? dataLength : ServerConstants.BODY_SIZE );
                ByteBufferUtils.printBBInfo(bbBody);
                clientData.bbBuf[1] = bbBody;
            }
//===================== build output ==============================================
            serverException.insertIntoByteBuffer(bbBody);
            outConnectionContext.insertIntoByteBuffer(bbBody);
            bbBody.flip();
//=========================Update header================================
            hdr.setTotalLength(bbBody.limit());
            hdr.insertIntoByteBuffer(bbHeader);
            bbHeader.flip();

            clientData.setByteBufferArray(bbHeader, bbBody);
            clientData.setHdr(hdr);
            clientData.setRequest(ServerConstants.REQUST_WRITE_READ);
            clientData.setTrafConnection(trafConnection);
            
        } catch (SQLException se){
            System.out.println(serverWorkerName + ". Connect.SQLException :" + se);
            clientData.setRequestAndDisconnect();
        } catch (UnsupportedEncodingException ue){
            System.out.println(serverWorkerName + ". Connect.UnsupportedEncodingException :" + ue);
            clientData.setRequestAndDisconnect();
        } catch (Exception e){
            System.out.println(serverWorkerName + ". Connect.Exception :" + e);
            clientData.setRequestAndDisconnect();
        }
        reset();
        return clientData;
    }
}
