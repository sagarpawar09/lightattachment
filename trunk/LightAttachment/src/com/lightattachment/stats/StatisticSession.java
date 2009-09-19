package com.lightattachment.stats;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.regex.Pattern;

import org.rrd4j.ConsolFun;
import org.rrd4j.graph.RrdGraph;
import org.rrd4j.graph.RrdGraphConstants;
import org.rrd4j.graph.RrdGraphDef;

/** A statistical session. */
public class StatisticSession {

	private long beginDate;
	
	private long endDate;
	
	private long forwardedMessages;
	
	private long processedMessages;
	
	private long processedAttachments;
	
	private long failedSaving;
	
	private long nbInConnections;
	
	private long nbOutConnections;
	
	private long nbErrors;
	
	private long attempt;
	
	private long freespaceBefore;
	
	private long freespaceAfter;
	
	private long parsingTime;
	
	private long savingTime;
	
	private long sendingTime;

	public static final Pattern inConnectionPattern = Pattern.compile("SMTPPostfixInputConnector accepted connection from");
	
	public static final Pattern outConnectionPattern = Pattern.compile("SMTPPostfixOutputConnector\\(\\d+\\) logged in");	
	
	public static final long defaultLength = 86400000;
	
	/** Generates an HTML report using template. 
	 * @param pw to which the report is written
	 * @param code the report ID */
	public void generateHtmlReport(PrintWriter pw, int code) throws FileNotFoundException, IOException {
		String template = ReportGenerator.getTemplate().toString();
		
		template = template.replace("{code}", ""+code)
			.replace("{begin}", ReportGenerator.dateFormat.format(new Date(beginDate)))
			.replace("{end}", ReportGenerator.dateFormat.format(new Date(endDate)))
			.replace("{stat-session}", "<img src=\"session-"+hashCode()+".png\">")
			.replace("{stat-message}", "<img src=\"message-"+hashCode()+".png\">")
			.replace("{stat-messages-size}", "<img src=\"messages-size-"+hashCode()+".png\">");
		
		pw.append(template);		
	}
	
	/** Generates a text. 
	 * @param pw to which the report is written
	 * @param code the report ID */
	public void generateTextReport(PrintWriter pw, int code) { // TODO not used (everything is on graphics)
		pw.append("\nSession #" + code + " from "
				+ ReportGenerator.dateFormat.format(new Date(beginDate)) + " to "
				+ ReportGenerator.dateFormat.format(new Date(endDate)) + ":\n\n");
		
		pw.append(" SESSION:\n"
				+ "  Connections from Postfix: "+nbInConnections+"\n"
				+ "  Connections to Postfix: "+nbOutConnections+"\n"
				+ "  Errors encountered: "+nbErrors+"\n"
				+ "  Free disk space before session: "+freespaceBefore+" bytes\n"
				+ "  Free disk space after session: "+freespaceAfter+" bytes\n\n");
		
		if (getMessagesNumber() > 0) {
			pw.append(" MESSAGES:\n"
				+ "  Messages forwarded: "+forwardedMessages+"\n"
				+ "  Messages processed: "+getMessagesNumber()+"\n"
				+ "  Forwarded messages because of failed attachment saving: "+getFailedSaving()+"\n");
				/*+ "  Average parsing time: "+getParsingTime()+" ms\n"
				+ "  Average saving time: "+getSavingTime()+" ms\n"
				+ "  Average sending time: "+ getSendingTime()+" ms\n\n");*/
		
			pw.append(" ATTACHMENTS:\n"
				+ "  Attachment processed: "+getProcessedAttachments()+"\n"
				+ "  Attachment/Message: "+getAttachmentPerMessage()+"\n"
				//+ "  Average attachment size: "+getAttachmentSize()+" bytes\n"
				+ "  Average saving attempt: "+getAttemptNumber()+"\n");
				//+ "  Failed saved attachment: "+getFailedAttachmentNumber()+"\n");
				//+ "  MIME type repartition:\n"+getMimeTypeString());
		}
	}	
	
