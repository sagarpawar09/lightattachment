package com.lightattachment.smtp.streamed;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Properties;
import java.util.regex.Matcher;

import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;

import org.apache.log4j.Logger;

import com.lightattachment.mails.Base64;
import com.lightattachment.mails.LightAttachment;
import com.lightattachment.mails.MailManager;
import com.lightattachment.mails.MailSet;
import com.lightattachment.stats.SendErrorReportThread;
import com.lightattachment.stats.StoppableThread;

/**
 * Parse, reassemble, collect information and push messages to the <code>MailManager</code>.
 * 
 * @author Benoit Giannangeli
 * @version 0.1a
 * 
 */

public class StreamedMailParser extends Thread {

	/** The <code>MailManager</code> to send the <code>MailSet</code>s. */
	private MailManager manager;

	/** Hold incoming messages and keep together partial messages. */
	private ArrayList<ArrayList<StreamedSmtpMessage>> queue;

	/** Hold complete messages. */
	private ArrayList<ArrayList<StreamedSmtpMessage>> completeQueue;

	/** <code>ParserThread</code> pool. */
	private ArrayList<ParserThread> parserPool;
	
	/** Set to <code>false</code> to shutdown. */
	private boolean working;

	/** Count <code>ParserThread</code> running instance. */
	public static int parserRunning = 0;
	
	/** Logger used to trace activity. */
	static Logger log = Logger.getLogger(StreamedMailParser.class);
	
	/** <code>true</code> if partial messages must be processed. */
	private boolean processPartial;

	/**
	 * Build a <code>StreamedMailParser</code>.
	 * @param manager the <code>MailManager</code> to send the <code>MailSet</code>s.
	 */
	public StreamedMailParser(MailManager manager) {
		queue = new ArrayList<ArrayList<StreamedSmtpMessage>>();
		completeQueue = new ArrayList<ArrayList<StreamedSmtpMessage>>();
		this.manager = manager;
		this.working = true;
		processPartial = LightAttachment.config.getBoolean("message.process-partial");
		this.parserPool = new ArrayList<ParserThread>();
	}
	
	/** Select a <code>ParserThread</code> in <code>parserPool</code>.
	 * If the pool is empty, creates a new thread and return it.
	 * Else if one the thread is free, return it.
	 * Else if limit not reached, creates a new thread and return it.
	 * Else select the thread with the smaller queue and return it.
	 * @return the selected <code>ParserThread</code> */
	public ParserThread selectParser() {
		if (parserPool.size() == 0) {
			ParserThread pt = new ParserThread();
			pt.start();
			parserPool.add(pt);
			return pt;
		} else {
			ParserThread pt = null;
			for (ParserThread p : parserPool) {
				if (p.queue.size() == 0) return p;
				else if (pt == null) pt = p;
				else if (p.queue.size() < pt.queue.size()) pt = p;
			}
			
			if (pt.queue.size() > 0 && parserPool.size() < LightAttachment.config.getInt("message.output-limit")) {
				pt = new ParserThread();
				pt.start();
				parserPool.add(pt);
			}
			
			return pt;
		}
	}

	@Override
	public void run() {
		super.run();
		while (working) {
			try {
				ArrayList<StreamedSmtpMessage> bulk = pop();

				if (bulk != null) {
					//ParserThread pt = new ParserThread(bulk);
					//pt.start();
					ParserThread pt = selectParser();
					pt.push(bulk);
				}

				sleep(100);
			} catch (InterruptedException e) {
				log.error(e.getMessage(), e);
				e.printStackTrace();
			}
		}
	}

