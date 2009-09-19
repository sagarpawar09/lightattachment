package com.lightattachment.smtp.streamed;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;

import org.apache.log4j.Logger;

import com.lightattachment.mails.LightAttachment;
import com.lightattachment.smtp.SmtpActionType;
import com.lightattachment.smtp.SmtpRequest;
import com.lightattachment.smtp.SmtpResponse;
import com.lightattachment.smtp.SmtpState;
import com.lightattachment.stats.SendErrorReportThread;
import com.lightattachment.stats.StoppableThread;

/** 
 * The SMTP Server listening for Postfix <code>smtp</code> connections.
 * 
 * @author Benoit Giannangeli
 * @version 0.1a
 * 
 */

public class StreamedSMTPPostfixInputConnector extends Thread {

	/** Indicates whether this server is stopped or not. */
	private volatile boolean stopped = true;

	/** Handle to the server socket this server listens to. */
	private ServerSocket serverSocket;

	/** Message received are pushed to the <code>StreamedMailParser</code>. */
	private StreamedMailParser streamedParser;

	/** <code>ClientThread</code> pool. */
	private ArrayList<ClientThread> clientPool;
	
	/** Port the server listens on. */
	private int port;

	/** Timeout listening on server socket. */
	private int timeout = 500;

	/** Count <code>ClientThread</code> running instance. */
	public static int clientRunning = 0;
	
	/** Logger used to trace activity. */
	static Logger log = Logger.getLogger(StreamedSMTPPostfixInputConnector.class);

	/** Build a <code>StreamedSMTPPostfixInputConnector</code>.
	 * @param parser the parser. */
	public StreamedSMTPPostfixInputConnector(StreamedMailParser parser) throws IOException {
		this.port = LightAttachment.config.getInt("postfix.in-address[@port]");
		this.timeout = LightAttachment.config.getInt("postfix.in-address[@timeout]");
		this.streamedParser = parser;
		this.clientPool = new ArrayList<ClientThread>();
	}

	/** Main loop of the SMTP server. 
	 * Accept connection and use a <code>ClientThread</code> for each one. */
	public void run() {
		stopped = false;
		try {
			try {
				serverSocket = new ServerSocket(port);
				log.info("LightAttachment service listening on port " + port +" (v. 2.1)");
				serverSocket.setSoTimeout(timeout); // Block for maximum of 1.5 seconds
			} finally {
				synchronized (this) {
					// Notify when server socket has been created
					notifyAll();
				}
			}

			log.info("LightAttachment service started");
			// Server: loop until stopped
			while (!isStopped()) {
				// Start server socket and listen for client connections
				Socket socket = null;
				try {
					socket = serverSocket.accept();
					log.info("SMTPPostfixInputConnector accepted connection from ("+socket.hashCode()+")"+ socket.getInetAddress());
					//ClientThread ct = new ClientThread(socket);
					//ct.start();
					ClientThread ct = selectClient();
					ct.setSocket(socket);
				} catch (Exception e) {
					if (socket != null) {
						socket.close();
					}
					continue; // Non-blocking socket timeout occurred: try accept() again
				}	
			}
		} catch (IOException e) {
			log.error(e.getMessage(),e);
			e.printStackTrace();
			SendErrorReportThread sert = new SendErrorReportThread(null,
					"StreamedSMTPPostfixInputConnector has been stopped.",e);
			sert.start();
		} finally {
			if (serverSocket != null) {
				try {
					serverSocket.close();
				} catch (IOException e) {
					e.printStackTrace();
					log.error(e.getMessage(),e);
					SendErrorReportThread sert = new SendErrorReportThread(null,
							"StreamedSMTPPostfixInputConnector has been stopped: server socket couldn't be initialized",e);
					sert.start();
				}
			}
		}
	}

	/** Select a <code>ClientThread</code> in <code>clientPool</code>.
	 * If the pool is empty, creates a new thread and return it.
	 * Else if one thread is free, return it.
	 * Else creates a new thread and return it.
	 * @return the selected <code>ClientThread</code> */
	public ClientThread selectClient() {
		if (clientPool.size() == 0) {
			ClientThread ct = new ClientThread(null);
			ct.start();
			clientPool.add(ct);
			return ct;
		} else {			
			for (ClientThread c : clientPool) if (c.getSocket() == null) return c;
			ClientThread ct = new ClientThread(null);
			ct.start();
			clientPool.add(ct);
			return ct;
		}
	}
	
	/** Check if the server has been placed in a stopped state. Allows another thread to
	 * stop the server safely.
	 * @return true if the server has been sent a stop signal, false otherwise. */
	public synchronized boolean isStopped() {
		return stopped;
	}

	/** Stops the server. Server is shutdown after processing of the current request is complete. 
	 * @throws IOException */
	public synchronized void shutdown() throws IOException {
		for (ClientThread c : clientPool) c.shutdown();
		// Mark us closed
		stopped = true;
		try {
			// Kick the server accept loop
			serverSocket.close();
			log.info("LightAttachment service stopped");
		} catch (IOException e) {
			log.error(e.getMessage(),e);
		}
	}

	/** Handle transaction for a single connection in a single thread. */
	private class ClientThread extends StoppableThread {
		
