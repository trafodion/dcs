/*
# @@@ START COPYRIGHT @@@   
#   
# (C) Copyright 2013-2015 Hewlett-Packard Development Company, L.P.   
#   
#  Licensed under the Apache License, Version 2.0 (the "License");   
#  you may not use this file except in compliance with the License.   
#  You may obtain a copy of the License at   
#   
#      http://www.apache.org/licenses/LICENSE-2.0   
#   
#  Unless required by applicable law or agreed to in writing, software   
#  distributed under the License is distributed on an "AS IS" BASIS,   
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.   
#  See the License for the specific language governing permissions and   
#  limitations under the License.   
#   
# @@@ END COPYRIGHT @@@   
*/ 

import java.sql.*;
import java.util.*;
import java.io.*;
import org.junit.Test;
import static org.junit.Assert.*;


/*  The test case is added for bug #1452993;
 *  T2 don't read the property file from System Properties but T4 do it.
 *  
 *  The test need run with a property file, like t2prop. 
 *  java -Dprop=t2prop PropTest
 */
public class PropTest
{
    private static String propFile;
    private static String url;
    private static String hpjdbc_version;
    private static String usr;
    private static String pwd;
    private static String catalog;
    private static String schema;
    private static Properties props;

    static {
        try {
            propFile = System.getProperty("prop");
            if (propFile != null) {
                FileInputStream fs = new FileInputStream(new File(propFile));
                props = new Properties();
                props.load(fs);

                url = props.getProperty("url");
                usr = props.getProperty("user");
                pwd = props.getProperty("password");
                catalog = props.getProperty("catalog");
                schema = props.getProperty("schema");
                hpjdbc_version = props.getProperty("hpjdbc_version");
            } else {
                System.out.println("Error: prop is not set ...");
                System.exit(0);
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        try {
            Class.forName(hpjdbc_version.trim());
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println(e.getMessage());
            System.exit(0);
        }
    }

    @Test
    private static void  testDefaultPropertiesConnection() throws SQLException {
        Connection conn = null;
	try {
	    // The option -Dproperties=propFile can be used to instead of System.setProperty()
            System.setProperty("properties", propFile);

            conn = DriverManager.getConnection(url, usr, pwd);
	    System.out.println("Catalog : " + conn.getCatalog());
	    assertEquals("Catalog should be the same as the properties file defined",catalog, conn.getCatalog());
	    System.out.println("testDefaultPropertiesConnection : PASS");
	}
	catch (Exception e) {
	}
    }

    public static void main(String args[]) {
        PropTest t = new PropTest();
        try {
	    testDefaultPropertiesConnection();
        } catch (Exception e) {
            System.out.println("SQLException : " + e.getMessage());
            e.printStackTrace();
        }
    }
}
