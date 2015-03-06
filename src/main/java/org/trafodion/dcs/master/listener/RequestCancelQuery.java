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
package org.trafodion.dcs.master.listener;

import java.net.*;
import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.channels.spi.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.data.Stat;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.ZooKeeper;

import org.trafodion.dcs.Constants;
import org.trafodion.dcs.util.*;
import org.trafodion.dcs.zookeeper.ZkClient;

public class RequestCancelQuery {
    private static  final Log LOG = LogFactory.getLog(RequestCancelQuery.class);

    private ZkClient zkc = null;
    private String parentZnode = "";
    
    private int dialogueId = 0;
    private int srvrType = 0;
    private String srvrObjRef = "";
    private int stopType = 0;

    private boolean cancelConnection = false;
    private GetObjRefException exception = null;
    private ByteBuffer header = null;
    private ByteBuffer body = null;
    private Header hdr = null;
    private SocketAddress clientSocketAddress = null;

    RequestCancelQuery(ZkClient zkc,String parentZnode){
        this.zkc = zkc;
        this.parentZnode = parentZnode;
        init ();
    }

    void init (){
        dialogueId = 0;
        srvrType = 0;
        srvrObjRef = "";
        stopType = 0;
        cancelConnection = false;
        exception = null;
        header = null;
        body = null;
        hdr = null;
        clientSocketAddress = null;
    }

    void reset (){
        dialogueId = 0;
        srvrType = 0;
        srvrObjRef = "";
        stopType = 0;
        cancelConnection = false;
        exception = null;
        header = null;
        body = null;
        hdr = null;
        clientSocketAddress = null;
    }

    ClientData processRequest(ClientData clientData, Socket s) { 
        cancelConnection = false;
        exception = new GetObjRefException();
        header = clientData.header;
        body = clientData.body;
        hdr = clientData.hdr;
        clientSocketAddress = clientData.clientSocketAddress;

        try {
// get input
            header.flip();
            hdr.extractFromByteArray(header);
            body.flip();
            dialogueId = body.getInt();
            srvrType = body.getInt();
            srvrObjRef = ByteBufferUtils.extractString(body);
            stopType = body.getInt();
            if(LOG.isDebugEnabled()){
                LOG.debug(clientSocketAddress + ". dialogueId :" + dialogueId);
                LOG.debug(clientSocketAddress + ". srvrType :" + srvrType);
                LOG.debug(clientSocketAddress + ". srvrObjRef :" + srvrObjRef);
                LOG.debug(clientSocketAddress + ". stopType :" + stopType);
            }
// process request

// build output
            header.clear();
            body.clear();
            exception.exception_nr = ListenerConstants.DcsMasterParamError_exn;
            exception.ErrorText = "Cancel Query Api is not implemented [" + hdr.getOperationId() + "]";
            LOG.debug(clientSocketAddress + ": " + exception.ErrorText);
            exception.insertIntoByteBuffer(body);
            body.flip();
            hdr.setTotalLength(body.limit());
            hdr.insertIntoByteBuffer(header);
            header.flip();

            clientData.header = header;
            clientData.body = body;
            clientData.hdr = hdr;
        } catch (UnsupportedEncodingException ue){
            LOG.error("Exception in RequestCancelQuery: " + s.getRemoteSocketAddress() + ": " + ue.getMessage() );
            cancelConnection = true;
        }
        header = null;
        body = null;
        hdr = null;
        clientSocketAddress = null;
        exception = null;

        if (cancelConnection == true)
            clientData.requestReply = ListenerConstants.REQUST_CLOSE;
        else
            clientData.requestReply = ListenerConstants.REQUST_WRITE_EXCEPTION;
        reset();
        return clientData;
    }
}