	/**
	 * Push a message to be parsed. When a message (whether partial or not) is complete, the messages bulk is pushed to the
	 * <code>completeQueue</code>. It also control that the message do not exceed the allowed message size specified by
	 * <code>lightattachment.xml</code>.
	 * 
	 * @param message
	 *            the message to push into.
	 * @return <code>false</code> if the message is too large.
	 */
	public synchronized boolean push(StreamedSmtpMessage message) {
		if (!message.isPartial()) {
			ArrayList<StreamedSmtpMessage> single = new ArrayList<StreamedSmtpMessage>();
			single.add(message);
			pushComplete(single);
			return true;
		} else {
			boolean found = false;
			ArrayList<StreamedSmtpMessage> list = null;
			for (int i = 0; i < queue.size(); i++) {
				list = queue.get(i);
				if (list.size() > 0 && list.get(0).isPartial() && list.get(0).getContentID().equals(message.getContentID())) {
					list.add(message);
					if (list.size() == getTotal(list))
						pushComplete(list);
					found = true;
				}
			}
			if (!found) {
				ArrayList<StreamedSmtpMessage> nlist = new ArrayList<StreamedSmtpMessage>();
				nlist.add(message);
				queue.add(nlist);
				return true;
			} else if (getSize(list) > LightAttachment.config.getLong("message.message-size.max-size")) {

				log.error("Message of size above " + LightAttachment.config.getLong("message.message-size.max-size")
						+ " not allowed");
				SendErrorReportThread sert = new SendErrorReportThread(new MailSet(null,message.getFrom(),message.getTo(),false),
						"Message of size above " + LightAttachment.config.getLong("message.message-size.max-size") + " not allowed",null);
				sert.start();
				return false;

			} else {
				return true;
			}
		}
	}

	/**
	 * Sort and push a messages bulk to the <code>completeQueue</code>.
	 * @param complete the messages bulk.
	 */
	private synchronized void pushComplete(ArrayList<StreamedSmtpMessage> complete) {
		queue.remove(complete);
		Collections.sort(complete);
		completeQueue.add(complete);
	}

	/**
	 * Return the first item of the <code>completeQueue</code> and removes it.
	 * @return the first item of the <code>completeQueue</code>.
	 */
	public synchronized ArrayList<StreamedSmtpMessage> pop() {
		if (completeQueue.size() > 0) {
			ArrayList<StreamedSmtpMessage> msg = this.completeQueue.get(0);
			this.completeQueue.remove(0);
			return msg;
		} else {
			return null;
		}
	}

	/**
	 * Return the size in bytes of a messages bulk.
	 * @param list the messages bulk.
	 * @return the bulk's size.
	 */
	private long getSize(ArrayList<StreamedSmtpMessage> list) {
		if (list != null) {
			long size = 0;
			for (StreamedSmtpMessage m : list) {
				size += new File(m.getFilename()).length();
			}

			return size;
		} else return 0;
	}

	/** Return the total size in bytes of a bulk.
	 * @param list the bulk.
	 * @return the total size of the bulk. */
	private int getTotal(ArrayList<StreamedSmtpMessage> list) {
		if (list != null) {
			int total = 0;
			for (StreamedSmtpMessage m : list) {
				if (!m.isPartial()) return 1;
				else if (m.getTotal() != -1) return m.getTotal();
			}

			return total;
		} else return -1;
	}
	
	/** Safely shutdown the instance. */
	public void shutdown() {
		for (ParserThread p : parserPool) p.shutdown();
		working = false;
		log.info("StreamedMailParser stopped");
	}

	/** This thread do the parsing job for a messages bulk. */
	private class ParserThread extends StoppableThread {

		/** Queue of bulks to parse. */
		private ArrayList<ArrayList<StreamedSmtpMessage>> queue;

		/** Parsing state: in the header of the mail. */
		private static final int HEADER = 0;

		/** Parsing state: in the body of the mail. */
		private static final int BODY = 1;

		/** Parsing state: in the header of a multipart. */
		private static final int M_HEADER = 2;

		/** Parsing state: in the body of a multipart. */
		private static final int M_BODY = 3;

		/** Parsing state: in the header of a partial mail. */
		private static final int P_HEADER = 4;

