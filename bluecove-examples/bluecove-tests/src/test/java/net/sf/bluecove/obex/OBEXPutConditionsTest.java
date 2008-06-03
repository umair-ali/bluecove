/**
 *  BlueCove - Java library for Bluetooth
 *  Copyright (C) 2008 Vlad Skarzhevskyy
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
package net.sf.bluecove.obex;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.microedition.io.Connector;
import javax.obex.ClientSession;
import javax.obex.HeaderSet;
import javax.obex.Operation;
import javax.obex.ResponseCodes;
import javax.obex.ServerRequestHandler;

import com.intel.bluetooth.DebugLog;
import com.intel.bluetooth.obex.BlueCoveInternals;

/**
 * @author vlads
 * 
 */
public class OBEXPutConditionsTest extends OBEXBaseEmulatorTestCase {

	private int serverDataLength;

	private byte[] serverData;

	private static long LENGTH_NO_DATA = 0xffffffffl;

	@Override
	protected void setUp() throws Exception {
		super.setUp();
		serverDataLength = -1;
		serverData = null;
	}

	private class RequestHandler extends ServerRequestHandler {

		@Override
		public int onPut(Operation op) {
			try {
				serverRequestHandlerInvocations++;
				DebugLog.debug("serverRequestHandlerInvocations", serverRequestHandlerInvocations);
				if (serverRequestHandlerInvocations > 1) {
					return ResponseCodes.OBEX_HTTP_BAD_REQUEST;
				}
				serverHeaders = op.getReceivedHeaders();
				Long dataLength = (Long) serverHeaders.getHeader(HeaderSet.LENGTH);
				if (dataLength == null) {
					return ResponseCodes.OBEX_HTTP_LENGTH_REQUIRED;
				}
				long length = dataLength.longValue();
				int len = (int) length;
				if (length != LENGTH_NO_DATA) {
					InputStream is = op.openInputStream();
					serverData = new byte[len];
					int got = 0;
					// read fully
					while (got < len) {
						int rc = is.read(serverData, got, len - got);
						if (rc < 0) {
							break;
						}
						got += rc;
					}
					is.close();
					serverDataLength = got;
				}
				op.close();
				return ResponseCodes.OBEX_HTTP_OK;
			} catch (IOException e) {
				e.printStackTrace();
				return ResponseCodes.OBEX_HTTP_UNAVAILABLE;
			}
		}
	}

	@Override
	protected ServerRequestHandler createRequestHandler() {
		return new RequestHandler();
	}

	private void runPUTOperation(boolean flush, long length, byte[] data1, byte[] data2, int expectedPackets)
			throws IOException {

		ClientSession clientSession = (ClientSession) Connector.open(selectService(serverUUID));
		HeaderSet hsConnectReply = clientSession.connect(null);
		assertEquals("connect", ResponseCodes.OBEX_HTTP_OK, hsConnectReply.getResponseCode());
		int writePacketsConnect = BlueCoveInternals.getPacketsCountWrite(clientSession);

		HeaderSet hsOperation = clientSession.createHeaderSet();

		hsOperation.setHeader(HeaderSet.LENGTH, new Long(length));

		// Create PUT Operation
		Operation putOperation = clientSession.put(hsOperation);

		OutputStream os = putOperation.openOutputStream();
		os.write(data1);
		if (flush) {
			DebugLog.debug("client flush 1");
			os.flush();
		}
		if (data2 != null) {
			os.write(data2);
			if (flush) {
				DebugLog.debug("client flush 2");
				os.flush();
			}
		}
		DebugLog.debug("client OutputStream close");
		os.close();

		DebugLog.debug("client Operation close");
		putOperation.close();

		DebugLog.debug("PUT packets", BlueCoveInternals.getPacketsCountWrite(clientSession) - writePacketsConnect);

		DebugLog.debug("client Session disconnect");
		clientSession.disconnect(null);

		clientSession.close();

		assertEquals("invocations", 1, serverRequestHandlerInvocations);
		assertEquals("LENGTH", new Long(length), serverHeaders.getHeader(HeaderSet.LENGTH));
		assertEquals("data.length", data1.length, serverDataLength);

		assertEquals("c.writePackets", expectedPackets, BlueCoveInternals.getPacketsCountWrite(clientSession));
		assertEquals("c.readPackets", expectedPackets, BlueCoveInternals.getPacketsCountRead(clientSession));
		assertEquals("s.writePackets", expectedPackets, BlueCoveInternals
				.getPacketsCountWrite(serverAcceptedConnection));
		assertEquals("s.readPackets", expectedPackets, BlueCoveInternals.getPacketsCountRead(serverAcceptedConnection));
	}