		/** The socket to read on. */
		private Socket socket;				
		
		/** Build a <code>ClientThread</code> from scratch. */
		public ClientThread(Socket socket) {
			super();
			this.socket = socket;
			clientRunning++;
			System.err.println("ClientThread++ count: "+clientRunning);
		}
		
		public Socket getSocket() {
			return socket;
		}

		public void setSocket(Socket socket) {
			this.socket = socket;
		}

		/** Safely shutdown the instance. */
		public void shutdown() throws IOException {
			clientRunning--;
			System.err.println("ClientThread-- count: "+clientRunning);
			setDone(true);
			if (socket != null && !socket.isClosed()) socket.close();			
		}
		
		@Override
		public void run() {
			super.run();

			while (!isDone()) {
				if (socket != null) {
					try {
						// Get the input and output streams
						BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
						PrintWriter out = new PrintWriter(socket.getOutputStream());

						handleTransaction(out, input, socket);

						socket.close();
						log.info("SMTPPostfixInputConnector close connection with (" + socket.hashCode() + ")"
								+ socket.getInetAddress());
					} catch (IOException e) {
						log.error(e.getMessage(), e);
						e.printStackTrace();
						SendErrorReportThread sert = new SendErrorReportThread(null,
								"StreamedSMTPPostfixInputConnector get error(s) while speaking to (" + socket.hashCode() + ")"
										+ socket.getInetAddress(), e);
						sert.start();
					} catch (InterruptedException e) {
						log.error(e.getMessage(), e);
						e.printStackTrace();
						SendErrorReportThread sert = new SendErrorReportThread(null,
								"StreamedSMTPPostfixInputConnector get error(s) while speaking to (" + socket.hashCode() + ")"
										+ socket.getInetAddress(), e);
						sert.start();
					}
					socket = null;
				} else {
					try {
						sleep(100);
					} catch (InterruptedException e) {
						log.error(e.getMessage(), e);
						e.printStackTrace();
						SendErrorReportThread sert = new SendErrorReportThread(null,
								"StreamedSMTPPostfixInputConnector get error(s) while speaking to (" + socket.hashCode() + ")"
										+ socket.getInetAddress(), e);
						sert.start();
					}
				}
			}

			setDone(true);

		}

		/**
		 * Handle SMTP transactions.
		 * 
		 * @param out output stream.
		 * @param input input stream.
		 * @param socket socket to read on.
		 */
		private void handleTransaction(PrintWriter out, BufferedReader input, Socket socket) throws IOException, InterruptedException {
			// Initialize the state machine
			SmtpState smtpState = SmtpState.CONNECT;
			SmtpRequest smtpRequest = new SmtpRequest(SmtpActionType.CONNECT, "", smtpState);

			// Execute the connection request
			SmtpResponse smtpResponse = smtpRequest.execute();

			// Send initial response
			sendResponse(out, smtpResponse);
			smtpState = smtpResponse.getNextState();

			StreamedSmtpMessage msg = new StreamedSmtpMessage(LightAttachment.config.getString("directory.temp") + System.nanoTime());

			String from = "";
			String to = "";
			boolean goon = true;
			while (smtpState != SmtpState.CONNECT && !socket.isClosed() && socket.isConnected() && goon) {
				
				String line = input.readLine();

				if (line == null) {
					break;
				}

				// Create request from client input and current state
				SmtpRequest request = SmtpRequest.createRequest(line, smtpState);
				// Execute request and create response object
				SmtpResponse response = request.execute();
				
				if (msg.isFull()) {
					response = new SmtpResponse(552,
							"Requested mail action aborted by LightAttachment: exceeded storage allocation",response.getNextState());
					goon = false;
				}
				
				// Move to next internal state
				smtpState = response.getNextState();
				// Send response to client
				sendResponse(out, response);			
				
				// Store input in message
				String params = request.getParams();		
				msg.store(response, params);

				if (request.getAction().equals(SmtpActionType.MAIL)) {
					from = request.getFrom();
				} else if (request.getAction().equals(SmtpActionType.RCPT)) {
					if (to == null || to.length() <= 0)
						to = request.getTo();
					else
						to += "," + request.getTo();
				}

				// If message reception is complete save it
				if (smtpState == SmtpState.QUIT) {
					if (from != null && to != null) {
						msg.setFrom(from);
						msg.setTo(to);
					}
					msg.end();
					log.info("("+socket.hashCode()+") received a message in "+(msg.getEnd()-msg.getBegin())+" ms");
					boolean pushed = streamedParser.push(msg);
					msg = new StreamedSmtpMessage(LightAttachment.config.getString("directory.temp") + System.nanoTime());
					to = null;
					if (!pushed) msg.setFull(true);
				}
			}
		}

		/** Send response to client.
		 * @param out output stream.
		 * @param smtpResponse response object. */
		private void sendResponse(PrintWriter out, SmtpResponse smtpResponse) {
			if (smtpResponse.getCode() > 0) {
				int code = smtpResponse.getCode();
				String message = smtpResponse.getMessage();
				out.print(code + " " + message + "\r\n");
				out.flush();
			}
		}
		
	}

}
