 /**********************************************************************
 **                                                                   **
 **               This code belongs to the KETTLE project.            **
 **                                                                   **
 ** Kettle, from version 2.2 on, is released into the public domain   **
 ** under the Lesser GNU Public License (LGPL).                       **
 **                                                                   **
 ** For more details, please read the document LICENSE.txt, included  **
 ** in this project                                                   **
 **                                                                   **
 ** http://www.kettle.be                                              **
 ** info@kettle.be                                                    **
 **                                                                   **
 **********************************************************************/
 
package org.pentaho.di.trans.steps.http;

import java.io.InputStream;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.methods.GetMethod;
import org.pentaho.di.core.Const;
import org.pentaho.di.core.exception.KettleException;
import org.pentaho.di.core.exception.KettleStepException;
import org.pentaho.di.core.exception.KettleValueException;
import org.pentaho.di.core.row.RowDataUtil;
import org.pentaho.di.core.row.RowMetaInterface;
import org.pentaho.di.core.row.ValueDataUtil;
import org.pentaho.di.trans.Trans;
import org.pentaho.di.trans.TransMeta;
import org.pentaho.di.trans.step.BaseStep;
import org.pentaho.di.trans.step.StepDataInterface;
import org.pentaho.di.trans.step.StepInterface;
import org.pentaho.di.trans.step.StepMeta;
import org.pentaho.di.trans.step.StepMetaInterface;


/**
 * Retrieves values from a database by calling database stored procedures or functions
 *  
 * @author Matt
 * @since 26-apr-2003
 */
public class HTTP extends BaseStep implements StepInterface
{
	private HTTPMeta meta;
	private HTTPData data;
	
	public HTTP(StepMeta stepMeta, StepDataInterface stepDataInterface, int copyNr, TransMeta transMeta, Trans trans)
	{
		super(stepMeta, stepDataInterface, copyNr, transMeta, trans);
	}
	
	private Object[] execHttp(RowMetaInterface rowMeta, Object[] row) throws KettleException
	{
        if (first)
		{
			first=false;
			data.argnrs=new int[meta.getArgumentField().length];
			
			for (int i=0;i<meta.getArgumentField().length;i++)
			{
				data.argnrs[i]=rowMeta.indexOfValue(meta.getArgumentField()[i]);
				if (data.argnrs[i]<0)
				{
					logError(Messages.getString("HTTP.Log.ErrorFindingField")+meta.getArgumentField()[i]+"]"); //$NON-NLS-1$ //$NON-NLS-2$
					throw new KettleStepException(Messages.getString("HTTP.Exception.CouldnotFindField",meta.getArgumentField()[i])); //$NON-NLS-1$ //$NON-NLS-2$
				}
			}
		}

        return callHttpService(rowMeta, row);
	}
	
	private Object[] callHttpService(RowMetaInterface rowMeta, Object[] rowData) throws KettleException
    {
        String url = determineUrl(rowMeta, rowData);
        try
        {
            logDetailed("Connecting to : ["+url+"]");
            
            // Prepare HTTP get
            // 
            HttpClient httpclient = new HttpClient();
            HttpMethod method = new GetMethod(url);

            // Execute request
            // 
            try
            {
                int result = httpclient.executeMethod(method);
                
                // The status code
                if (log.isDebug()) log.logDebug(toString(), "Response status code: " + result);
                
                // the response
                InputStream inputStream = method.getResponseBodyAsStream();
                StringBuffer bodyBuffer = new StringBuffer();
                int c;
                while ( (c=inputStream.read())!=-1) bodyBuffer.append((char)c);
                inputStream.close();
                
                String body = bodyBuffer.toString();
                if (log.isDebug()) log.logDebug(toString(), "Response body: "+body);
                
                return RowDataUtil.addValueData(rowData, rowMeta.size(), body);
            }
            finally
            {
                // Release current connection to the connection pool once you are done
                method.releaseConnection();
            }
        }
        catch(Exception e)
        {
            throw new KettleException("Unable to get result from specified URL :"+url, e);
        }
    }

    private String determineUrl(RowMetaInterface outputRowMeta, Object[] row) throws KettleValueException
    {
        StringBuffer url = new StringBuffer(meta.getUrl()); // the base URL
        
        for (int i=0;i<data.argnrs.length;i++)
        {
            if (i==0)
            {
                url.append('?');
            }
            else
            {
                url.append('&');
            }
            url.append(meta.getArgumentParameter()[i]);
            url.append('=');
            
            String s = outputRowMeta.getString(row, data.argnrs[i]);
            if ( s != null )
            	s = ValueDataUtil.replace(s, " ", "%20");
            url.append(s);
        }
        
        // TODO: maybe do some more replacement of "forbidden" characters
        
        return url.toString();
    }

    public boolean processRow(StepMetaInterface smi, StepDataInterface sdi) throws KettleException
	{
		meta=(HTTPMeta)smi;
		data=(HTTPData)sdi;

		Object[] r=getRow();     // Get row from input rowset & set row busy!
		if (r==null)  // no more input to be expected...
		{
			setOutputDone();
			return false;
		}
		
		if ( first )
		{
			data.outputRowMeta = getInputRowMeta().clone();
			meta.getFields(data.outputRowMeta, getStepname(), null, null, this);
		}
		    
		try
		{
			Object[] outputRowData = execHttp(getInputRowMeta(), r); // add new values to the row
			putRow(data.outputRowMeta, outputRowData);  // copy row to output rowset(s);
				
            if (checkFeedback(linesRead)) logBasic(Messages.getString("HTTP.LineNumber")+linesRead); //$NON-NLS-1$
		}
		catch(KettleException e)
		{
			logError(Messages.getString("HTTP.ErrorInStepRunning")+e.getMessage()); //$NON-NLS-1$
			setErrors(1);
			stopAll();
			setOutputDone();  // signal end to receiver(s)
			return false;
		}
			
		return true;
	}
	
	public boolean init(StepMetaInterface smi, StepDataInterface sdi)
	{
		meta=(HTTPMeta)smi;
		data=(HTTPData)sdi;

		if (super.init(smi, sdi))
		{
		    return true;
		}
		return false;
	}
		
	public void dispose(StepMetaInterface smi, StepDataInterface sdi)
	{
	    meta = (HTTPMeta)smi;
	    data = (HTTPData)sdi;
	    
	    super.dispose(smi, sdi);
	}
	
	public String toString()
	{
		return this.getClass().getName();
	}
	
	//
	// Run is were the action happens!
	public void run()
	{
		try
		{
			logBasic(Messages.getString("System.Log.StartingToRun")); //$NON-NLS-1$
			
			while (processRow(meta, data) && !isStopped());
		}
		catch(Throwable t)
		{
			logError(Messages.getString("System.Log.UnexpectedError")+" : "); //$NON-NLS-1$ //$NON-NLS-2$
            logError(Const.getStackTracker(t));
            setErrors(1);
			stopAll();
		}
		finally
		{
			dispose(meta, data);
			logSummary();
			markStop();
		}
	}
}