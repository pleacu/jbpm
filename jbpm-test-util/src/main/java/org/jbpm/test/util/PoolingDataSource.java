/*
 * Copyright 2017 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
*/

package org.jbpm.test.util;

import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Properties;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;
import javax.sql.XADataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arjuna.ats.jdbc.TransactionalDriver;
import com.arjuna.ats.jta.TransactionManager;


public class PoolingDataSource implements DataSource {
    
    private static final Logger logger = LoggerFactory.getLogger(PoolingDataSource.class); 
    
    private final TransactionalDriver transactionalDriver = new TransactionalDriver();
    private Properties driverProperties = new Properties();
    private String uniqueName;
    private String className;
    private XADataSource xads;
    private Connection connection;

    public PoolingDataSource() {

    }

    public Properties getDriverProperties() {

        return driverProperties;
    }
    
    public void setDriverProperties(Properties driverProperties) {
        this.driverProperties = driverProperties;
    }

    public void init()  {
        try {
            xads = (XADataSource)Class.forName(className).newInstance();
            String url = driverProperties.getProperty("url", driverProperties.getProperty("URL"));
            xads.getClass().getMethod("setURL", new Class[] {String.class}).invoke(xads, new Object[] {url});
            
            try {
                InitialContext initContext = new InitialContext();
                initContext.rebind(uniqueName, this);
                
                initContext.rebind("java:comp/UserTransaction", com.arjuna.ats.jta.UserTransaction.userTransaction());
                initContext.rebind("java:comp/TransactionManager", TransactionManager.transactionManager());
                initContext.rebind("java:comp/TransactionSynchronizationRegistry", new com.arjuna.ats.internal.jta.transaction.arjunacore.TransactionSynchronizationRegistryImple());
            } catch (NamingException e) {
                logger.warn("No InitialContext available, resource won't be accessible via lookup");
            }
            // Keep the connection open - this is important because otherwise H2 will delete the tables
            // DB_CLOSE_DELAY can't be used or the tests interfere with each other
            connection =  getConnection(driverProperties.getProperty("user"), driverProperties.getProperty("password"));
        } catch (SQLException | InvocationTargetException | NoSuchMethodException | IllegalAccessException | InstantiationException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public void close() {
        try {
            connection.close();
            new InitialContext().unbind(uniqueName);
        } catch (SQLException | NamingException e) {
            throw new RuntimeException(e);
        }
    }

    public void setUniqueName(String uniqueName) {
        this.uniqueName = uniqueName;
    }

    public String getUniqueName() {
        return uniqueName;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public Connection getConnection() throws SQLException {
        if (driverProperties.getProperty("user") != null) {
            return getConnection(driverProperties.getProperty("user"), driverProperties.getProperty("password"));
        } else {
            Properties properties = new Properties();
            properties.put(TransactionalDriver.XADataSource, this.xads);
            return transactionalDriver.connect("jdbc:arjuna:" + uniqueName, properties);
        }
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        Properties properties = new Properties();
        properties.put(TransactionalDriver.XADataSource, this.xads);
        properties.put(TransactionalDriver.userName, username);
        if (password != null) {
            properties.put(TransactionalDriver.password, password);
        }

        return transactionalDriver.connect("jdbc:arjuna:" + uniqueName, properties);
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        return null;
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return false;
    }

    @Override
    public PrintWriter getLogWriter() throws SQLException {
        return null;
    }

    @Override
    public void setLogWriter(PrintWriter out) throws SQLException {

    }

    @Override
    public void setLoginTimeout(int seconds) throws SQLException {

    }

    @Override
    public int getLoginTimeout() throws SQLException {
        return 0;
    }

    @Override
    public java.util.logging.Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return null;
    }
}