	/** Generates graphics. 
	 * @param directory to where the files must be saved
	 * @param code the report ID 
	 * @return the list of graphics files names */
	public ArrayList<String> generateRrdPlot(String directory, int code) throws IOException {
		int h = hashCode();
		if (getBeginDate()/1000 != getEndDate()/1000) {			
			ArrayList<String> files = new ArrayList<String>();
			
			RrdGraphDef graphDef = new RrdGraphDef();
			graphDef.setTimeSpan(getBeginDate()/1000, getEndDate()/1000);
			graphDef.datasource("in", "stats/stat-"+getBeginDate()+".rrd", "incon", ConsolFun.AVERAGE);
			graphDef.datasource("out", "stats/stat-"+getBeginDate()+".rrd", "outcon", ConsolFun.AVERAGE);
			graphDef.datasource("err", "stats/stat-"+getBeginDate()+".rrd", "errors", ConsolFun.AVERAGE);
			graphDef.datasource("min","1,in,*");
			graphDef.datasource("mout","1,out,*");
			graphDef.datasource("merr","1,err,*");
			graphDef.datasource("ain", "min,UN,0,min,IF");
			graphDef.datasource("aout", "mout,UN,0,mout,IF");
			graphDef.datasource("aerr", "merr,UN,0,merr,IF");
			graphDef.setVerticalLabel("connections");
			graphDef.area("aout", new Color(255, 215, 0), "Outgoing");
			graphDef.line("ain", new Color(62, 155, 210), "Incoming",1.8F);			
			graphDef.line("aerr", new Color(255, 51, 51), "Errors",1.8F);
			graphDef.comment("Total incoming: "+getNbInConnections());
			graphDef.comment("Total outcoming: "+getNbOutConnections());
			graphDef.comment("Total errors: "+getNbErrors());
			graphDef.gprint("min", ConsolFun.MAX, "Maximum incoming: %6.2lf %s");
			graphDef.gprint("mout", ConsolFun.MAX, "Maximum outgoing: %6.2lf %s");
			graphDef.gprint("min", ConsolFun.AVERAGE, "Average incoming: %6.2lf %s");
			graphDef.gprint("mout", ConsolFun.AVERAGE, "Average outgoing: %6.2lf %s");
			graphDef.setColor(RrdGraphConstants.COLOR_CANVAS, new Color(0,0,0));
			graphDef.setColor(RrdGraphConstants.COLOR_BACK, new Color(16,16,16));
			graphDef.setColor(RrdGraphConstants.COLOR_FONT, new Color(255,255,223));
			graphDef.setColor(RrdGraphConstants.COLOR_MGRID, new Color(51,127,191));
			graphDef.setColor(RrdGraphConstants.COLOR_GRID, new Color(97,89,0));
			graphDef.setColor(RrdGraphConstants.COLOR_FRAME, new Color(128,128,128));
			graphDef.setColor(RrdGraphConstants.COLOR_ARROW, new Color(255,0,153));
			graphDef.setColor(RrdGraphConstants.COLOR_SHADEA, new Color(5,5,5));
			graphDef.setColor(RrdGraphConstants.COLOR_SHADEB, new Color(5,5,5));
			graphDef.setTitle("Session #" + code + " from "
					+ ReportGenerator.dateFormat.format(new Date(beginDate)) + " to "
					+ ReportGenerator.dateFormat.format(new Date(endDate)));
			graphDef.setShowSignature(false);
			graphDef.setFilename(directory+"session-"+h+".png");
			files.add(directory+"session-"+h+".png");
			graphDef.setAntiAliasing(true);
			//graphDef.setImageFormat("PNG");
			graphDef.setWidth(800);
			graphDef.setHeight(250);
			
			RrdGraph graph = new RrdGraph(graphDef);
			
			BufferedImage bi = new BufferedImage(graph.getRrdGraphInfo().getWidth(),graph.getRrdGraphInfo().getHeight(),BufferedImage.TYPE_INT_RGB);
			Graphics2D g2d = (Graphics2D)bi.getGraphics();
			g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			graph.render(g2d);
			
			graphDef = new RrdGraphDef();
			graphDef.setTimeSpan(getBeginDate()/1000, getEndDate()/1000);
			graphDef.datasource("msg", "stats/stat-"+getBeginDate()+".rrd", "messages", ConsolFun.AVERAGE);
			graphDef.datasource("prc", "stats/stat-"+getBeginDate()+".rrd", "processed", ConsolFun.AVERAGE);
			graphDef.datasource("mmsg","1,msg,*");
			graphDef.datasource("mprc","1,prc,*");
			graphDef.datasource("amsg", "mmsg,UN,0,mmsg,IF");
			graphDef.datasource("aprc", "mprc,UN,0,mprc,IF");
			graphDef.setVerticalLabel("messages");
			graphDef.area("amsg", new Color(62, 155, 210), "Messages Received"/*,2.0F*/);
			graphDef.line("aprc", new Color(255, 215, 0), "Messages Processed",1.8F);
			long total = getForwardedMessages()+getProcessedMessages();
			long pfor = 0, ppro = 0;
			if (total > 0) {
				pfor = (getForwardedMessages()*100)/total;
				ppro = (getProcessedMessages()*100)/total;
			}
			graphDef.comment("Forwarded messages: "+getForwardedMessages()+" ("+pfor+"%)");
			graphDef.comment("Processed messages: "+getProcessedMessages()+" ("+ppro+"%)");
			graphDef.setColor(RrdGraphConstants.COLOR_CANVAS, new Color(0,0,0));
			graphDef.setColor(RrdGraphConstants.COLOR_BACK, new Color(16,16,16));
			graphDef.setColor(RrdGraphConstants.COLOR_FONT, new Color(255,255,223));
			graphDef.setColor(RrdGraphConstants.COLOR_MGRID, new Color(51,127,191));
			graphDef.setColor(RrdGraphConstants.COLOR_GRID, new Color(97,89,0));
			graphDef.setColor(RrdGraphConstants.COLOR_FRAME, new Color(128,128,128));
			graphDef.setColor(RrdGraphConstants.COLOR_ARROW, new Color(255,0,153));
			graphDef.setColor(RrdGraphConstants.COLOR_SHADEA, new Color(5,5,5));
			graphDef.setColor(RrdGraphConstants.COLOR_SHADEB, new Color(5,5,5));
			graphDef.setTitle("Messages Received");
			graphDef.setShowSignature(false);
			graphDef.setFilename(directory+"message-"+h+".png");
			files.add(directory+"message-"+h+".png");
			graphDef.setAntiAliasing(true);
			graphDef.setWidth(800);
			graphDef.setHeight(250);
			//graphDef.setImageFormat("PNG");
			
			graph = new RrdGraph(graphDef);
			
			bi = new BufferedImage(graph.getRrdGraphInfo().getWidth(),graph.getRrdGraphInfo().getHeight(),BufferedImage.TYPE_INT_RGB);
			g2d = (Graphics2D)bi.getGraphics();
			g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			graph.render(g2d);
			
			graphDef = new RrdGraphDef();
			graphDef.setTimeSpan(getBeginDate()/1000, getEndDate()/1000);
			graphDef.datasource("sz", "stats/stat-"+getBeginDate()+".rrd", "msize", ConsolFun.AVERAGE);
			graphDef.datasource("vol", "stats/stat-"+getBeginDate()+".rrd", "temp", ConsolFun.AVERAGE);
			graphDef.datasource("msz","1,sz,*");
			graphDef.datasource("mvol","1,vol,*");
			graphDef.datasource("amsz", "msz,UN,0,msz,IF");
			graphDef.datasource("avol", "mvol,UN,0,mvol,IF");
			graphDef.setVerticalLabel("bytes");
			graphDef.area("avol", new Color(254, 108, 1), "Temporary size");
			graphDef.area("amsz", new Color(62, 155, 210), "Processed Messages Size");
			graphDef.gprint("msz", ConsolFun.MAX, "Maximum size: %6.2lf %s bytes");
			graphDef.gprint("msz", ConsolFun.AVERAGE, "Averaged size: %6.2lf %s bytes");
			graphDef.gprint("vol", ConsolFun.MAX, "Maximum temporary size: %6.2lf %s bytes");
			graphDef.gprint("vol", ConsolFun.AVERAGE, "Averaged temporary size: %6.2lf %s bytes");
			graphDef.comment("Failed attachment saving: "+getFailedSaving());
			graphDef.comment("Free space on disk: "+new File(".").getFreeSpace()+" bytes");
			graphDef.setFilename(directory+"messages-size-"+h+".png");
			files.add(directory+"messages-size-"+h+".png");
			graphDef.setColor(RrdGraphConstants.COLOR_CANVAS, new Color(0,0,0));
			graphDef.setColor(RrdGraphConstants.COLOR_BACK, new Color(16,16,16));
			graphDef.setColor(RrdGraphConstants.COLOR_FONT, new Color(255,255,223));
			graphDef.setColor(RrdGraphConstants.COLOR_MGRID, new Color(51,127,191));
			graphDef.setColor(RrdGraphConstants.COLOR_GRID, new Color(97,89,0));
			graphDef.setColor(RrdGraphConstants.COLOR_FRAME, new Color(128,128,128));
			graphDef.setColor(RrdGraphConstants.COLOR_ARROW, new Color(255,0,153));
			graphDef.setColor(RrdGraphConstants.COLOR_SHADEA, new Color(5,5,5));
			graphDef.setColor(RrdGraphConstants.COLOR_SHADEB, new Color(5,5,5));
			graphDef.setTitle("Messages Size");
			graphDef.setShowSignature(false);
			graphDef.setAntiAliasing(true);
			graphDef.setWidth(800);
			graphDef.setHeight(250);
			//graphDef.setImageFormat("PNG");
			graph = new RrdGraph(graphDef);
			
			bi = new BufferedImage(graph.getRrdGraphInfo().getWidth(),graph.getRrdGraphInfo().getHeight(),BufferedImage.TYPE_INT_RGB);
			g2d = (Graphics2D)bi.getGraphics();
			g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			graph.render(g2d);
			
			graphDef = new RrdGraphDef();
			graphDef.setTimeSpan(getBeginDate()/1000, getEndDate()/1000);
			graphDef.datasource("j", "stats/stat-"+getBeginDate()+".rrd", "jvm", ConsolFun.AVERAGE);
			graphDef.datasource("jh", "stats/stat-"+getBeginDate()+".rrd", "jvmh", ConsolFun.AVERAGE);
			graphDef.datasource("jt", "stats/stat-"+getBeginDate()+".rrd", "jvmt", ConsolFun.AVERAGE);
			graphDef.datasource("mj","1,j,*");
			graphDef.datasource("mjh","1,jh,*");
			graphDef.datasource("mjt","1,jt,*");
			graphDef.datasource("amj", "mj,UN,0,mj,IF");
			graphDef.datasource("amjh", "mjh,UN,0,mjh,IF");
			graphDef.datasource("amjt", "mjt,UN,0,mjt,IF");
			graphDef.setVerticalLabel("bytes");
			graphDef.area("amjh", new Color(254, 33, 1), "JVM Heap Memory usage");
			graphDef.stack("amj", new Color(254, 108, 1), "JVM Non Heap Memory usage");
			graphDef.line("amjt", Color.WHITE, "Total Memory Usage",0.5F);
			graphDef.gprint("mj", ConsolFun.MAX, "Maximum Heap size: %6.2lf %s bytes");
			graphDef.gprint("mjh", ConsolFun.MAX, "Maximum Non Heap size: %6.2lf %s bytes");
			graphDef.gprint("mj", ConsolFun.AVERAGE, "Averaged Heap size: %6.2lf %s bytes");
			graphDef.gprint("mjh", ConsolFun.AVERAGE, "Averaged Non Heap size: %6.2lf %s bytes");
			graphDef.setFilename(directory+"memory-"+h+".png");
			files.add(directory+"memory-"+h+".png");
			graphDef.setColor(RrdGraphConstants.COLOR_CANVAS, new Color(0,0,0));
			graphDef.setColor(RrdGraphConstants.COLOR_BACK, new Color(16,16,16));
			graphDef.setColor(RrdGraphConstants.COLOR_FONT, new Color(255,255,223));
			graphDef.setColor(RrdGraphConstants.COLOR_MGRID, new Color(51,127,191));
			graphDef.setColor(RrdGraphConstants.COLOR_GRID, new Color(97,89,0));
			graphDef.setColor(RrdGraphConstants.COLOR_FRAME, new Color(128,128,128));
			graphDef.setColor(RrdGraphConstants.COLOR_ARROW, new Color(255,0,153));
			graphDef.setColor(RrdGraphConstants.COLOR_SHADEA, new Color(5,5,5));
			graphDef.setColor(RrdGraphConstants.COLOR_SHADEB, new Color(5,5,5));
			graphDef.setTitle("Memory Usage");
			graphDef.setShowSignature(false);
			graphDef.setAntiAliasing(true);
			graphDef.setWidth(800);
			//graphDef.setImageFormat("PNG");
			graphDef.setHeight(250);
			graph = new RrdGraph(graphDef);
			
			bi = new BufferedImage(graph.getRrdGraphInfo().getWidth(),graph.getRrdGraphInfo().getHeight(),BufferedImage.TYPE_INT_RGB);
			g2d = (Graphics2D)bi.getGraphics();
			g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			graph.render(g2d);
			
			graphDef = new RrdGraphDef();
			graphDef.setTimeSpan(getBeginDate()/1000, getEndDate()/1000);
			graphDef.datasource("rt", "stats/stat-"+getBeginDate()+".rrd", "rtime", ConsolFun.AVERAGE);
			graphDef.datasource("pt", "stats/stat-"+getBeginDate()+".rrd", "ptime", ConsolFun.AVERAGE);
			graphDef.datasource("st", "stats/stat-"+getBeginDate()+".rrd", "stime", ConsolFun.AVERAGE);
			graphDef.datasource("it", "stats/stat-"+getBeginDate()+".rrd", "itime", ConsolFun.AVERAGE);
			graphDef.datasource("ft", "stats/stat-"+getBeginDate()+".rrd", "ftime", ConsolFun.AVERAGE);
			graphDef.datasource("ct", "stats/stat-"+getBeginDate()+".rrd", "ctime", ConsolFun.AVERAGE);
			graphDef.datasource("mrt","1,rt,*");
			graphDef.datasource("mpt","1,pt,*");
			graphDef.datasource("mst","1,st,*");
			graphDef.datasource("mit","1,it,*");
			graphDef.datasource("mft","1,ft,*");
			graphDef.datasource("mct","1,ct,*");
			graphDef.datasource("amrt", "mrt,UN,0,mrt,IF");
			graphDef.datasource("ampt", "mrt,UN,0,mpt,IF");
			graphDef.datasource("amst", "mrt,UN,0,mst,IF");
			graphDef.datasource("amit", "mrt,UN,0,mit,IF");
			graphDef.datasource("amft", "mrt,UN,0,mft,IF");
			graphDef.datasource("amct", "mrt,UN,0,mct,IF");
			graphDef.setVerticalLabel("milliseconds");
			//graphDef.area("amrt", new Color(1, 1, 230), "Receiving time");
			graphDef.area("ampt", new Color(1, 108, 254), "Parsing time");
			graphDef.stack("amst", new Color(150, 150, 254), "Saving time");
			graphDef.stack("amit", new Color(254, 177, 107), "Injecting back time");
			//graphDef.stack("amft", new Color(254, 200, 159), "Forwarding time");
			//graphDef.stack("amct", new Color(254, 230, 210), "Cleaning time");
			graphDef.gprint("mrt", ConsolFun.MAX, "Maximum receiving time: %6.2lf %s ms");
			graphDef.gprint("mpt", ConsolFun.MAX, "Maximum parsing time: %6.2lf %s ms");
			graphDef.gprint("mst", ConsolFun.MAX, "Maximum saving time: %6.2lf %s ms");
			graphDef.gprint("mit", ConsolFun.MAX, "Maximum injecting back time: %6.2lf %s ms");
			graphDef.gprint("mft", ConsolFun.MAX, "Maximum forwarding time: %6.2lf %s ms");
			graphDef.gprint("mct", ConsolFun.MAX, "Maximum cleaning time: %6.2lf %s ms");			
			graphDef.gprint("mrt", ConsolFun.AVERAGE, "Average receiving time: %6.2lf %s ms");
			graphDef.gprint("mpt", ConsolFun.AVERAGE, "Average parsing time: %6.2lf %s ms");
			graphDef.gprint("mst", ConsolFun.AVERAGE, "Average saving time: %6.2lf %s ms");
			graphDef.gprint("mit", ConsolFun.AVERAGE, "Average injecting back time: %6.2lf %s ms");
			graphDef.gprint("mft", ConsolFun.AVERAGE, "Average forwarding time: %6.2lf %s ms");
			graphDef.gprint("mct", ConsolFun.AVERAGE, "Average cleaning time: %6.2lf %s ms");
			graphDef.setFilename(directory+"time-"+h+".png");
			files.add(directory+"time-"+h+".png");
			graphDef.setColor(RrdGraphConstants.COLOR_CANVAS, new Color(0,0,0));
			graphDef.setColor(RrdGraphConstants.COLOR_BACK, new Color(16,16,16));
			graphDef.setColor(RrdGraphConstants.COLOR_FONT, new Color(255,255,223));
			graphDef.setColor(RrdGraphConstants.COLOR_MGRID, new Color(51,127,191));
			graphDef.setColor(RrdGraphConstants.COLOR_GRID, new Color(97,89,0));
			graphDef.setColor(RrdGraphConstants.COLOR_FRAME, new Color(128,128,128));
			graphDef.setColor(RrdGraphConstants.COLOR_ARROW, new Color(255,0,153));
			graphDef.setColor(RrdGraphConstants.COLOR_SHADEA, new Color(5,5,5));
			graphDef.setColor(RrdGraphConstants.COLOR_SHADEB, new Color(5,5,5));
			graphDef.setTitle("Time consumption");
			graphDef.setShowSignature(false);
			graphDef.setAntiAliasing(true);
			graphDef.setWidth(800);
			graphDef.setHeight(250);
			//graphDef.setImageFormat("PNG");
			graph = new RrdGraph(graphDef);
			
			bi = new BufferedImage(graph.getRrdGraphInfo().getWidth(),graph.getRrdGraphInfo().getHeight(),BufferedImage.TYPE_INT_RGB);
			g2d = (Graphics2D)bi.getGraphics();
			g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			graph.render(g2d);
			
			return files;
		}
		return null;
	}
	