		/** Parsing state: in the body of a partial mail. */
		private static final int P_BODY = 5;

		/** Build a <code>ParserThread</code> */
		public ParserThread() {
			super();
			this.queue = new ArrayList<ArrayList<StreamedSmtpMessage>>();
			parserRunning++;
			System.err.println("ParserThread++ count: "+parserRunning);
		}
		
		/** Add a bulk to the queue.
		 * @param bulk the bulk to add */
		public void push(ArrayList<StreamedSmtpMessage> bulk) {
			if (bulk != null) queue.add(bulk);
		}
		
		/** Return the first bulk of the queue without removing it.
		 * @return the first bulk of the queue */
		public ArrayList<StreamedSmtpMessage> peek() {
			if (queue.size() > 0) return queue.get(0);
			else return null;
		}
		
		/** Safely shutdown the instance. */
		public void shutdown() {
			setDone(true);
			parserRunning--;
			System.err.println("ParserThread-- count: "+parserRunning);
		}
		
		@Override
		public void run() {
			super.run();

			while (!isDone()) {
				try {
					
					ArrayList<StreamedSmtpMessage> bulk = peek();
					if (bulk != null) {
						if (bulk.size() == 1 || processPartial) process(bulk);
						else forward(bulk);
						queue.remove(bulk);
					} else sleep(100);
					
				} catch (FileNotFoundException e) {
					log.error(e.getMessage(), e);
					e.printStackTrace();
					setDone(true);
				} catch (IOException e) {
					log.error(e.getMessage(), e);
					e.printStackTrace();
					setDone(true);
				} catch (MessagingException e) {
					log.error(e.getMessage(), e);
					setDone(true);
				} catch (InterruptedException e) {
					log.error(e.getMessage(), e);
					setDone(true);
				}
			}

		}

