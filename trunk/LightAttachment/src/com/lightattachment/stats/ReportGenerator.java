package com.lightattachment.stats;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.StringTokenizer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.log4j.Logger;

import com.lightattachment.mails.LightAttachment;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Font;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;

/** Generate activity reports. */
public class ReportGenerator {

	/** The session for which the report must be generated. */
	private StatisticSession session;
	
	/** List of mail address to which the report must be sent. */
	private ArrayList<String> mailto;

	// Used for RRA choice (see StatisticAppender)
	
	public static final int YEAR = 1;
	/** 4 years in milliseconds */
	public static final long YEAR_TIME_LIMIT = 126144000000L;
	public static final int MONTH = 2;
	/** 4 months in milliseconds */
	public static final long MONTH_TIME_LIMIT =  9676800000L;
	public static final int WEEK = 3;
	/** 4 weeks in milliseconds */
	public static final long WEEK_TIME_LIMIT =   2419200000L;
	public static final int DAY = 4;
	/** 4 days in milliseconds */
	public static final long DAY_TIME_LIMIT =     345600000L;
	public static final int HOUR = 5;
	/** 4 hours in milliseconds */
	public static final long HOUR_TIME_LIMIT =     14400000L;
	public static final int MINUTE = 6;
	/** 4 minutes in milliseconds */
	public static final long MINUTE_TIME_LIMIT =     240000L;
	public static final int SECOND = 7;
	
	/** Date format used in the report. */
	public static final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
	
	/** Logger used to trace activity. */
	static Logger log = Logger.getLogger(ReportGenerator.class);
	
	/** Build a <code>ReportGenerator</code>.
	 * @param session for which the report must be generated */
	public ReportGenerator(StatisticSession session) {
		this.session = session;
		mailto = new ArrayList<String>();
		StringTokenizer st = new StringTokenizer(LightAttachment.config.getString("report.mailto")," ");
        while (st.hasMoreTokens()) getMailto().add(st.nextToken());
        File dir = new File("stats/reports/report-" + hashCode());
        if (!dir.exists()) if (dir.mkdir()) System.out.println("Folder stats/reports/report-" + hashCode()+" created\n");
	}