	/**
	 * Verify that server can read data without getting to the exact end of file
	 * in InputStream
	 */
	public void testPUTOperationComplete() throws IOException {
		byte data[] = simpleData;
		int expectedPackets = 1 + 2 + 1;
		runPUTOperation(false, data.length, data, null, expectedPackets);

		assertEquals("data", data, serverData);
	}

	public void testPUTOperationCompleteBigData() throws IOException {
		throw new IOException("TODO");
	}

	/**
	 * Verify that call to flush do cause double invocation for onPut
	 */
	public void testPUTOperationCompleteFlush() throws IOException {
		byte data[] = simpleData;

		int expectedPackets = 1 + 2 + 1 + 1;
		runPUTOperation(true, data.length, data, null, expectedPackets);

		assertEquals("data", data, serverData);
	}

	public void testPUTOperationCompleteFlushBigData() throws IOException {
		throw new IOException("TODO");
	}

	public void testPUTOperationSendMore() throws IOException {
		byte data[] = simpleData;

		int expectedPackets = 1 + 2 + 1;
		runPUTOperation(false, data.length, data, "More".getBytes("iso-8859-1"), expectedPackets);

		assertEquals("data", data, serverData);
	}

	public void testPUTOperationSendMoreBigData() throws IOException {
		throw new IOException("TODO");
	}

	public void testPUTOperationSendLess() throws IOException {
		byte data[] = simpleData;
		int less = 4;

		int expectedPackets = 1 + 2 + 1;
		runPUTOperation(false, data.length + less, data, null, expectedPackets);

		assertEquals("data", data.length, data, serverData);
	}

	public void testPUTOperationSendLessBigData() throws IOException {
		throw new IOException("TODO");
	}

	/**
	 * No data in Operation OutputStream
	 */
	public void testPUTOperationNoData() throws IOException {

		ClientSession clientSession = (ClientSession) Connector.open(selectService(serverUUID));
		HeaderSet hsConnectReply = clientSession.connect(null);
		assertEquals("connect", ResponseCodes.OBEX_HTTP_OK, hsConnectReply.getResponseCode());
		int writePacketsConnect = BlueCoveInternals.getPacketsCountWrite(clientSession);

		HeaderSet hs = clientSession.createHeaderSet();
		String name = "Hello.txt";
		hs.setHeader(HeaderSet.NAME, name);
		hs.setHeader(HeaderSet.LENGTH, new Long(LENGTH_NO_DATA));

		// Create PUT Operation
		Operation putOperation = clientSession.put(hs);

		OutputStream os = putOperation.openOutputStream();
		os.close();

		putOperation.close();

		DebugLog.debug("PUT packets", BlueCoveInternals.getPacketsCountWrite(clientSession) - writePacketsConnect);

		clientSession.disconnect(null);

		clientSession.close();

		assertEquals("NAME", name, serverHeaders.getHeader(HeaderSet.NAME));
		assertNull("data", serverData);
		assertEquals("invocations", 1, serverRequestHandlerInvocations);

		int expectedPackets = 1 + 2 + 1;

		assertEquals("c.writePackets", expectedPackets, BlueCoveInternals.getPacketsCountWrite(clientSession));
		assertEquals("c.readPackets", expectedPackets, BlueCoveInternals.getPacketsCountRead(clientSession));
		assertEquals("s.writePackets", expectedPackets, BlueCoveInternals
				.getPacketsCountWrite(serverAcceptedConnection));
		assertEquals("s.readPackets", expectedPackets, BlueCoveInternals.getPacketsCountRead(serverAcceptedConnection));
	}
}