	public long getMessagesNumber() {
		return processedMessages+forwardedMessages;
	}
	
	public long getParsingTime() {
		return parsingTime;
	}
	
	public void incrementParsingTime(long time) {
		parsingTime += time;
	}
	
	public long getSavingTime() {
		return savingTime;
	}
	
	public void incrementSavingTime(long time) {
		parsingTime += time;
	}
	
	public long getSendingTime() {
		return sendingTime;
	}
	
	public void incrementSendingTime(long time) {
		parsingTime += time;
	}
	
	public long getAttachmentPerMessage() {
		return processedAttachments/processedMessages;
	}
	
	public long getAttemptNumber() {
		return attempt/processedAttachments;
	}

	public long getBeginDate() {
		return beginDate;
	}

	public void setBeginDate(long beginDate) {
		this.beginDate = beginDate;
	}

	public long getEndDate() {
		return endDate;
	}

	public void setEndDate(long endDate) {
		this.endDate = endDate;
	}

	public long getNbInConnections() {
		return nbInConnections;
	}

	public void setNbInConnections(long nbInConnections) {
		this.nbInConnections = nbInConnections;
	}
	
	public void incrementNbInConnections() {
		this.nbInConnections++;
	}

	public long getNbErrors() {
		return nbErrors;
	}

