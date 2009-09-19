package com.lightattachment.mails;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.mail.MessagingException;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.multipart.FilePart;
import org.apache.commons.httpclient.methods.multipart.MultipartRequestEntity;
import org.apache.commons.httpclient.methods.multipart.Part;
import org.apache.commons.httpclient.methods.multipart.StringPart;
import org.apache.log4j.Logger;

import com.lightattachment.stats.SendErrorReportThread;
import com.lightattachment.stats.StoppableThread;

/** 
 * Send the attachments of its queue to e-BigSend.
 * 
 * @author Benoit Giannangeli
 * @version 0.1a
 * 
 */

public class AttachmentSaver extends Thread {

	/** The queue of the <code>MailSet</code> to save. It is filled by the <code>MailManager</code>. */
	private ArrayList<MailSet> toSaveQueue;

	/** The <code>MailManager</code> to send the modified mails back. */
	private MailManager manager;
	
	/** Set to <code>false</code> to shutdown. */
	private boolean working;
	
	/** <code>SaveThread</code> pool. */
	private ArrayList<SaveThread> savePool;
	
	/** <code>SaveByHostThread</code> pool. */
	private ArrayList<SaveByHostThread> byhostPool;
	
	/** Count <code>SaveThread</code> running. */
	public static int saveRunning = 0;
	
	/** Count <code>SaveByHostThread</code> running. */
	public static int byhostRunning = 0;
	
	/** Logger used to trace activity. */
	static Logger log = Logger.getLogger(AttachmentSaver.class);
	
	/** 
	 * Build the <code>AttachmentSaver</code> (needs <code>lightattachment.xml</code> config file). 
	 * 
	 * @param manager the <code>MailManager</code> to send the modified mails back
	 * */
	public AttachmentSaver(MailManager manager) {
		super();
		this.manager = manager;
		this.working = true;
		this.toSaveQueue = new ArrayList<MailSet>();
		this.savePool = new ArrayList<SaveThread>();
		this.byhostPool = new ArrayList<SaveByHostThread>();
	}
	
	/** Select a <code>SaveThread</code> in <code>savePool</code>.
	 * If the pool is empty, creates a new thread and return it.
	 * Else if one the thread is free, return it.
	 * Else if limit not reached, creates a new thread and return it.
	 * Else select the thread with the smaller queue and return it.
	 * @return the selected <code>SaveThread</code> */
	public SaveThread selectSave() {
		if (savePool.size() == 0) {
			SaveThread st = new SaveThread();
			st.start();
			savePool.add(st);
			return st;
		} else {
			SaveThread st = null;
			for (SaveThread s : savePool) {
				if (s.queue.size() == 0) return s;
				else if (st == null) st = s;
				else if (s.queue.size() < st.queue.size()) st = s;
			}
			
			if (st.queue.size() > 0 && savePool.size() < LightAttachment.config.getInt("message.output-limit")) {
				st = new SaveThread();
				st.start();
				savePool.add(st);
			}
			
			return st;
		}
	}
	
	/** Select a <code>SaveByHostThread</code> in <code>byhostPool</code>.
	 * If the pool is empty, creates a new thread and return it.
	 * Else if one the thread is free, return it.
	 * Else if limit not reached, creates a new thread and return it.
	 * Else select the thread with the smaller queue and return it.
	 * @return the selected <code>SaveByHostThread</code> */
	public synchronized SaveByHostThread selectByHost() {
		if (byhostPool.size() == 0) {
			SaveByHostThread sbht = new SaveByHostThread();
			sbht.start();
			byhostPool.add(sbht);
			return sbht;
		} else {
			SaveByHostThread sbht = null;
			for (SaveByHostThread s : byhostPool) {
				if (s.queue.size() == 0) sbht = s;
				else if (sbht == null) sbht = s;
				else if (s.queue.size() < sbht.queue.size()) sbht = s;				
			}
			
			if (sbht.queue.size() > 0 && byhostPool.size() < LightAttachment.config.getInt("message.output-limit")) {
				sbht = new SaveByHostThread();
				sbht.start();
				byhostPool.add(sbht);
			}
			
			return sbht;
		}
	}

	/** Push a <code>MailSet</code> to the saved queue. 
	 * @param mail the <code>MailSet</code> to push into the queue */
	public synchronized void push(MailSet mail) {
		if (mail != null) toSaveQueue.add(mail);
	}
	
	/** Return the first value of the save queue without removing it.
	 * @return the first value of the save queue */
	@SuppressWarnings("unused")
	private synchronized MailSet peek() {
		if (toSaveQueue.size() > 0)
			return toSaveQueue.get(0);
		else 
			return null;
	}
	
