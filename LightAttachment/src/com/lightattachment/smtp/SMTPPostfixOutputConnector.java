package com.lightattachment.smtp;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Writer;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.StringTokenizer;

import javax.mail.MessagingException;

import org.apache.commons.net.smtp.SMTPClient;
import org.apache.commons.net.smtp.SMTPConnectionClosedException;
import org.apache.commons.net.smtp.SMTPReply;
import org.apache.log4j.Logger;

import com.lightattachment.mails.MailSet;
import com.lightattachment.mails.StringOutputStream;
import com.lightattachment.stats.SendErrorReportThread;
import com.lightattachment.stats.StoppableThread;

/** 
 * Connect to the <code>smtpd</code> instance of Postfix to send back messages.
 * Messages and their envelopes are written to an input stream read by the <code>SMTPPostfixOutputConnector</code>. 
 *
 * @author Benoit Giannangeli
 * @version 0.1a
 * 
 */

public class SMTPPostfixOutputConnector extends StoppableThread {
		
	private ArrayList<MailSet> queue;
	
	/** Set to <code>false</code> to shutdown the <code>MailManager</code>. */
	private boolean working;
	
	/** The address of the Postfix <code>smtpd</code> instance. */
	private String SMTPAddress;
	
	/** The port of the Postfix <code>smtpd</code> instance. */
	private int port;
	
	private SMTPClient client;
	
	/** Logger used to trace activity. */
	static Logger log = Logger.getLogger(SMTPPostfixOutputConnector.class);
	
	/** Thread creation date. */
	private long created;
	
	/** Thread connection date. */
	private long connected;
	
	/** Count running instance. */
	public static int running = 0;
	
	/** Build a <code>SMTPPostfixOutputConnector</code> and connect to the specified address.
	 * @param pos the <code>PipedOutputStream</code> to connect on.
	 * @param SMTPAddress the Postfix <code>smtpd</code> address.
	 * @param port the Postfix <code>smtpd</code> port. 
	 * @throws IOException 
	 * @throws SocketException */
	public SMTPPostfixOutputConnector(String SMTPAddress, int port) throws SocketException, IOException {
		super();
		this.created = System.currentTimeMillis();
		this.working = true;
		this.SMTPAddress = SMTPAddress;
		this.port = port;
		this.queue = new ArrayList<MailSet>();
		running++;
		System.err.println("SMTPPostfixOutputConnector++ count: "+running);
		int reply;
		client = new SMTPClient();
		client.connect(SMTPAddress,port);
		this.connected = System.currentTimeMillis();
		log.info("SMTPPostfixOutputConnector("+this.hashCode()+") connected to "+client.getReplyString().replace("\r\n", "").replace("220 ", "")
				+" in "+(connected-created)+" ms");

		reply = client.getReplyCode();

		if (!SMTPReply.isPositiveCompletion(reply)) {
			client.disconnect();
			log.error("("+this.hashCode()+") Postfix SMTP server refused connection");
			SendErrorReportThread sert = new SendErrorReportThread(null,
					"Error while initiating connection to Postfix: Postfix SMTP server refused connection ("+this.hashCode()+")",null);
			sert.start();
			shutdown();
		} else {
			if (client.login()) {
				log.info("SMTPPostfixOutputConnector("+this.hashCode()+") logged in");
			}  else {
				client.disconnect();
				log.error("("+this.hashCode()+") Couldn't login Postfix SMTP Server");
				SendErrorReportThread sert = new SendErrorReportThread(null,
						"Error while initiating connection to Postfix: Couldn't login Postfix SMTP Server ("+this.hashCode()+")",null);
				sert.start();
				shutdown();
			}
		}
	}
	
	/** Reconnect the instance to Postfix. */
	public void reconnect() throws SocketException, IOException {
		int reply;
		client.connect(SMTPAddress,port);
		this.connected = System.currentTimeMillis();
		log.info("SMTPPostfixOutputConnector("+this.hashCode()+") reconnected to "+client.getReplyString().replace("\r\n", "").replace("220 ", "")
				+" in "+(connected-created)+" ms");

		reply = client.getReplyCode();

		if (!SMTPReply.isPositiveCompletion(reply)) {
			client.disconnect();
			log.error("("+this.hashCode()+") Postfix SMTP server refused connection");
			SendErrorReportThread sert = new SendErrorReportThread(null,
					"Error while initiating connection to Postfix: Postfix SMTP server refused connection ("+this.hashCode()+")",null);
			sert.start();
			shutdown();
		} else {
			if (client.login()) {
				log.info("SMTPPostfixOutputConnector("+this.hashCode()+") logged in");
			}  else {
				client.disconnect();
				log.error("("+this.hashCode()+") Couldn't login Postfix SMTP Server");
				SendErrorReportThread sert = new SendErrorReportThread(null,
						"Error while initiating connection to Postfix: Couldn't login Postfix SMTP Server ("+this.hashCode()+")",null);
				sert.start();
				shutdown();
			}
		}
	}
	
