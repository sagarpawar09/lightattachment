package com.lightattachment.smtp.streamed;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;

import com.lightattachment.mails.LightAttachment;
import com.lightattachment.smtp.SmtpResponse;
import com.lightattachment.smtp.SmtpState;
import com.lightattachment.stats.SendErrorReportThread;

/** 
 * Used to build a message on disk (never in memory).
 * A partial <code>StreamedSmtpMessage</code> is <code>Comparable</code> to another.
 * 
 * @author Benoit Giannangeli
 * @version 0.1a
 * 
 */

public class StreamedSmtpMessage implements Comparable<StreamedSmtpMessage> {
	
	/** The name of the file where the message is written. */
	private String filename;
	
	/** Used to easily write on the message file. */
	private PrintWriter pw;	
	
	/** <code>true</code> if the message is partial. */
	private boolean partial;
	
	/** Number of the partial message (1 if not partial). */
	private int number;
	
	/** The total number of partial messages which formed the whole message (1 if not partial). */
	private int total;
	
	/** The content ID of the partial message (<code>null</code> if not partial). */
	private String contentID;
	
	/** The message ID. */
	private String messageID;
	
	/** The message <code>from</code> envelope. */
	private String from;
	
	/** The message <code>to</code> envelope. */
	private String to;
	
	/** The message size in bytes. */
	private long size;
	
	/** The maximum allowed message size in bytes. */
	private static long max;
	
	/** The total space of the current file system. */
	private long totalSpace;
	
	/** A line count used to periodically check the disk free space. 
	 * This control is CPU consuming and can't be tested for each line received. */
	private long lineCount;
	
	/** The percentage of free disk space to maintain.
	 * If exceeded, messages are bounced. */
	private int pfree;
	
	/** <code>true</code> if the message can't be saved. */
	private boolean full;
	
	/** <code>true</code> if the message must be forwarded. */
	private boolean forward;
	
	/** Used during the first information collection. */
	private boolean waitCompletePartialHeader;
	
	/** Receiving beginning date. */
	private long begin;
	
	/** Receiving ending date. */
	private long end;
	
	/** The current file system. */
	private static final File dir = new File(".");
	
	/** Logger used to trace activity. */
	static Logger log = Logger.getLogger(StreamedMailParser.class);
	
	
	// MESSAGE PATTERNS
	
	/** Match the message ID. */
	public static final Pattern messageIDPattern = Pattern.compile("Message-I[dD]:\\s*(.*)");
	
	/** Match content ID, number and total number of a partial message. */
	public static final Pattern partialPattern = Pattern.compile("Content-[Tt]ype:\\s*message/partial;\\s*id=\"?([^\";]*)\"?;\\s*number=(\\d+);\\s*total=(\\d+)");
	/** Match the number of the partial message. */
	public static final Pattern partialNumberPattern = Pattern.compile("\\s*number=(\\d+)");
	/** Match the id of a partial message. */
	public static final Pattern partialIDPattern = Pattern.compile("\\s*id=\"?([^\";]*)\"?");
	/** Match the total number of partial messages. */
	public static final Pattern partialTotalPattern = Pattern.compile("\\s*total=(\\d+)");
	
	/** Match the boundary of a multipart message. */
	public static final Pattern boundaryPattern = Pattern.compile("boundary=\"?([^\"]+)\"?");
	
	/** Match the content type header field. */
	public static final Pattern contentTypePattern = Pattern.compile("Content-[Tt]ype:\\s*([\\w\\d-]+/[\\w\\d-]+);");
	
	/** Match the name of an attachment. */
	public static final Pattern contentNamePattern = Pattern.compile("\\s*name=\"?([^\"\\n]*)\"?");
	/** Match the content type and name of an attachment. */
	public static final Pattern contentTypeNamePattern = Pattern.compile("Content-[Tt]ype:\\s*([\\d\\w-]+/[\\d\\w-]+);\\s*name=\"?([^\"\\n]*)\"?");
	
	/** Match the content disposition of multipart (inline, attachment). */
	public static final Pattern contentDispositionPattern = Pattern.compile("Content-[Dd]isposition:\\s*(\\w+);?\\s*(filename=.*)?");
	
	/** Match the content transfer encoding of a multipart. */
	public static final Pattern base64Pattern = Pattern.compile("Content-Transfer-Encoding:\\s*base64");
	
	/** Build a <code>StreamedSmtpMessage</code>.
	 * @param filename the name of the file to write on. */
	public StreamedSmtpMessage(String filename) throws FileNotFoundException {		
		this.filename = filename;
		partial = false;
		number = -1;
		total = -1;
		contentID = null;
		size = 0;
		full = false;
		max = LightAttachment.config.getLong("message.message-size.max-size");
		totalSpace = dir.getTotalSpace();
		pfree = LightAttachment.config.getInt("directory.free-space");
		lineCount = 0;
		waitCompletePartialHeader = false;
		forward = false;
	}