	/** Return the first value of the save queue and remove it.
	 * @return the first value of the save queue */
	private synchronized MailSet pop() { 
		if (toSaveQueue.size() > 0) {
			MailSet ms = toSaveQueue.get(0);
			toSaveQueue.remove(0);
			return ms;
		} else {
			return null;
		}
	}
	
	/** Select the e-BigSend URL in functions of the target address of the message. 
	 * See <code>lightattachment.xml</code> to add other e-BigSend URL.
	 * @param to the target adress of the message (a single one)
	 * @return the selected e-BigSend URL */
	@SuppressWarnings("unchecked")
	private synchronized ArrayList<String> selectEBigSend(String to) {			
		List<HierarchicalConfiguration> c = LightAttachment.config.configurationsAt("ebigsend.address.alternate");
		
		ArrayList<String> selected = new ArrayList<String>();
		for (HierarchicalConfiguration h : c) {
			String pattern = h.getString("incoming-host");
			Matcher m = Pattern.compile(pattern).matcher(to);
			boolean found = m.find();
			StringTokenizer ch = new StringTokenizer(h.getString("outcoming-host")," ");
			
			if (ch.countTokens() != 0 && found) {
				while (ch.hasMoreTokens()) {
					String host = ch.nextToken();
					selected.add(host.replace("\n", ""));
				}
			}
		}
		
		selected.add(LightAttachment.config.getString("ebigsend.address.default"));				
		
		return selected;
	}
	
	/** Select e-BigSend URL for several recipient.
	 * @param to a list of recipient separated by a coma
	 * @return list of possible hosts associated to several recipient */
	private synchronized HashMap<ArrayList<String>,LinkedList<String>> selectEBigSends(String to) {
		HashMap<ArrayList<String>,LinkedList<String>> selected = new HashMap<ArrayList<String>, LinkedList<String>>();		
		StringTokenizer token = new StringTokenizer(to,",");
		while (token.hasMoreTokens()) {
			String dest = token.nextToken();
			ArrayList<String> host = selectEBigSend(dest);
			if (selected.containsKey(host)) selected.get(host).add(dest);
			else {
				selected.put(host, new LinkedList<String>());
				selected.get(host).add(dest);
			}
		}
				
		return selected;
	}	
	
	/** Safely shutdown the instance. */
	public synchronized void shutdown() throws HttpException, IOException, MessagingException, InterruptedException {
		for (SaveByHostThread s : byhostPool) s.shutdown();
		for (SaveThread st : savePool)  st.shutdown();
		working = false;
		log.info("AttachmentSaver stopped");		
	}
	
	public void run() {
		super.run();
		while (working) {
						
			try {

				MailSet set = null;
				if ((set = this.pop()) != null) {
					SaveThread st = selectSave();
					st.push(set);
				}
				
				sleep(100);
				
			} catch (InterruptedException e) {
				log.error(e.getMessage(),e);
				e.printStackTrace();
			}
			
		}
	}
	
	/** Each set is saved within an instance of this thread. */
	private class SaveThread extends StoppableThread {

		/** The sets to save. */
		private ArrayList<MailSet> queue;
		
		/** Build a <code>SaveThread</code> */
		public SaveThread() {
			super();
			this.queue = new ArrayList<MailSet>();	
			saveRunning++;
			System.err.println("SaveThread++ count: "+saveRunning);
		}

		/** Add a <code>MailSet</code> to the queue.
		 * @param set the <code>MailSet</code> to add*/
		public void push(MailSet set) {
			if (set != null) queue.add(set);
		}

		/** Return the first <code>MailSet</code> of the queue without removing it.
		 * @return the first <code>MailSet</code> of the queue */
		public MailSet peek() {
			if (queue.size() > 0) return queue.get(0);
			else return null;
		}
		
		/** Shutdown the instance. */
		public void shutdown() {
			setDone(true);
			saveRunning--;
			System.err.println("SaveThread-- count: "+saveRunning);
		}
		
