/**
 *  BlueCove - Java library for Bluetooth
 *  Copyright (C) 2007 Vlad Skarzhevskyy
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 *  @version $Id$
 */
package com.intel.bluetooth.obex;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import javax.obex.HeaderSet;
import javax.obex.Operation;

import com.intel.bluetooth.DebugLog;

abstract class OBEXClientOperation implements Operation {

	protected OBEXClientSessionImpl session;
	
	protected HeaderSet replyHeaders;
	
	protected HeaderSet sendHeaders;
	
	protected int sendHeadersLength = 0;
	
	protected boolean isClosed;
	
	protected boolean operationInProgress;
	
	protected boolean operationStarted;
	
	protected boolean outputStreamOpened = false;
	
	protected boolean inputStreamOpened = false;
	
	protected Object lock;
	
	OBEXClientOperation(OBEXClientSessionImpl session, HeaderSet replyHeaders) throws IOException {
		this.session = session;
		this.replyHeaders = replyHeaders;
		this.isClosed = false;
		this.lock = new Object();
		if (replyHeaders != null) {
			switch (replyHeaders.getResponseCode()) {
			case OBEXOperationCodes.OBEX_RESPONSE_SUCCESS:
			case OBEXOperationCodes.OBEX_RESPONSE_CONTINUE:
				this.operationInProgress = true;
				break;
			default:
				this.operationInProgress = false;
			}
		} else {
			this.operationInProgress = false;
		}
	}
	
	protected void writeAbort() throws IOException {
		try {
			session.writeOperation(OBEXOperationCodes.ABORT, null);
			byte[] b = session.readOperation();
			HeaderSet dataHeaders = OBEXHeaderSetImpl.readHeaders(b[0], b, 3);
			if (dataHeaders.getResponseCode() != OBEXOperationCodes.OBEX_RESPONSE_SUCCESS) {
				throw new IOException("Fails to abort operation");
			}
		} finally {
			this.isClosed = true;
			closeStream();
		}
	}

	abstract void started() throws IOException;
	
	abstract void closeStream() throws IOException;
	
	protected void validateOperationIsOpen()  throws IOException {
		if (isClosed) {
            throw new IOException("operation closed");
		}
	}
	
	/* (non-Javadoc)
	 * @see javax.obex.Operation#getReceivedHeaders()
	 */
	public HeaderSet getReceivedHeaders() throws IOException {
		validateOperationIsOpen();
		started();
		return OBEXHeaderSetImpl.cloneHeaders(this.replyHeaders);
	}

	/* (non-Javadoc)
	 * @see javax.obex.Operation#getResponseCode()
	 * 
	 *  A call will do an implicit close on the Stream and therefore signal that the request is done.
	 */
	public int getResponseCode() throws IOException {
		validateOperationIsOpen();
		started();
		closeStream();
		return this.replyHeaders.getResponseCode();
	}

	public void sendHeaders(HeaderSet headers) throws IOException {
		if (headers == null) {
			throw new NullPointerException("headers are null");
		}
		OBEXHeaderSetImpl.validateCreatedHeaderSet(headers);
		validateOperationIsOpen();
		if ((this.operationStarted) && (!this.operationInProgress)) {
			throw new IOException("the transaction has already ended");
		}
		synchronized (lock) {
			sendHeaders = headers;
			sendHeadersLength = OBEXHeaderSetImpl.toByteArray(sendHeaders).length;
		}
	}

	/* (non-Javadoc)
	 * @see javax.microedition.io.ContentConnection#getEncoding()
	 * <code>getEncoding()</code> will always return <code>null</code>
	 */
	public String getEncoding() {
		return null;
	}

	/* (non-Javadoc)
	 * @see javax.microedition.io.ContentConnection#getLength()
	 * <code>getLength()</code> will return the length specified by the OBEX
     * Length header or -1 if the OBEX Length header was not included.
	 */
	public long getLength() {
		Long len;
		try {
			len = (Long)replyHeaders.getHeader(HeaderSet.LENGTH);
		} catch (IOException e) {
			return -1;
		}
		if (len == null) {
			return -1;
		}
		return len.longValue();
	}

	/* (non-Javadoc)
	 * @see javax.microedition.io.ContentConnection#getType()
	 * <code>getType()</code> will return the value specified in the OBEX Type
     * header or <code>null</code> if the OBEX Type header was not included.
	 */
	public String getType() {
		try {
			return (String)replyHeaders.getHeader(HeaderSet.TYPE);
		} catch (IOException e) {
			return null;
		}
	}

	public DataInputStream openDataInputStream() throws IOException {
		 return new DataInputStream(openInputStream());
	}

	public DataOutputStream openDataOutputStream() throws IOException {
		return new DataOutputStream(openOutputStream());
	}

	/* (non-Javadoc)
	 * @see javax.microedition.io.Connection#close()
	 */
	public void close() throws IOException {
		started();
		closeStream();
		if (!this.isClosed) {
			this.isClosed = true;
			DebugLog.debug("operation closed");
		}
	}

	public boolean isClosed() {
		return this.isClosed;
	}
}