	/** Add a <code>MailSet</code> to the queue. 
	 * @param the <code>MailSet</code> to add */
	public void push(MailSet set) {
		if (set != null) queue.add(set);
	}
	
	/** Return the first <code>MailSet</code> of the queue.
	 * @return the first <code>MailSet</code> of the queue */
	public MailSet peek() {
		if (queue.size() > 0) {
			return queue.get(0);
		} else return null;
	}
	
	/** Return the queue size.
	 * @return the queue size */
	public int size() {
		return queue.size();
	}
	
	public void run() {
		super.run();
		try {
			while (working) {					
				MailSet set = peek();	
				
				if (set != null) {
					try {
						if (set.isSent()) set.setSent(false);
						
						if (!client.isConnected()) reconnect();
						if (set.isFromFile()) {
							for (String filename : set.getOriginalMessages()) send(set, filename);
						} else {
							try {
								StringBuffer buffer = new StringBuffer();
								set.getMessage().writeTo(new StringOutputStream(buffer));
								send(set, buffer);
							} catch (MessagingException e) {
								log.error(e.getMessage(), e);
								SendErrorReportThread sert = new SendErrorReportThread(set,
										"Error while injecting back to Postfix: " + e.getMessage(), e);
								sert.start();
								//shutdown();
							}
						}
						
						set.setSent(true);
						log.info("MailSet "+set.hashCode()+" set sented");
						queue.remove(set);
					} catch (SMTPConnectionClosedException c) {
						reconnect();
					}				
				}
				sleep(100);
			}
		} catch (IOException e) {
			log.error(e.getMessage(),e);
			e.printStackTrace();
			SendErrorReportThread sert = new SendErrorReportThread(null,
					"SMTPPostfixOutputConnector "+this.hashCode()+" stopped ",e);
			sert.start();
		} catch (InterruptedException e) {
			log.error(e.getMessage(),e);
			e.printStackTrace();
			SendErrorReportThread sert = new SendErrorReportThread(null,
					"SMTPPostfixOutputConnector "+this.hashCode()+" stopped ",e);
			sert.start();
		} 
		this.setDone(true);
	}

	/** Send to Postfix the modified mail.
	 * @param set the <code>MailSet</code> processed
	 * @param data the message to send */
	public void send(MailSet set, StringBuffer data) throws IOException {
		if (!set.isSent()) {
				boolean tok = client.setSender(set.getFrom());
				//System.err.println("TO: "+to);
				StringTokenizer st = new StringTokenizer(set.getTo(),",");
				while (st.hasMoreTokens()) {
					String rcpt = st.nextToken();
					//System.err.println("RCPT "+rcpt);
					if (!client.addRecipient(rcpt)) tok = false;
				}

				if (tok) {
					log.info("("+this.hashCode()+") Envelope sent");
					Writer w = client.sendMessageData();
					if (w != null) {
						
							w.write(data.toString().replace("\n","\r\n"));
						
						w.close();
						log.info("("+this.hashCode()+") Message sent");
						log.info("Modified mail "+set.hashCode()+" sent back to Postfix");
						if (!client.completePendingCommand()) {
							log.error("("+this.hashCode()+") Error while sending back message to Postfix SMTP Server");
							SendErrorReportThread sert = new SendErrorReportThread(set,
									"Error while injecting back to Postfix: error during data sending ("+this.hashCode()+")",null);
							sert.start();
							client.disconnect();
						}/* else if (!client.logout()) {
							log.error("("+this.hashCode()+") Error while logging out from Postfix SMTP Server");
							SendErrorReportThread sert = new SendErrorReportThread(set,
									"Error while injecting back to Postfix: Error while logging out from Postfix SMTP Server ("+this.hashCode()+")",null);
							sert.start();
							client.disconnect();
						}
						client.disconnect();
						log.info("SMTPPostfixOutputConnector("+this.hashCode()+") logged out");*/
					} else {
						log.error("("+this.hashCode()+") DATA command failed with Postfix SMTP Server");
						SendErrorReportThread sert = new SendErrorReportThread(set,
								"Error while injecting back to Postfix: DATA command failed with Postfix SMTP Server ("+this.hashCode()+")",null);
						sert.start();
					}
				} else {
					log.error("("+this.hashCode()+") MAIL or RCPT command failed with Postfix SMTP Server");
					SendErrorReportThread sert = new SendErrorReportThread(set,
							"Error while injecting back to Postfix: MAIL or RCPT command failed with Postfix SMTP Server ("+this.hashCode()+")",null);
					sert.start();
				}
		} else {
			System.err.println("** DOUBLE SENT **");
		}
	}
	
