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
package org.trafodion.dcs.servermt.serverDriverInputOutput;

import java.net.*;
import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.channels.spi.*;
import java.sql.SQLException;

import org.trafodion.dcs.Constants;
import org.trafodion.dcs.util.*;
import org.trafodion.dcs.servermt.ServerConstants;
import org.trafodion.dcs.servermt.ServerUtils;
import org.trafodion.dcs.servermt.serverHandler.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class SQLDataValue {
    private static final Log LOG = LogFactory.getLog(SQLDataValue.class);
    private int length;
    private byte[] buffer;
// ----------------------------------------------------------
    public void insertIntoByteBuffer(ByteBuffer bbBuf) throws UnsupportedEncodingException {
        if (buffer != null) {
            bbBuf.putInt(length);
            bbBuf.put(buffer, 0, length);
        } else
            bbBuf.putInt(0);
    }
// ----------------------------------------------------------
    public void extractFromByteBuffer(ByteBuffer bbBuf) throws UnsupportedEncodingException {
        length = bbBuf.getInt();
        if (length > 0) {
            ByteBufferUtils.extractByteArrayLen(bbBuf, length);
            bbBuf.get(); //null terminating
        }
    }
// ----------------------------------------------------------
    public int lengthOfData() {
        return (buffer != null) ? ServerConstants.INT_FIELD_SIZE + buffer.length + 1 : ServerConstants.INT_FIELD_SIZE;
    }
}
