package com.lightattachment.stats;

import java.util.regex.Pattern;

public class StatisticAttachment {
	
	private String name;
	
	private long size;
	
	private String mimeType;
	
	private boolean saved;
	
	private int nbAttempt;
	
	public static final Pattern nameSizePattern = Pattern.compile("Mail (\\d+) got attachment called '([^ ']+)' of (\\d+) bytes and MIME type ([\\d\\w-]+/[\\d\\w-]+) saved to '([^ ']+)'");

	// TODO Use hashCode instead of file name
	public static final Pattern savedPattern = Pattern.compile("Attachment '([^ ']+)' of mail (\\d+) successfully saved at ([^ ]+) in (\\d+) attempt\\(s\\)");
	
	public StatisticAttachment() {
	}
	
	public StatisticAttachment(String name, long size, String mime) {
		this.name = name;
		this.size = size;
		this.mimeType = mime;
	}

	public String getMimeType() {
		return mimeType;
	}

	public void setMimeType(String mimeType) {
		this.mimeType = mimeType;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public int getNbAttempt() {
		return nbAttempt;
	}

	public void setNbAttempt(int nbAttempt) {
		this.nbAttempt = nbAttempt;
	}

	public boolean isSaved() {
		return saved;
	}

	public void setSaved(boolean saved) {
		this.saved = saved;
	}

	public long getSize() {
		return size;
	}

	public void setSize(long size) {
		this.size = size;
	}
	
}