		public void run() {
			super.run();
			while (!isDone()) {
				MailSet set = peek();
				try {					
					if (set != null) {
						save(set);
						queue.remove(set);
					} else sleep(100);
				} catch (MessagingException e) {
					log.error(e.getMessage(), e);
					e.printStackTrace();
					SendErrorReportThread sert = new SendErrorReportThread(set, "Error while saving message.", e);
					sert.start();
				} catch (IOException e) {
					log.error(e.getMessage(), e);
					e.printStackTrace();
					SendErrorReportThread sert = new SendErrorReportThread(set, "Error while saving message.", e);
					sert.start();
				} catch (InterruptedException e) {
					log.error(e.getMessage(), e);
					e.printStackTrace();
					SendErrorReportThread sert = new SendErrorReportThread(set, "Error while saving message.", e);
					sert.start();
				}
			}
		}
		
		
		/** Save the attachment of the specified <code>MailSet</code> to e-BigSend. 
		 * It will try to save it as many time as allowed in <code>lightattachment.xml</code>. If it still fail to be saved, it will forward the message.
		 * If successfully saved, a new multipart is added to the modified mail with links to the saved attachment and the message is pushed to the 
		 * <code>MailManager</code> save queue. 
		 * @param set the <code>MailSet</code> to save */
		private void save(MailSet set) throws MessagingException, IOException, InterruptedException {
			if (set != null) {
				
				
					
					// <host, rcpt>
					HashMap<ArrayList<String>, LinkedList<String>> selectedHosts = selectEBigSends(set.getTo());
					// <host, set>
					HashMap<ArrayList<String>,MailSet> clones = new HashMap<ArrayList<String>, MailSet>();
					
					if (selectedHosts.keySet().size() == 1) {
						for (ArrayList<String> host : selectedHosts.keySet())
							if (!clones.containsKey(host)) clones.put(host, set);
					} else {
						log.info("Several hosts selected for message "+set.hashCode());
						
						boolean first = true;
						
						for (ArrayList<String> host : selectedHosts.keySet()) {
							if (!clones.containsKey(host)) {
								MailSet c;
								if (first) {
									c = set;
									first = false;
								} else c = set.clone();
								String to = "";
								LinkedList<String> rcpt = selectedHosts.get(host);
								for (String r : rcpt) to += r + ",";
								to = to.substring(0, to.length() - 1);
								c.setTo(to);
								
								clones.put(host, c);
							}
						}
						
						String nset = "";
						for (MailSet s : clones.values()) nset += s.hashCode()+",";
						nset = nset.substring(0,nset.length()-1);
						log.info("Message "+set.hashCode()+" now reffers to "+nset);
					}
					
					//LinkedList<SaveByHostThread> saveThreads = new LinkedList<SaveByHostThread>(); 
					for (ArrayList<String> h : clones.keySet()) {
						//SaveByHostThread sbht = new SaveByHostThread(clones.get(h),h);
						//saveThreads.add(sbht);
						//sbht.start();
						SaveByHostThread sbht = selectByHost();
						sbht.push(clones.get(h),h);
					}
					
					boolean done = false;
					while (!done) {
						done = true;
						for (MailSet s : clones.values()) done = done && s.isSent();
						sleep(100);
					}
					for (String filename : set.getParts().keySet()) {
						if (!filename.endsWith("-message")) {
							String ofilename = set.getParts().get(filename);
							if (!set.getOriginalMessages().contains(LightAttachment.config.getString("directory.temp") + ofilename))
								set.getOriginalMessages().add(LightAttachment.config.getString("directory.temp") + ofilename);
						}
					}
					manager.pushToClean(set);
				
			}
		}
		
	}
	
	/** Save <code>MailSet</code> using several host.*/
	private class SaveByHostThread extends StoppableThread {

		/** The sets to save */
		private ArrayList<MailSet> queue;

		/** Hosts to use for saving */
		private ArrayList<ArrayList<String>> hostQueue;
		
		/** Build a <code>SaveByHostThread</code> */
		public SaveByHostThread() {
			super();
			this.queue = new ArrayList<MailSet>();
			this.hostQueue = new ArrayList<ArrayList<String>>();
			byhostRunning++;
			System.err.println("SaveByHostThread++ count: "+byhostRunning);
		}
		
		/** Add a <code>MailSet</code> to the queue.
		 * @param set the <code>MailSet</code> to add
		 * @param host the list of host associated to the <code>MailSet</code> */
		public void push(MailSet set, ArrayList<String> host) {
			if (set != null && host != null) {
				queue.add(set);
				hostQueue.add(host);
			}
		}
		
		/** Return the first <code>MailSet</code> of the queue without removing it.
		 * @return the first <code>MailSet</code> */
		public MailSet peek() {
			if (queue.size() > 0) return queue.get(0);
			else return null;
		}
		
		/** Return the first list of host from the queue.
		 * @return the first list of host */
		public ArrayList<String> peekHost() {
			if (hostQueue.size() > 0) return hostQueue.get(0);
			else return null;
		}
		
		/** Shutdown the instance */
		public void shutdown() {
			setDone(true);
			byhostRunning--;
			System.err.println("SaveByHostThread-- count: "+byhostRunning);
		}
		
