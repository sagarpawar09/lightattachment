package com.lightattachment.mails;

import java.util.ArrayList;
import java.util.LinkedHashMap;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

/** 
 * Hold precious information about a message.
 * This is used all along the message processing to refer to the real message which is on the 
 * disk and never in memory.
 * 
 * @author Benoit Giannangeli
 * @version 0.1a
 * 
 */

public class MailSet {
	
	/** The original message ID.
	 * Set to <code>StreamedMailParser.STOP</code> to stop LightAttachment components. */
	private String messageID;
	
	/** The final message without attachment. */
	private MimeMessage message;
	
	/** Message envelope <code>from</code>. */
	private String from;
	
	/** Message envelope <code>to</code>. */
	private String to;
	
	/** When set to true, the MailSet is pushedToClean (deleted).*/ 
	private boolean sent;
	
	/** <code>true</code> if must be injected back from files. */
	private boolean fromFile;
	
	/** <code>true</code> if must be removed from disk. */
	private boolean clean;
	
	/** Contains references to the attachment files.
	 * Couples of <code>real file name</code> - <code>original file name</code>. */
	private LinkedHashMap<String,String> parts;

	/** Contains original messages file names.
	 * Used if the message is too small, too large or failed to be saved. */
	private ArrayList<String> originalMessages;
	
	/** Build a <code>MailSet</code> from scratch. */
	public MailSet() {
		this.parts = new LinkedHashMap<String, String>();
		this.originalMessages = new ArrayList<String>();
		messageID = null;
		message = null;
		from = null;
		to = null;
		sent = false;
		fromFile = false;
		clean = false;
	}
	
	/** Build a <code>MailSet</code>.
	 * @param id the message id.
	 * @param from the message <code>from</code> envelope.
	 * @param to the message <code>to</code> envelope.
	 * @param partial <code>true</code> if the message has been sent in several <code>message/partial</code> mails. */
	public MailSet(String id, String from, String to, boolean partial) {
		this.parts = new LinkedHashMap<String, String>();
		this.originalMessages = new ArrayList<String>();
		this.messageID = id;
		this.message = null;
		this.from = from;
		this.to = to;
		sent = false;
	}
		
	/** Build a <code>MailSet</code>.
	 * @param id the message id.
	 * @param message the modified message.
	 * @param from the message <code>from</code> envelope.
	 * @param to the message <code>to</code> envelope. */
	public MailSet(String id, MimeMessage message, String from, String to) {
		this.parts = new LinkedHashMap<String, String>();
		this.originalMessages = new ArrayList<String>();
		this.messageID = id;
		this.message = message;
		this.from = from;
		this.to = to;
		sent = false;
	}
	
	/** Add a new attachment reference.
	 * @param filename the real file name.
	 * @param name the original file name.*/
	public void add(String filename, String name) {
		parts.put(filename, name);
	}

	/** Return a copy of the current instance.
	 * @return a copy of the current instance */
	@SuppressWarnings("unchecked")
	protected MailSet clone() {
		MailSet clone = new MailSet();
		clone.setMessageID(new String(messageID));
		clone.setFrom(new String(from));
		clone.setTo(new String(to));
		clone.setParts((LinkedHashMap<String,String>)parts.clone());
		try {
			clone.setMessage(new MimeMessage(message));
		} catch (MessagingException e) {
			e.printStackTrace();
		}
		clone.setOriginalMessages((ArrayList<String>)originalMessages.clone());
		return clone;
	}

	public LinkedHashMap<String,String> getParts() {
		return parts;
	}

	public void setParts(LinkedHashMap<String,String> parts) {
		this.parts = parts;
	}

	public String getMessageID() {
		return messageID;
	}

	public void setMessageID(String messageID) {
		this.messageID = messageID;
	}

	public MimeMessage getMessage() {
		return message;
	}

	public void setMessage(MimeMessage message) {
		this.message = message;
	}
	
	public int getAttachmentNumber() {
		return parts.size();
	}

	public String getFrom() {
		return from;
	}

	public void setFrom(String from) {
		this.from = from;
	}

	public String getTo() {
		return to;
	}

	public void setTo(String to) {
		this.to = to;
	}

	public ArrayList<String> getOriginalMessages() {
		return originalMessages;
	}
	
	private void setOriginalMessages(ArrayList<String> originals) {
		originalMessages = originals;
	}

	public boolean isSent() {
		return sent;
	}

	public void setSent(boolean sent) {
		this.sent = sent;
	}

	public boolean isFromFile() {
		return fromFile;
	}

	public void setFromFile(boolean fromFile) {
		this.fromFile = fromFile;
	}

	public boolean isClean() {
		return clean;
	}

	public void setClean(boolean clean) {
		this.clean = clean;
	}
	
}