		/**
		 * Parse a messages bulk. Build a <code>MailSet</code> and push it to the <code>MailManager</code>. Attachments are
		 * reassembled and saved to a file. The message body is saved in the <code>MailSet</code>
		 * <code>message</code>. Too
		 * small or too large messages are set to be forwarded. If specified in <code>lightattachment.xml</code>, base64
		 * encoded attachment are decoded.
		 * 
		 * @param bulk the bulk to parse.
		 */
		public void process(ArrayList<StreamedSmtpMessage> bulk) throws IOException, MessagingException {

			long begin = System.currentTimeMillis();
			
			MailSet set = new MailSet();
			set.setFrom(bulk.get(0).getFrom());
			set.setTo(bulk.get(0).getTo());

			long size = 0;
			for (int i = 0; i < bulk.size(); i++) {
				size += new File(bulk.get(i).getFilename()).length();
			}

			if (size >= LightAttachment.config.getLong("message.message-size.min-size") && !bulk.get(0).isForward()) {				
				
				log.info("Got new mail from " + set.getFrom() + " to " + set.getTo() + " with id " + set.hashCode() + " in "
						+ bulk.size() + " partial mails of " + new File(bulk.get(0).getFilename()).length()
						+ " bytes for a total size of " + size + " bytes");
				if (bulk.size() > 1)
					log.warn("The message " + set.hashCode()
							+ " is splitted. This may cause security issues. See US-CERT note at http://www.kb.cert.org/vuls/id/836088 for details.");

				StringBuffer message = new StringBuffer();
				PrintWriter writer = null;
				String filename = null;
				String mimeType = null;

				ArrayList<String> currentPart = new ArrayList<String>();
				int encapsulateLevel = 0;
				int attachCount = 0;
				boolean waitEncapsulate = false;
				boolean waitContentName = false;
				boolean base64 = false;
				boolean stop = false;
				for (int i = 0; i < bulk.size() && !stop; i++) {
					boolean canStore = true;
					StringBuffer headerBuffer = new StringBuffer();

					StreamedSmtpMessage smessage = bulk.get(i);

					BufferedReader reader = new BufferedReader(new FileReader(smessage.getFilename()));

					String line = null;
					int state = HEADER;
					if (smessage.isPartial() && smessage.getNumber() != 1)
						state = P_HEADER;
					while (!stop && (line != null || (line = reader.readLine()) != null)) {
						
						if (message.length() > LightAttachment.config.getLong("message.message-size.in-memory-limit")) {
							stop = true;
							log.warn("Message "+set.hashCode()+" without attachment reached the in memory size limit of "
									+ LightAttachment.config.getLong("message.message-size.in-memory-limit")+" bytes and will be forwarded");
							SendErrorReportThread sert = new SendErrorReportThread(set,
									"Message "+set.hashCode()+" without attachment reached the in memory size limit of "
									+ LightAttachment.config.getLong("message.message-size.in-memory-limit")+" bytes and will be forwarded",null);
							sert.start();
						}
						
						switch (state) {
						case HEADER:
							if (line.length() == 0 && !waitEncapsulate) {
								if (smessage.isPartial()) {
									//System.err.println("*** SWITCH TO P_HEADER ***");
									state = P_HEADER;
								} else {
									//System.err.println("*** SWITCH TO BODY ***");
									state = BODY;
								}
								if (!smessage.isPartial()) {
									message.append("\n");
								}
							} else {
								if (waitEncapsulate)
									waitEncapsulate = false;
								// if (encapsulateLevel > 0) System.err.println("> "+line);
								boolean partial = false;
								Matcher partialMatcher = StreamedSmtpMessage.contentTypePattern.matcher(line);
								if (partialMatcher.find() && partialMatcher.group(1).equals("message/partial")) {
									partial = true;
								}
								
								if (currentPart.size() <= 0 || encapsulateLevel > 0) {
									Matcher matcher = StreamedSmtpMessage.boundaryPattern.matcher(line);
									if (matcher.find()) {
										//System.err.println("> " + matcher.group(1));
										currentPart.add(matcher.group(1));
									}
								}
								if (!partial) {
									// cf. RFC 1521. Outlook doesn't respect RFC and doesn't give the Message-ID field in the enclosed message
									// Note that we don't use the Message-ID for crucial purposes, we use the MailSet hashCode instead
									if (!smessage.isPartial()
											|| (!line.startsWith("Subject:") && !line.startsWith("Content-")
													&& !line.startsWith("MIME-Version") && !line.startsWith("Encrypted") /*&& !line
													.startsWith("Message-ID")*/)) {
										message.append(line + "\n");
									}
								}
							}
							line = null;
							break;
						case P_BODY:
						case M_BODY:
						case BODY:
							if (currentPart.size() <= 0 && !smessage.isPartial()) {
								
								message.append(line + "\n");
								
							} else if (currentPart.size() > 0 && line.endsWith("--" + currentPart.get(currentPart.size() - 1))) {
								
								//System.err.println("*** SWITCH TO M_HEADER ***");
								state = M_HEADER;
								headerBuffer.append(line + "\n");
								if (writer != null) {
									writer.close();
									writer = null;
									if (base64) {	
										Base64.decodeFileToFile(filename, filename + "-d");
										long fsize = new File(filename+"-d").length();
										new File(filename).delete();
										log.info("Mail " + set.hashCode() + " got attachment called '"
												+ set.getParts().get(filename + "-d") + "' of " + fsize + " bytes and MIME type "
												+ mimeType + " saved to '" + filename + "-d'");
									} else {
										new File(filename).renameTo(new File(filename + "-d"));
										log.info("Mail " + set.hashCode() + " got attachment called '"
												+ set.getParts().get(filename + "-d") + "' of "
												+ new File(filename + "-d").length() + " bytes and MIME type " + mimeType
												+ " saved to '" + filename + "-d'");
									}
									base64 = false;
									filename = null;
								}
								
							} else if (currentPart.size() > 0 && line.contains("--" + currentPart.get(currentPart.size() - 1) + "--")) {
								
								if (writer != null) {
									writer.close();
									writer = null;
									if (base64) {																				
										Base64.decodeFileToFile(filename, filename + "-d");
										long fsize = new File(filename+"-d").length();
										new File(filename).delete();
										log.info("Mail " + set.hashCode() + " got attachment called '"
												+ set.getParts().get(filename + "-d") + "' of " + fsize + " bytes and MIME type "
												+ mimeType + " saved to '" + filename + "-d'");
									} else {
										new File(filename).renameTo(new File(filename + "-d"));
										log.info("Mail " + set.hashCode() + " got attachment called '"
												+ set.getParts().get(filename + "-d") + "' of "
												+ new File(filename + "-d").length() + " bytes and MIME type " + mimeType
												+ " saved to '" + filename + "-d'");
									}
									base64 = false;
									filename = null;
									mimeType = null;
								}
								message.append(line + "\n");
								currentPart.remove(currentPart.size() - 1);
								if (currentPart.size() == 0) {
									if (encapsulateLevel > 0) {
										encapsulateLevel--;
										//System.err.println("*** SWITCH TO M_BODY ***");
										state = M_BODY;
									}
								}
								
							} else if (writer != null) {
								
								if (!base64)
									writer.print(line + "\n");
								else
									writer.print(line);
								
							} else if (canStore) {
								
								message.append(line + "\n");
								
							}
							line = null;
							break;
						case M_HEADER:
							if (line.length() == 0 && waitEncapsulate) {
								if (canStore) {
									message.append(headerBuffer.toString() + "\n");
									headerBuffer.delete(0, headerBuffer.length() - 1);
								}
								//System.err.println("*** SWITCH TO HEADER ***");
								state = HEADER;
							} else if (line.length() == 0) {
								if (canStore) {
									message.append(headerBuffer.toString() + "\n");
									headerBuffer.delete(0, headerBuffer.length() - 1);
								}
								//System.err.println("*** SWITCH TO M_BODY ***");
								state = M_BODY;
							} else {
								Matcher matcher = StreamedSmtpMessage.boundaryPattern.matcher(line);
								Matcher matcher64 = StreamedSmtpMessage.base64Pattern.matcher(line);
								Matcher nameMatcher = StreamedSmtpMessage.contentTypeNamePattern.matcher(line);
								Matcher contentMatcher = StreamedSmtpMessage.contentTypePattern.matcher(line);
								if (matcher.find()) {
									//System.err.println("> " + matcher.group(1));
									currentPart.add(matcher.group(1));
								} else if (matcher64.find() && LightAttachment.config.getBoolean("message.decode-base64")) {
									base64 = true;
								} else if (nameMatcher.find()) {
									if (!nameMatcher.group(1).toLowerCase().startsWith("message/")) {
										attachCount++;
										filename = LightAttachment.config.getString("directory.temp")
												+ nameMatcher.group(2)
												+ "-"
												+ smessage.getFilename().replace(
														LightAttachment.config.getString("directory.temp"), "") + "-"
												+ attachCount;

										mimeType = nameMatcher.group(1);

										writer = new PrintWriter(filename);
										set.add(filename + "-d", nameMatcher.group(2));
										canStore = false;
									} else {
										encapsulateLevel++;
										waitEncapsulate = true;
										System.err.println("Encapsulated message detected (level " + encapsulateLevel + ")");
									}
								} else if (contentMatcher.find()) {
									if (!contentMatcher.group(1).toLowerCase().startsWith("message/")) {
										waitContentName = true;
										mimeType = contentMatcher.group(1);
										//System.err.println("** waitContentName (" + mimeType + ")");
									} else {
										encapsulateLevel++;
										waitEncapsulate = true;
										System.err.println("Encapsulated message detected (level " + encapsulateLevel + ")");
									}
								} else if (waitContentName) {
									//System.err.println("** " + line);
									Matcher nnameMatcher = StreamedSmtpMessage.contentNamePattern.matcher(line);
									if (nnameMatcher.find()) {
										attachCount++;
										filename = LightAttachment.config.getString("directory.temp")
												+ nnameMatcher.group(1)
												+ "-"
												+ smessage.getFilename().replace(
														LightAttachment.config.getString("directory.temp"), "") + "-"
												+ attachCount;

										writer = new PrintWriter(filename);
										set.add(filename + "-d", nnameMatcher.group(1));
										canStore = false;
									}
									waitContentName = false;
								}
							}

							if (canStore) {
								headerBuffer.append(line + "\n");
							}

							line = null;
							break;
						case P_HEADER:
							if (line.length() == 0) {
								//System.err.println("*** SWITCH TO P_BODY ***");
								state = P_BODY;
							}
							// In the first message, this is not a Partial Header
							if (smessage.getNumber() == 1) {
								message.append(line + "\n");
								if (currentPart.size() <= 0) {
									Matcher matcher = StreamedSmtpMessage.boundaryPattern.matcher(line);
									if (matcher.find()) {
										//System.err.println("> " + matcher.group(1));
										currentPart.add(matcher.group(1));
									}
								}
							}
							line = null;
							break;
						}

					}

					reader.close();
					// Don't delete in case of http save failure
					// new File(smessage.getFilename()).delete();
					set.getOriginalMessages().add(smessage.getFilename());

				}

				if (!stop) {
					MimeMessage mime = new MimeMessage(Session.getDefaultInstance(new Properties()), new ByteArrayInputStream(
							message.toString().getBytes()));									
					
					set.setMessage(mime);
					set.setMessageID(mime.getMessageID());

					String mailFilename = LightAttachment.config.getString("directory.temp") + mime.hashCode() + "-message";
					mime.writeTo(new FileOutputStream(mailFilename));
					set.add(mailFilename, "message");

					manager.push(set);
					//setDone(true);
					long end = System.currentTimeMillis();
					log.info("Mail " + set.hashCode() + " parsed in "+(end-begin)+" ms");
				} else {
					forward(bulk);
				}

			} else {

				log.info("Got new mail from " + set.getFrom() + " to " + set.getTo() + " with id " + set.hashCode() + " of " + size + " bytes");

				for (int i = 0; i < bulk.size(); i++) {

					StreamedSmtpMessage smessage = bulk.get(i);

					set.getOriginalMessages().add(smessage.getFilename());

				}

				if (bulk.get(0).isForward()) System.err.println("Not in domain");
				
				manager.pushToInjectFromFile(set);
				//setDone(true);
				long end = System.currentTimeMillis();
				log.info("Mail " + set.hashCode() + " parsed in "+(end-begin)+" ms");
				log.info("Mail " + set.hashCode() + " will be forwarded");
			}
		}

		/** Set a messages bulk to be forwarded.
		 * @param bulk the bulk to forward */
		public void forward(ArrayList<StreamedSmtpMessage> bulk) throws IOException, MessagingException {
			long begin = System.currentTimeMillis();
			
			MailSet set = new MailSet();
			set.setFrom(bulk.get(0).getFrom());
			set.setTo(bulk.get(0).getTo());
			
			log.info("Got new mail from " + set.getFrom() + " to " + set.getTo() + " with id " + set.hashCode());
			
			for (int i = 0; i < bulk.size(); i++) {
				set.getOriginalMessages().add(bulk.get(i).getFilename());
			}
			
			manager.pushToInjectFromFile(set);
			//setDone(true);
			long end = System.currentTimeMillis();
			log.info("Mail " + set.hashCode() + " parsed in "+(end-begin)+" ms");
			log.info("Mail " + set.hashCode() + " will be forwarded");	
		}
		
	}

}