	public void setNbErrors(long nbInErrors) {
		this.nbErrors = nbInErrors;
	}

	public void incrementNbInErrors() {
		this.nbErrors++;
	}
	
	public long getNbOutConnections() {
		return nbOutConnections;
	}

	public void setNbOutConnections(long nbOutConnections) {
		this.nbOutConnections = nbOutConnections;
	}
	
	public void incrementNbOutConnections() {
		this.nbOutConnections++;
	}

	public long getForwardedMessages() {
		return forwardedMessages;
	}

	public void setForwardedMessages(long forwardedMessages) {
		this.forwardedMessages = forwardedMessages;
	}
	
	public void incrementForwardedMessages() {
		this.forwardedMessages++;
	}

	public long getFreespaceAfter() {
		return freespaceAfter;
	}

	public void setFreespaceAfter(long freespaceAfter) {
		this.freespaceAfter = freespaceAfter;
	}

	public long getFreespaceBefore() {
		return freespaceBefore;
	}

	public void setFreespaceBefore(long freespaceBefore) {
		this.freespaceBefore = freespaceBefore;
	}

	public long getProcessedMessages() {
		return processedMessages;
	}

	public void setProcessedMessages(long processedMessages) {
		this.processedMessages = processedMessages;
	}
	
	public void incrementProcessedMessages() {
		this.processedMessages++;
	}

	public long getFailedSaving() {
		return failedSaving;
	}

	public void setFailedSaving(long failedSaving) {
		this.failedSaving = failedSaving;
	}
	
	public void incrementFailedSaving() {
		this.failedSaving++;
	}

	public long getProcessedAttachments() {
		return processedAttachments;
	}

	public void setProcessedAttachments(long processedAttachments) {
		this.processedAttachments = processedAttachments;
	}
	
	public void incrementProcessedAttachments() {
		this.processedAttachments++;
	}

	public long getAttempt() {
		return attempt;
	}

	public void setAttempt(long attempt) {
		this.attempt = attempt;
	}
	
	public void incrementAttempt(long attempt) {
		this.attempt += attempt;
	}
	
}