	/** Send to Postfix the unchanged mail (= from files).
	 * @param set the <code>MailSet</code> processed
	 * @param file the file to send */
	public void send(MailSet set, String file) throws SocketException, IOException {
				
		if (!set.isSent()) {
				boolean tok = client.setSender(set.getFrom());
				//System.err.println("TO: "+to);
				StringTokenizer st = new StringTokenizer(set.getTo(),",");
				while (st.hasMoreTokens()) {
					String rcpt = st.nextToken();
					//System.err.println("RCPT "+rcpt);
					if (!client.addRecipient(rcpt)) tok = false;
				}

				if (tok) {
					log.info("("+this.hashCode()+") Envelope sent");
					Writer w = client.sendMessageData();
					if (w != null) {
						
							BufferedReader br = new BufferedReader(new FileReader(file));
							
							String l = null;
							while ((l = br.readLine()) != null) {
								w.write(new String(l+"\n"));
							}
							
							br.close();
							long size = new File(file).length();
							if (new File(file).delete()) log.info("Temporary file "+file+" of "+size+" bytes deleted");
							else log.warn("Fail to delete temporary file "+file);
						
						w.close();
						log.info("("+this.hashCode()+") Message sent");
						log.info("Modified mail "+set.hashCode()+" sent back to Postfix");
						if (!client.completePendingCommand()) {
							log.error("("+this.hashCode()+") Error while sending back message to Postfix SMTP Server");
							SendErrorReportThread sert = new SendErrorReportThread(set,
									"Error while injecting back to Postfix: error during data sending ("+this.hashCode()+")",null);
							sert.start();
							client.disconnect();
						} /*else if (!client.logout()) {
							log.error("("+this.hashCode()+") Error while logging out from Postfix SMTP Server");
							SendErrorReportThread sert = new SendErrorReportThread(set,
									"Error while injecting back to Postfix: Error while logging out from Postfix SMTP Server ("+this.hashCode()+")",null);
							sert.start();
							client.disconnect();
						}
						client.disconnect();
						log.info("SMTPPostfixOutputConnector("+this.hashCode()+") logged out");*/
					} else {
						log.error("("+this.hashCode()+") DATA command failed with Postfix SMTP Server");
						SendErrorReportThread sert = new SendErrorReportThread(set,
								"Error while injecting back to Postfix: DATA command failed with Postfix SMTP Server ("+this.hashCode()+")",null);
						sert.start();
					}
				} else {
					log.error("("+this.hashCode()+") MAIL or RCPT command failed with Postfix SMTP Server");
					SendErrorReportThread sert = new SendErrorReportThread(set,
							"Error while injecting back to Postfix: MAIL or RCPT command failed with Postfix SMTP Server ("+this.hashCode()+")",null);
					sert.start();
				}
		} else {
			System.err.println("** DOUBLE SENT **");
		}

	}
	
	/** Safely shutdown the instance. */
	public void shutdown() throws IOException {
		running--;
		System.err.println("SMTPPostfixOutputConnector-- count: "+running);
		// Can block shutdown, so commented
		/*try {
			//client.logout();
			client.disconnect();
			log.info("SMTPPostfixOutputConnector(" + this.hashCode() + ") logged out");
			working = false;
		} catch (SMTPConnectionClosedException c) {}	*/
		setDone(true);
		log.info("SMTPPostfixOutputConnector("+this.hashCode()+") stopped after "+(getEnd()-getBegin())+" ms");
	}

	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public String getSMTPAddress() {
		return SMTPAddress;
	}

	public void setSMTPAddress(String address) {
		SMTPAddress = address;
	}
	
}
