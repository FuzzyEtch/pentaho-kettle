/*! ******************************************************************************
 *
 * Pentaho Data Integration
 *
 * Copyright (C) 2002-2016 by Pentaho : http://www.pentaho.com
 *
 *******************************************************************************
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 ******************************************************************************/

package org.pentaho.di.trans.steps.ssh;

import java.io.File;

import org.pentaho.di.core.Const;
import org.pentaho.di.core.exception.KettleException;
import org.pentaho.di.core.row.RowMetaInterface;
import org.pentaho.di.core.variables.VariableSpace;
import org.pentaho.di.i18n.BaseMessages;
import org.pentaho.di.trans.step.BaseStepData;
import org.pentaho.di.trans.step.StepDataInterface;

import com.trilead.ssh2.Connection;
import com.trilead.ssh2.HTTPProxyData;

/**
 * @author Samatar
 * @since 03-Juin-2008
 *
 */
public class SSHData extends BaseStepData implements StepDataInterface {
  public int indexOfCommand;
  public Connection conn;
  public boolean wroteOneRow;
  public String commands;
  public int nrInputFields;
  public int nrOutputFields;

  // Output fields
  public String stdOutField;
  public String stdTypeField;

  public RowMetaInterface outputRowMeta;

  public SSHData() {
    super();
    this.indexOfCommand = -1;
    this.conn = null;
    this.wroteOneRow = false;
    this.commands = null;
    this.stdOutField = null;
    this.stdTypeField = null;
  }

  public static Connection OpenConnection( String serveur, int port, String username, String password,
      boolean useKey, String keyFilename, String passPhrase, int timeOut, VariableSpace space, String proxyhost,
      int proxyport, String proxyusername, String proxypassword ) throws KettleException {
    Connection conn = null;
    boolean isAuthenticated = false;
    File keyFile = null;
    try {
      // perform some checks
      if ( useKey ) {
        if ( Const.isEmpty( keyFilename ) ) {
          throw new KettleException( BaseMessages.getString( SSHMeta.PKG, "SSH.Error.PrivateKeyFileMissing" ) );
        }
        keyFile = new File( keyFilename );
        if ( !keyFile.exists() ) {
          throw new KettleException( BaseMessages.getString( SSHMeta.PKG, "SSH.Error.PrivateKeyNotExist", keyFilename ) );
        }
      }
      // Create a new connection
      conn = new Connection( serveur, port );

      /* We want to connect through a HTTP proxy */
      if ( !Const.isEmpty( proxyhost ) ) {
        /* Now connect */
        // if the proxy requires basic authentication:
        if ( !Const.isEmpty( proxyusername ) ) {
          conn.setProxyData( new HTTPProxyData( proxyhost, proxyport, proxyusername, proxypassword ) );
        } else {
          conn.setProxyData( new HTTPProxyData( proxyhost, proxyport ) );
        }
      }

      // and connect
      if ( timeOut == 0 ) {
        conn.connect();
      } else {
        conn.connect( null, 0, timeOut * 1000 );
      }
      // authenticate
      if ( useKey ) {
        isAuthenticated =
          conn.authenticateWithPublicKey( username, keyFile, space.environmentSubstitute( passPhrase ) );
      } else {
        isAuthenticated = conn.authenticateWithPassword( username, password );
      }
      if ( isAuthenticated == false ) {
        throw new KettleException( BaseMessages.getString( SSHMeta.PKG, "SSH.Error.AuthenticationFailed", username ) );
      }
    } catch ( Exception e ) {
      // Something wrong happened
      // do not forget to disconnect if connected
      if ( conn != null ) {
        conn.close();
      }
      throw new KettleException( BaseMessages.getString( SSHMeta.PKG, "SSH.Error.ErrorConnecting", serveur, username ), e );
    }
    return conn;
  }
}