	/** Generates a text report. In fact nothing because the whole report is on graphics.
	 * Zip a folder with the text report and graphics. Sends them. 
	 * The whole generation is done within a thread. */
	public void generateTextReport() throws IOException {
		final int hashCode = hashCode();
		
		new StoppableThread() {
			public void run() {
				try {
					System.out.print("Generating text report...");
					String file = "stats/reports/report-" + hashCode + "/report.txt";
					PrintWriter pw = new PrintWriter(file);

					session.generateTextReport(pw, hashCode);

					pw.close();
					System.out.println(" done");
					log.info("Text report "+hashCode+" generated");

					ArrayList<String> files = new ArrayList<String>();
					files.add(file);

					System.out.print("Generating plots...");
					generateRrdPlot();
					System.out.println(" done");
					log.info("Report "+hashCode+" plots generated");

					System.out.print("Compress report...");
					zipFolder(new File("stats/reports/report-" + hashCode + "/"), new File("stats/reports/report-" + hashCode
							+ ".zip"));
					System.out.println(" done");
					log.info("Report "+hashCode+" compressed");

					for (String m : mailto) {
						SendReportThread sd = new SendReportThread(LightAttachment.config.getString("report.mailfrom"), m,
								"LightAttachment Activity Report #" + hashCode + " of the "
										+ ReportGenerator.dateFormat.format(new Date()),
								"LightAttachment Activity Report #" + hashCode, LightAttachment.config.getString("report.smtp"),
								"stats/reports/report-" + hashCode + ".zip", hashCode);
						sd.start();
						while (!sd.isDone()) sleep(100);
					}
					
					setDone(true);
				} catch (IOException e) {
					e.printStackTrace();
					log.error(e.getMessage(), e);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				setDone(true);
			}
		}.start();
	}
	
	/** Generates a html report.
	 * Zip a folder with the html report and graphics. Sends them. 
	 * The whole generation is done within a thread. */
	public void generateHtmlReport() throws IOException {
		
		final int hashCode = hashCode();
		
		new StoppableThread() {
			public void run() {
				try {
					System.out.print("Generating html report...");
					String file = "stats/reports/report-" + hashCode + "/report.html";
					PrintWriter pw = new PrintWriter(file);

					session.generateHtmlReport(pw, hashCode);
					log.info("Html report "+hashCode+" generated");

					pw.close();

					System.out.println(" done");

					System.out.print("Generating plots...");
					generateRrdPlot();
					System.out.println(" done");
					log.info("Report "+hashCode+" plots generated");

					copyFile(new File("stats/reports/logo.png"), new File("stats/reports/report-" + hashCode + "/logo.png"));
					copyFile(new File("stats/reports/favicon.png"), new File("stats/reports/report-" + hashCode
							+ "/favicon.png"));

					System.out.print("Compress report...");
					zipFolder(new File("stats/reports/report-" + hashCode + "/"), new File("stats/reports/report-" + hashCode
							+ ".zip"));
					System.out.println(" done");
					log.info("Report "+hashCode+" compressed");

					for (String m : mailto) {
						SendReportThread sd = new SendReportThread(LightAttachment.config.getString("report.mailfrom"), m,
								"LightAttachment Activity Report #" + hashCode + " of the " + ReportGenerator.dateFormat.format(new Date()),
								"LightAttachment Activity Report #" + hashCode,
								LightAttachment.config.getString("report.smtp"), 
								"stats/reports/report-" + hashCode + ".zip", hashCode);
						sd.start();
						while (!sd.isDone()) sleep(100);
					}
					
					setDone(true);
				} catch (IOException e) {
					e.printStackTrace();
					log.error(e.getMessage(), e);
				} catch (InterruptedException e) {
					e.printStackTrace();
					log.error(e.getMessage(), e);
				}
				setDone(true);
			}
		}.start();
	}

	/** Generates a pdf report and sends it.
	 * The whole generation is done within a thread. */	
	public void generatePdfReport() throws IOException {
		
		final int hashCode = hashCode();
		
		new StoppableThread() {
			public void run() {
				try {			
					setDone(false);
					System.out.print("Generating plots...");
					ArrayList<String> files = generateRrdPlot();
					System.out.println(" done");
					log.info("Report "+hashCode+" plots generated");
					
					System.out.print("Generating pdf report...");
					Document document = new Document();
					PdfWriter.getInstance(document, 
						new FileOutputStream("stats/reports/report-" + hashCode + "/report-"+hashCode+".pdf"));
					document.open();

					Paragraph p = new Paragraph();
			        p.add(new Chunk("LightAttachment Report\n", new Font(Font.HELVETICA, 22, Font.BOLD)));
			        Paragraph p2 = new Paragraph();
			        p2.add(new Chunk("Session ", new Font(Font.HELVETICA, 18)));
			        p2.add(new Chunk("#"+hashCode, new Font(Font.COURIER, 18)));
			        p2.add(new Chunk(" on ", new Font(Font.HELVETICA, 18)));
			        p2.add(new Chunk(LightAttachment.config.getString("hostname"), new Font(Font.COURIER, 18)));
			        p.setAlignment(Paragraph.ALIGN_CENTER);
			        p2.setAlignment(Paragraph.ALIGN_CENTER);
					
			        document.add(p);
			        document.add(p2);
			        
					if (files != null) {
						for (String f : files) {
							com.lowagie.text.Image image = com.lowagie.text.Image.getInstance(f);
							image.scalePercent(60);
							document.add(image);
							new File(f).delete();
						}
					}
					
					document.close();
					System.out.println(" done");
					log.info("Pdf report "+hashCode+" generated");

					for (String m : mailto) {
						SendReportThread sd = new SendReportThread(LightAttachment.config.getString("report.mailfrom"), m,
								"LightAttachment Activity Report #" + hashCode + " of the "
										+ ReportGenerator.dateFormat.format(new Date()),
								"LightAttachment Activity Report #" + hashCode,
								LightAttachment.config.getString("report.smtp"), 
								"stats/reports/report-" + hashCode + "/report-"+hashCode+".pdf", hashCode);
						sd.start();
						while (!sd.isDone()) sleep(100);
					}
					
					setDone(true);
				} catch (IOException e) {
					e.printStackTrace();
					log.error(e.getMessage(), e);
				} catch (InterruptedException e) {
					e.printStackTrace();
					log.error(e.getMessage(), e);
				} catch (DocumentException e) {
					e.printStackTrace();
					log.error(e.getMessage(), e);
				}
				setDone(true);
			}
		}.start();
	}
	
	/** Generate graphics for the session.
	 * @return file list of the graphics */
	public ArrayList<String> generateRrdPlot() throws IOException {	
		return session.generateRrdPlot("stats/reports/report-" + hashCode()+"/",hashCode());
	}
	
	/** Copy a file to another. 
	 * @param in the source
	 * @param out the destination */
	public static void copyFile(File in, File out) throws IOException {
		FileChannel inChannel = new FileInputStream(in).getChannel();
		FileChannel outChannel = new FileOutputStream(out).getChannel();
		try {
			inChannel.transferTo(0, inChannel.size(), outChannel);
		} catch (IOException e) {
			throw e;
		} finally {
			if (inChannel != null)
				inChannel.close();
			if (outChannel != null)
				outChannel.close();
		}
	}
	
	/** Zip a folder.
	 * @param inFolder the folder to zip. 
	 * @param outFile the destination
	 * @return the zipped file */
	public static File zipFolder(File inFolder, File outFile) {
		try {
			// compress outfile stream
			ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(outFile)));

			// writting stream
			BufferedInputStream in = null;

			byte[] data = new byte[4024];
			String files[] = inFolder.list();

			for (int i = 0; i < files.length; i++) {
				// System.out.println("Adding: " + files[i]);
				in = new BufferedInputStream(new FileInputStream(inFolder.getPath() + "/" + files[i]), 4024);

				out.putNextEntry(new ZipEntry(files[i])); // write data header (name, size, etc)
				int count;
				while ((count = in.read(data, 0, 4024)) != -1) {
					out.write(data, 0, count);
				}
				out.closeEntry(); // close each entry
			}
			cleanUp(out);
			cleanUp(in);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return new File(outFile + ".zip");
	}
	
	/** Return the HTML report template in a <code>StringBuffer</code>.
	 * @return the template */
	public static StringBuffer getTemplate() throws IOException, FileNotFoundException {
		StringBuffer template = new StringBuffer();
		
		BufferedReader reader = new BufferedReader(new FileReader(LightAttachment.config.getString("report.template")));
		String line = null;
		while ((line = reader.readLine()) != null) template.append(line);		
		reader.close();
		
		return template;
	}
	
	private static void cleanUp(InputStream in) throws Exception {
		in.close();
	}

	private static void cleanUp(OutputStream out) throws Exception {
		out.flush();
		out.close();
	}
	
	public StatisticSession getSession() {
		return session;
	}
	
	public ArrayList<String> getMailto() {
		return mailto;
	}

	@Override
	public int hashCode() {
		return Math.abs((int)session.getBeginDate());
	}

}