		/** Convert the byte representation of a CRC to a string representation.
		 * @param bytes the CRC
		 * @param separator 
		 * @param line
		 * @return the string representation of the CRC */
		public String byteToString(byte[] bytes, String separator, int line) {
		    if (bytes == null) return "";
		    StringBuilder sb = new StringBuilder();
		    int iCount = 1;
		    for (byte bt : bytes) {
		        sb.append(Integer.toHexString((bt&0xFF)+0x100).substring(1).toUpperCase());
		        if (iCount++ >= line && line != 0) {
		            iCount = 1;
		            sb.append("\n");
		        } else sb.append(separator);		        
		    } 
		    return sb.substring(0, sb.length()-1);
		}
		
		public void run() {
			while (!isDone()) {
				MailSet set = peek();
				ArrayList<String> host = peekHost();

				if (set != null && host != null) {
					try {
						long begin = System.currentTimeMillis();
						HttpClient client = new HttpClient();
						client.getHttpConnectionManager().getParams().setConnectionTimeout(
								LightAttachment.config.getInt("ebigsend.timeout"));

						LinkedHashMap<String, String> links = new LinkedHashMap<String, String>();

						boolean failure = false;
						for (String filename : set.getParts().keySet()) {
							if (!filename.endsWith("-message")) {

								String ofilename = set.getParts().get(filename);
								File targetFile = new File(filename);

								// Compute the attachment CRC
								MessageDigest m = MessageDigest.getInstance("MD5");
								DigestInputStream dis = new DigestInputStream(new FileInputStream(filename), m);
								byte[] buf = new byte[4096];
								while (dis.read(buf) >= 0) {
								}
								String CRC = byteToString(m.digest(), "", 0);
								dis.close();

								boolean tryAgain = true;
								int attempt = 1;

								while (tryAgain
										&& (attempt <= LightAttachment.config.getInt("ebigsend.attempt") || host.size() > 0)) {
									if (attempt > 1)
										Thread.sleep(LightAttachment.config.getInt("ebigsend.attempt-wait"));
									try {
										attempt++;

										// Check the file is not already on e-BigSend
										boolean already = false;
										PostMethod post = new PostMethod(host.get(0));
										Part[] data = { new StringPart("CRC", CRC) };
										post.setRequestEntity(new MultipartRequestEntity(data, post.getParams()));

										int st = client.executeMethod(post);
										if (st == HttpStatus.SC_OK) {
											String r = post.getResponseBodyAsString();
											if (r != null && !r.equals("false") && !r.equals("null")) {
												// It's already there, so grab the link to it
												log.info("Attachment '" + ofilename + "' of mail " + set.hashCode()
														+ " was already saved at " + r);
												links.put(r, ofilename);
												tryAgain = false;
												failure = false;
												already = true;
											}
										} else failure = true;

										post.releaseConnection();

										// If not found on e-BigSend, try to send it
										if (!already) {
											if (attempt == 2)
												log.info("Selecting host " + host.get(0) + " for message " + set.hashCode());
											PostMethod filePost = new PostMethod(host.get(0));
											Part[] parts = { new FilePart(ofilename, targetFile) };
											filePost.setRequestEntity(new MultipartRequestEntity(parts, filePost.getParams()));

											int status = client.executeMethod(filePost);
											if (status == HttpStatus.SC_OK) {
												String place = filePost.getResponseBodyAsString();
												log.info("Attachment '" + ofilename + "' of mail " + set.hashCode()
														+ " successfully saved at " + place + " in " + (attempt - 1)
														+ " attempt(s)");
												links.put(place, ofilename);
												tryAgain = false;
												failure = false;
											} else {
												log.warn("Attachment '" + ofilename + "' of mail " + set.hashCode()
														+ " couldn't be saved (attempt #" + (attempt - 1) + "): "
														+ HttpStatus.getStatusText(status));
												failure = true;
												if (attempt > LightAttachment.config.getInt("ebigsend.attempt")
														&& host.size() <= 1) {
													log.error("Mail " + set.hashCode()
															+ " couldn't be saved and will be forwarded");
													SendErrorReportThread sert = new SendErrorReportThread(
															set,
															"No e-BigSend server was reachable. The message has been forwarded unchanged.",
															null);
													sert.start();
													manager.pushToInjectFromFile(set);
													break;
												}
											}

											filePost.releaseConnection();
										}

									} catch (HttpException e) {
										log.warn("Attachment(s) of mail " + set.hashCode() + " couldn't be saved (attempt #"
												+ (attempt - 1) + "): " + e.getMessage(), e);
										failure = true;
										if (attempt > LightAttachment.config.getInt("ebigsend.attempt") && host.size() <= 1) {
											log.error("Mail " + set.hashCode() + " couldn't be saved and will be forwarded");
											SendErrorReportThread sert = new SendErrorReportThread(
													set,
													"No e-BigSend server was reachable. The message has been forwarded unchanged.",
													e);
											sert.start();
											manager.pushToInjectFromFile(set);
											break;
										}
									} catch (IOException e) {
										log.warn("Attachment(s) of mail " + set.hashCode() + " couldn't be saved (attempt #"
												+ (attempt - 1) + "): " + e.getMessage(), e);
										failure = true;

										if (attempt > LightAttachment.config.getInt("ebigsend.attempt") && host.size() <= 1) {
											log.error("Mail " + set.hashCode() + " couldn't be saved and will be forwarded");
											SendErrorReportThread sert = new SendErrorReportThread(
													set,
													"No e-BigSend server was reachable. The message has been forwarded unchanged.",
													e);
											sert.start();
											manager.pushToInjectFromFile(set);
											break;
										}
									} catch (Exception e) {
										log.warn("Attachment(s) of mail " + set.hashCode() + " couldn't be saved (attempt #"
												+ (attempt - 1) + "): " + e.getMessage(), e);
										failure = true;
										if (attempt > LightAttachment.config.getInt("ebigsend.attempt") && host.size() <= 1) {
											log.error("Mail " + set.hashCode() + " couldn't be saved and will be forwarded");
											SendErrorReportThread sert = new SendErrorReportThread(
													set,
													"No e-BigSend server was reachable. The message has been forwarded unchanged.",
													e);
											sert.start();
											manager.pushToInjectFromFile(set);
											break;
										}
									}
									if (attempt > LightAttachment.config.getInt("ebigsend.attempt")) {
										host.remove(0);
										if (host.size() > 0)
											attempt = 1;
									}
								}

							} else {
								if (!failure) {
									StringBuffer data = new StringBuffer();
									data.append("Content-Type: text/plain; charset=ISO-8859-1\n");
									data.append("Content-Disposition: inline\n");
									data.append("Content-Transfer-Encoding: 7bit\n\n");
									data.append(LightAttachment.config.getString("ebigsend.head-message") + "\n\n");
									for (String key : links.keySet()) {
										String lname = key;
										String oname = links.get(key);
										data.append(LightAttachment.config.getString("ebigsend.att-message").replace("{name}",
												oname).replace("{address}", lname)
												+ "\n");
									}

									ByteArrayInputStream inMsgStream = new ByteArrayInputStream(data.toString().getBytes());

									MimeMessage msg = set.getMessage();

									// TDODO Detect mail without attachment before if min size limit set to 0
									if (msg.getContent() instanceof MimeMultipart) {
										MimeMultipart multi = (MimeMultipart) msg.getContent();

										MimeBodyPart bpart = new MimeBodyPart(inMsgStream);

										multi.addBodyPart(bpart);

										msg.setContent(multi);
										msg.saveChanges();
									}

									manager.pushToInject(set);
								}
							}
						}

						// setDone(true);

						long end = System.currentTimeMillis();
						log.info("All attachments of mail " + set.hashCode() + " processed in " + (end-begin) + " ms");

						while (true) {
							if (set.isSent()) break;
							sleep(100);
						}
					} catch (IOException e) {
						log.error(e.getMessage(), e);
						e.printStackTrace();
						// setDone(true);
						SendErrorReportThread sert = new SendErrorReportThread(null, "AttachmentSaver has been stopped.", e);
						sert.start();
					} catch (MessagingException e) {
						log.error(e.getMessage(), e);
						e.printStackTrace();
						// setDone(true);
						SendErrorReportThread sert = new SendErrorReportThread(null, "AttachmentSaver has been stopped.", e);
						sert.start();
					} catch (InterruptedException e) {
						log.error(e.getMessage(), e);
						e.printStackTrace();
						// setDone(true);
						SendErrorReportThread sert = new SendErrorReportThread(null, "AttachmentSaver has been stopped.", e);
						sert.start();
					} catch (NoSuchAlgorithmException e) {
						log.error(e.getMessage(), e);
						e.printStackTrace();
						// setDone(true);
						SendErrorReportThread sert = new SendErrorReportThread(null, "AttachmentSaver has been stopped.", e);
						sert.start();
					}
					queue.remove(set);
					hostQueue.remove(host);
				} else {
					try {
						sleep(100);
					} catch (InterruptedException e) {
						log.error(e.getMessage(), e);
						e.printStackTrace();
					}
				}
			}
		}
	}
}
