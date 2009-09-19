package com.lightattachment.stats;

import java.util.ArrayList;
import java.util.regex.Pattern;

public class StatisticMessage {

	private long messageID;

	private int nbPartial;

	private long partialSize;
	
	private long totalSize;

	private boolean forwarded;

	private long inDate;

	private long parsingDate;

	private long savedDate;

	private long outDate;
	
	private ArrayList<StatisticAttachment> attachments;
	
	public static final Pattern inPattern = Pattern.compile("Got new mail from ([^ ]+) to ([^ ]+) with id (\\d+) in (\\d+) partial mails of (\\d+) bytes for a total size of (\\d+) bytes");
	
	public static final Pattern receivingPattern = Pattern.compile("\\(\\d+\\) received a message in (\\d+) ms");
	
	public static final Pattern parsingPattern = Pattern.compile("Mail \\d+ parsed in (\\d+) ms");
	
	public static final Pattern savePattern = Pattern.compile("All attachments of mail \\d+ processed in (\\d+) ms");
	
	public static final Pattern outPattern = Pattern.compile("Mail \\d+ injected back to Postfix in (\\d+) ms");
	
	public static final Pattern outUnchangedPattern = Pattern.compile("Mail \\d+ injected back to Postfix unchanged in (\\d+) ms");
	
	public static final Pattern cleaningPattern = Pattern.compile("Mail \\d+ deleted from disk in (\\d+) ms");

	public static final Pattern ignoreForwardedPattern = Pattern.compile("Mail (\\d+) will be forwarded");
	
	public static final Pattern ignoreForwardedSizePattern = Pattern.compile("Got new mail from [^ ]+ to [^ ]+ with id \\d+ of (\\d+) bytes");
	
	public static final Pattern forwardedPattern = Pattern.compile("Mail (\\d+) couldn't be saved and will be forwarded");
	
	public static final Pattern deletedPattern = Pattern.compile("Temporary file [^ ]+ of (\\d+) bytes deleted");
	
	public StatisticMessage() {
		attachments = new ArrayList<StatisticAttachment>();
	}
	
	public StatisticMessage(long start, long id, int partial, long size, long total) {
		attachments = new ArrayList<StatisticAttachment>();
		messageID = id;
		nbPartial = partial;
		partialSize = size;
		inDate = start;
		totalSize = total;
	}

	public long getMessageID() {
		return messageID;
	}

	public void setMessageID(long messageID) {
		this.messageID = messageID;
	}

	public String toString() {
		return ""+messageID;
	}

	public long getParsingDate() {
		return parsingDate;
	}

	public void setParsingDate(long endParsingDate) {
		this.parsingDate = endParsingDate;
	}

	public boolean isForwarded() {
		return forwarded;
	}

	public void setForwarded(boolean forwarded) {
		this.forwarded = forwarded;
	}

	public long getInDate() {
		return inDate;
	}

	public void setInDate(long inDate) {
		this.inDate = inDate;
	}

	public int getNbPartial() {
		return nbPartial;
	}

	public void setNbPartial(int nbPartial) {
		this.nbPartial = nbPartial;
	}

	public long getOutDate() {
		return outDate;
	}

	public void setOutDate(long outDate) {
		this.outDate = outDate;
	}

	public long getPartialSize() {
		return partialSize;
	}

	public void setPartialSize(long partialSize) {
		this.partialSize = partialSize;
	}

	public long getSavedDate() {
		return savedDate;
	}

	public void setSavedDate(long savedDate) {
		this.savedDate = savedDate;
	}

	public ArrayList<StatisticAttachment> getAttachments() {
		return attachments;
	}

	public StatisticAttachment getAttachment(String name) {
		for (StatisticAttachment a : attachments) {
			if (a.getName().equals(name)) return a;
		}
		return null;
	}
	
	public void setAttachments(ArrayList<StatisticAttachment> attachments) {
		this.attachments = attachments;
	}
	
	public void addAttachment(StatisticAttachment attachment) {
		this.attachments.add(attachment);
	}

	public long getTotalSize() {
		return totalSize;
	}

	public void setTotalSize(long totalSize) {
		this.totalSize = totalSize;
	}
	
}