	/** Store a single line to the message. 
	 * Constantly checking for too large messages and storage exceeding. 
	 * @param response the <code>SmtpResponse</code> of the SMTP client.
	 * @param params the line to store. */
	public void store(SmtpResponse response, String params) throws FileNotFoundException {
		if (params != null && ((params.length() == 0 && messageID != null) || params.length() != 0)) {
			if (pw == null) {
				pw = new PrintWriter(new File(filename));
				begin = System.currentTimeMillis();
			}

			lineCount++;
			
			size = new File(filename).length();

			if (lineCount <= 100000 || ((double) dir.getFreeSpace() / (double) totalSpace) * 100 >= pfree) {
				if (lineCount > 100000) lineCount = 0;
				if (size <= max) {

					if (SmtpState.DATA_HDR.equals(response.getNextState())) {
	
						if (messageID == null) {
							Matcher matcher = messageIDPattern.matcher(params);
							if (matcher.find()) {
								messageID = matcher.group(1);
							}
						}

						Matcher contentMatcher = contentTypePattern.matcher(params);
						Matcher numberMatcher = partialNumberPattern.matcher(params);
						Matcher totalMatcher = partialTotalPattern.matcher(params);
						Matcher idMatcher = partialIDPattern.matcher(params);
						if (!waitCompletePartialHeader) {
							if (contentMatcher.find() && contentMatcher.group(1).equals("message/partial")) {
								partial = true;
								if (numberMatcher.find()) number = Integer.parseInt(numberMatcher.group(1));
								else waitCompletePartialHeader = true;
								
								if (totalMatcher.find()) total = Integer.parseInt(totalMatcher.group(1));
								else waitCompletePartialHeader = true;
								
								if (idMatcher.find()) contentID = idMatcher.group(1);
								else waitCompletePartialHeader = true;
							}
						} else {
							if (numberMatcher.find()) number = Integer.parseInt(numberMatcher.group(1));
							
							if (totalMatcher.find()) total = Integer.parseInt(totalMatcher.group(1));
							
							if (idMatcher.find()) contentID = idMatcher.group(1);
							
							waitCompletePartialHeader = number <= -1 || contentID == null || total > -1;
						}			

						pw.print(params);
						pw.flush();
					} else if (SmtpState.DATA_BODY == response.getNextState()) {
						pw.print(params);
						pw.flush();
					}

				} else {

					log.error("Message of size above " + LightAttachment.config.getLong("message.message-size.max-size")
							+ " bytes not allowed");
					SendErrorReportThread sert = new SendErrorReportThread(null,
							"Message of size above " + LightAttachment.config.getLong("message.message-size.max-size")
							+ " bytes not allowed",null);
					sert.start();
					full = true;
					new File(filename).delete();

				}

			} else {

				log.error("Only "+LightAttachment.config.getLong("directory.free-space")+"% of free space available: stop receiving");
				SendErrorReportThread sert = new SendErrorReportThread(null,
						"Only "+LightAttachment.config.getLong("directory.free-space")+"% of free space available: stop receiving",null);
				sert.start();
				full = true;
				new File(filename).delete();

			}
		}
	}
	
	public void end() {
		end = System.currentTimeMillis();
		if (pw != null) pw.close();
	}
	
	public boolean isPartial() {
		return partial;
	}

	public String getContentID() {
		return contentID;
	}

	public void setContentID(String contentID) {
		this.contentID = contentID;
	}

	public int getNumber() {
		return number;
	}

	public void setNumber(int number) {
		this.number = number;
	}

	public int getTotal() {
		return total;
	}

	public void setTotal(int total) {
		this.total = total;
	}

	/** Compare <code>this</code> to another <code>StreamedSmtpMessage</code>.
	 * @return -1 if <code>o</code> partial number is before, 0 if the same, 1 if after. */
	public int compareTo(StreamedSmtpMessage o) {
		if (o.getNumber() > number) return -1;
		else if (o.getNumber() == number) return 0;
		else if (o.getNumber() < number) return 1;
		return 0;
	}

	public String getFrom() {		
		return from;
	}

	public void setFrom(String from) {
		ArrayList<String> domains = new ArrayList<String>();
		StringTokenizer tokenizer = new StringTokenizer(LightAttachment.config.getString("message.domain")," ");
		while (tokenizer.hasMoreTokens()) {
			domains.add(tokenizer.nextToken());
		}
		
		forward = true;
		
		for (String d : domains) {
			if (from.replace(">","").replace("<", "").endsWith("@"+d.toLowerCase()) 
					|| from.replace(">","").replace("<", "").endsWith("."+d.toLowerCase())) 
				forward = false;
		}
		this.from = from;
	}

	public String getMessageID() {
		return messageID;
	}

	public void setMessageID(String messageID) {
		this.messageID = messageID;
	}

	public String getTo() {
		return to;
	}

	public void setTo(String to) {
		this.to = to;
	}

	public String getFilename() {
		return filename;
	}

	public boolean isFull() {
		return full;
	}

	public void setFull(boolean full) {
		this.full = full;
	}
	
	public boolean isForward() {
		return forward;
	}

	public long getBegin() {
		return begin;
	}

	public long getEnd() {
		return end;
	}
	
}
