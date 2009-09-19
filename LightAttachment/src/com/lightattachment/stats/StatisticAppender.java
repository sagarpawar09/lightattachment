package com.lightattachment.stats;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.util.Date;
import java.util.regex.Matcher;

import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggingEvent;
import org.rrd4j.ConsolFun;
import org.rrd4j.DsType;
import org.rrd4j.core.RrdDb;
import org.rrd4j.core.RrdDef;
import org.rrd4j.core.Sample;

import com.lightattachment.mails.LightAttachment;

/** This appender is used to extract statistical information from the logs. */
public class StatisticAppender extends AppenderSkeleton {

	/** The statistical session. */
	private StatisticSession session;
	
	/** The RRD database. */
	private RrdDb rrddb;
	
	/** The report generator. */
	private ReportGenerator report;
	
	/** The current data sample of the RRD database. */
	private Sample messageSample;
	
	/** The temporary volume in bytes. */
	private long volume;

	/** Logger used to trace activity. */
	static Logger log = Logger.getLogger(StatisticAppender.class);
	
	/** Build a <code>StatisticAppender</code> and call <code>init</code>. */
	public StatisticAppender() {
		init();
	}
	
	/** Is called for each log event. 
	 * Each log message is matched against regex in order to extract data. 
	 * @param logEvent the log event */
	protected void append(LoggingEvent logEvent) {

		if (!logEvent.getLoggerName().contains("StatisticAppender")) {
			if (logEvent.timeStamp <= session.getEndDate()) {
				
				try { // If no logs, memory will get down to 0 on graph
					MemoryMXBean mbean = ManagementFactory.getMemoryMXBean();
					if (!rrddb.isClosed()) {
						if (messageSample == null) {
							messageSample = rrddb.createSample(logEvent.timeStamp / 1000);
							messageSample.setValue("jvm", mbean.getNonHeapMemoryUsage().getUsed());
							messageSample.setValue("jvmh", mbean.getHeapMemoryUsage().getUsed());
							messageSample.setValue("jvmt", mbean.getNonHeapMemoryUsage().getUsed() + mbean.getHeapMemoryUsage().getUsed() + 1);
							messageSample.setValue("temp", volume);
						} else {
							if (logEvent.timeStamp / 1000 > messageSample.getTime()) {
								if (rrddb.getLastUpdateTime() < messageSample.getTime()) {
									System.err.println(messageSample.dump());
									messageSample.update();
								}
								messageSample = rrddb.createSample(logEvent.timeStamp / 1000);
								messageSample.setValue("jvm", mbean.getNonHeapMemoryUsage().getUsed());
								messageSample.setValue("jvmh", mbean.getHeapMemoryUsage().getUsed());
								messageSample.setValue("jvmt", mbean.getNonHeapMemoryUsage().getUsed() + mbean.getHeapMemoryUsage().getUsed() + 1);
								messageSample.setValue("temp", volume);
							} else {
								messageSample.setValue("jvm", mbean.getNonHeapMemoryUsage().getUsed());
								messageSample.setValue("jvmh", mbean.getHeapMemoryUsage().getUsed());
								messageSample.setValue("jvmt", mbean.getNonHeapMemoryUsage().getUsed() + mbean.getHeapMemoryUsage().getUsed() + 1);
								messageSample.setValue("temp", volume);
							}
						}
					}
				} catch (IOException e) {
					log.error(e.getMessage(), e);
					e.printStackTrace();
				}
				
				String event = (String) logEvent.getMessage();

				if (logEvent.getLevel().equals(Level.ERROR)) {
					session.incrementNbInErrors();
					try {
						if (!rrddb.isClosed()) {
							
							if (messageSample == null) {							
								messageSample = rrddb.createSample(logEvent.timeStamp/1000);
								messageSample.setValue("errors", 1);
								messageSample.setValue("temp", volume);
							} else {
								if (logEvent.timeStamp / 1000 > messageSample.getTime()) {									
									if (rrddb.getLastUpdateTime() < messageSample.getTime()) {																				
										System.err.println(messageSample.dump());
										messageSample.update();									
									}
									messageSample = rrddb.createSample(logEvent.timeStamp / 1000);
									messageSample.setValue("errors", 1);
									messageSample.setValue("temp", volume);
								} else {
									double previous;
									if (new Double(messageSample.getValues()[5]).isNaN()) previous = 1;
									else previous = messageSample.getValues()[5] + 1;
									messageSample.setValue("errors", previous);
									if (new Double(messageSample.getValues()[9]).isNaN()) previous = volume;
									else previous = messageSample.getValues()[9] + volume;
									messageSample.setValue("temp", previous);
								}
							}
						}
					} catch (IOException e) {
						log.error(e.getMessage(),e);
						e.printStackTrace();
					}
				}

				Matcher inMatcher = StatisticMessage.inPattern.matcher(event);
				if (inMatcher.find()) {
					/*session.addMessage(new StatisticMessage(logEvent.timeStamp, 				
						Long.parseLong(inMatcher.group(3)), Integer.parseInt(inMatcher.group(4)), 
						Long.parseLong(inMatcher.group(5)), Long.parseLong(inMatcher.group(5))));*/
					session.incrementProcessedMessages();
					try {
						if (!rrddb.isClosed()) {
							if (messageSample == null) {							
								messageSample = rrddb.createSample(logEvent.timeStamp/1000);
								messageSample.setValue("messages", 1);
								messageSample.setValue("processed", 1);
								messageSample.setValue("msize", Long.parseLong(inMatcher.group(5)));
								volume += Long.parseLong(inMatcher.group(5));
								messageSample.setValue("temp", volume);
							} else {
								if (logEvent.timeStamp / 1000 > messageSample.getTime()) {									
									if (rrddb.getLastUpdateTime() < messageSample.getTime()) {
										System.err.println(messageSample.dump());
										messageSample.update();									
									}
									messageSample = rrddb.createSample(logEvent.timeStamp / 1000);
									messageSample.setValue("messages", 1);
									messageSample.setValue("processed", 1);
									messageSample.setValue("msize", Long.parseLong(inMatcher.group(5)));
									volume += Long.parseLong(inMatcher.group(5));
									messageSample.setValue("temp", volume);
								} else {
									double previous;
									double sprevious;
									if (new Double(messageSample.getValues()[0]).isNaN()) previous = 1;
									else previous = messageSample.getValues()[0] + 1;
									if (new Double(messageSample.getValues()[2]).isNaN()) sprevious = Long.parseLong(inMatcher.group(5));
									else sprevious = messageSample.getValues()[2] + Long.parseLong(inMatcher.group(5));
									messageSample.setValue("messages", previous);
									messageSample.setValue("processed", previous);
									messageSample.setValue("msize", sprevious);
									volume += sprevious;
									messageSample.setValue("temp", volume);
								}
							}
						}
					} catch (IOException e) {
						log.error(e.getMessage(),e);
						e.printStackTrace();
					}
				}

				Matcher receivingMatcher = StatisticMessage.receivingPattern.matcher(event);
				if (receivingMatcher.find()) {
					try {
						if (!rrddb.isClosed()) {							
							if (messageSample == null) {							
								messageSample = rrddb.createSample(logEvent.timeStamp/1000);
								messageSample.setValue("rtime", Long.parseLong(receivingMatcher.group(1)));
							} else {
								if (logEvent.timeStamp / 1000 > messageSample.getTime()) {
									if (rrddb.getLastUpdateTime() < messageSample.getTime()) {
										System.err.println(messageSample.dump());
										messageSample.update();									
									}
									messageSample = rrddb.createSample(logEvent.timeStamp / 1000);
									messageSample.setValue("rtime", Long.parseLong(receivingMatcher.group(1)));
								} else {
									double previous;
									if (new Double(messageSample.getValues()[10]).isNaN()) previous = Long.parseLong(receivingMatcher.group(1));
									else previous = messageSample.getValues()[10] + Long.parseLong(receivingMatcher.group(1));
									messageSample.setValue("rtime", previous);
								}
							}
						}
					} catch (IOException e) {
						log.error(e.getMessage(),e);
						e.printStackTrace();
					}
				}
				
				Matcher parsingMatcher = StatisticMessage.parsingPattern.matcher(event);
				if (parsingMatcher.find()) {
					try {
						if (!rrddb.isClosed()) {							
							if (messageSample == null) {							
								messageSample = rrddb.createSample(logEvent.timeStamp/1000);
								messageSample.setValue("ptime", Long.parseLong(parsingMatcher.group(1)));
							} else {
								if (logEvent.timeStamp / 1000 > messageSample.getTime()) {
									if (rrddb.getLastUpdateTime() < messageSample.getTime()) {
										System.err.println(messageSample.dump());
										messageSample.update();									
									}
									messageSample = rrddb.createSample(logEvent.timeStamp / 1000);
									messageSample.setValue("ptime", Long.parseLong(parsingMatcher.group(1)));
								} else {
									double previous;
									if (new Double(messageSample.getValues()[11]).isNaN()) previous = Long.parseLong(parsingMatcher.group(1));
									else previous = messageSample.getValues()[11] + Long.parseLong(parsingMatcher.group(1));
									messageSample.setValue("ptime", previous);
								}
							}
						}
					} catch (IOException e) {
						log.error(e.getMessage(),e);
						e.printStackTrace();
					}
				}

				Matcher saveMatcher = StatisticMessage.savePattern.matcher(event);
				if (saveMatcher.find()) {
					try {
						if (!rrddb.isClosed()) {							
							if (messageSample == null) {							
								messageSample = rrddb.createSample(logEvent.timeStamp/1000);
								messageSample.setValue("stime", Long.parseLong(saveMatcher.group(1)));
							} else {
								if (logEvent.timeStamp / 1000 > messageSample.getTime()) {
									if (rrddb.getLastUpdateTime() < messageSample.getTime()) {
										System.err.println(messageSample.dump());
										messageSample.update();									
									}
									messageSample = rrddb.createSample(logEvent.timeStamp / 1000);
									messageSample.setValue("stime", Long.parseLong(saveMatcher.group(1)));
								} else {
									double previous;
									if (new Double(messageSample.getValues()[12]).isNaN()) previous = Long.parseLong(saveMatcher.group(1));
									else previous = messageSample.getValues()[12] + Long.parseLong(saveMatcher.group(1));
									messageSample.setValue("stime", previous);
								}
							}
						}
					} catch (IOException e) {
						log.error(e.getMessage(),e);
						e.printStackTrace();
					}
				}

				Matcher outMatcher = StatisticMessage.outPattern.matcher(event);
				if (outMatcher.find()) {
					try {
						if (!rrddb.isClosed()) {							
							if (messageSample == null) {							
								messageSample = rrddb.createSample(logEvent.timeStamp/1000);
								messageSample.setValue("itime", Long.parseLong(outMatcher.group(1)));
							} else {
								if (logEvent.timeStamp / 1000 > messageSample.getTime()) {
									if (rrddb.getLastUpdateTime() < messageSample.getTime()) {
										System.err.println(messageSample.dump());
										messageSample.update();									
									}
									messageSample = rrddb.createSample(logEvent.timeStamp / 1000);
									messageSample.setValue("itime", Long.parseLong(outMatcher.group(1)));
								} else {
									double previous;
									if (new Double(messageSample.getValues()[13]).isNaN()) previous = Long.parseLong(outMatcher.group(1));
									else previous = messageSample.getValues()[13] + Long.parseLong(outMatcher.group(1));
									messageSample.setValue("itime", previous);
								}
							}
						}
					} catch (IOException e) {
						log.error(e.getMessage(),e);
						e.printStackTrace();
					}
				}

				Matcher outUnchangedMatcher = StatisticMessage.outUnchangedPattern.matcher(event);
				if (outUnchangedMatcher.find()) {
					try {
						if (!rrddb.isClosed()) {							
							if (messageSample == null) {							
								messageSample = rrddb.createSample(logEvent.timeStamp/1000);
								messageSample.setValue("ftime", Long.parseLong(outUnchangedMatcher.group(1)));
							} else {
								if (logEvent.timeStamp / 1000 > messageSample.getTime()) {
									if (rrddb.getLastUpdateTime() < messageSample.getTime()) {
										System.err.println(messageSample.dump());
										messageSample.update();									
									}
									messageSample = rrddb.createSample(logEvent.timeStamp / 1000);
									messageSample.setValue("ftime", Long.parseLong(outUnchangedMatcher.group(1)));
								} else {
									double previous;
									if (new Double(messageSample.getValues()[14]).isNaN()) previous = Long.parseLong(outUnchangedMatcher.group(1));
									else previous = messageSample.getValues()[14] + Long.parseLong(outUnchangedMatcher.group(1));
									messageSample.setValue("ftime", previous);
								}
							}
						}
					} catch (IOException e) {
						log.error(e.getMessage(),e);
						e.printStackTrace();
					}
				}
				
				Matcher cleaningMatcher = StatisticMessage.cleaningPattern.matcher(event);
				if (cleaningMatcher.find()) {
					try {
						if (!rrddb.isClosed()) {							
							if (messageSample == null) {							
								messageSample = rrddb.createSample(logEvent.timeStamp/1000);
								messageSample.setValue("ctime", Long.parseLong(cleaningMatcher.group(1)));
							} else {
								if (logEvent.timeStamp / 1000 > messageSample.getTime()) {
									if (rrddb.getLastUpdateTime() < messageSample.getTime()) {
										System.err.println(messageSample.dump());
										messageSample.update();									
									}
									messageSample = rrddb.createSample(logEvent.timeStamp / 1000);
									messageSample.setValue("ctime", Long.parseLong(cleaningMatcher.group(1)));
								} else {
									double previous;
									if (new Double(messageSample.getValues()[15]).isNaN()) previous = Long.parseLong(cleaningMatcher.group(1));
									else previous = messageSample.getValues()[15] + Long.parseLong(cleaningMatcher.group(1));
									messageSample.setValue("ctime", previous);
								}
							}
						}
					} catch (IOException e) {
						log.error(e.getMessage(),e);
						e.printStackTrace();
					}
				}
				
				Matcher forwardedMatcher = StatisticMessage.forwardedPattern.matcher(event);
				if (forwardedMatcher.find()) {
					session.incrementFailedSaving();
				}

				Matcher ignoreForwardedMatcher = StatisticMessage.ignoreForwardedPattern.matcher(event);
				if (ignoreForwardedMatcher.find()) {
					session.incrementForwardedMessages();
					try {
						if (!rrddb.isClosed()) {
							
							if (messageSample == null) {							
								messageSample = rrddb.createSample(logEvent.timeStamp/1000);
								messageSample.setValue("messages", 1);
								messageSample.setValue("temp", volume);
							} else {
								if (logEvent.timeStamp / 1000 > messageSample.getTime()) {
									if (rrddb.getLastUpdateTime() < messageSample.getTime()) {
										System.err.println(messageSample.dump());
										messageSample.update();									
									}
									messageSample = rrddb.createSample(logEvent.timeStamp / 1000);
									messageSample.setValue("messages", 1);
									messageSample.setValue("temp", volume);
								} else {
									double previous;
									if (new Double(messageSample.getValues()[0]).isNaN()) previous = 1;
									else previous = messageSample.getValues()[0] + 1;
									messageSample.setValue("messages", previous);
									if (new Double(messageSample.getValues()[9]).isNaN()) previous = volume;
									else previous = messageSample.getValues()[9] + volume;
									messageSample.setValue("temp", previous);
								}
							}
						}
					} catch (IOException e) {
						log.error(e.getMessage(),e);
						e.printStackTrace();
					}
				}
				
				
				Matcher ignoreForwardedSizeMatcher = StatisticMessage.ignoreForwardedSizePattern.matcher(event);
				if (ignoreForwardedSizeMatcher.find()) {
					volume += Long.parseLong(ignoreForwardedSizeMatcher.group(1));					
				}

				Matcher inConnectionMatcher = StatisticSession.inConnectionPattern.matcher(event);
				if (inConnectionMatcher.find()) {
					session.incrementNbInConnections();
					try {
						if (!rrddb.isClosed()) {
							
							if (messageSample == null) {							
								messageSample = rrddb.createSample(logEvent.timeStamp/1000);
								messageSample.setValue("incon", 1);
								messageSample.setValue("temp", volume);
							} else {
								if (logEvent.timeStamp / 1000 > messageSample.getTime()) {
									if (rrddb.getLastUpdateTime() < messageSample.getTime()) {
										System.err.println(messageSample.dump());
										messageSample.update();									
									}
									messageSample = rrddb.createSample(logEvent.timeStamp / 1000);
									messageSample.setValue("incon", 1);
									messageSample.setValue("temp", volume);
								} else {
									double previous;
									if (new Double(messageSample.getValues()[3]).isNaN()) previous = 1;
									else previous = messageSample.getValues()[3] + 1;
									messageSample.setValue("incon", previous);
									if (new Double(messageSample.getValues()[9]).isNaN()) previous = volume;
									else previous = messageSample.getValues()[9] + volume;
									messageSample.setValue("temp", previous);
								}
							}
						}
					} catch (IOException e) {
						log.error(e.getMessage(),e);
						e.printStackTrace();
					}
				}

				Matcher outConnectionMatcher = StatisticSession.outConnectionPattern.matcher(event);
				if (outConnectionMatcher.find()) {
					session.incrementNbOutConnections();
					try {
						if (!rrddb.isClosed()) {
							
							if (messageSample == null) {							
								messageSample = rrddb.createSample(logEvent.timeStamp/1000);
								messageSample.setValue("outcon", 1);
								messageSample.setValue("temp", volume);
							} else {
								if (logEvent.timeStamp / 1000 > messageSample.getTime()) {
									if (rrddb.getLastUpdateTime() < messageSample.getTime()) {
										System.err.println(messageSample.dump());
										messageSample.update();									
									}
									messageSample = rrddb.createSample(logEvent.timeStamp / 1000);
									messageSample.setValue("outcon", 1);
									messageSample.setValue("temp", volume);
								} else {
									double previous;
									if (new Double(messageSample.getValues()[4]).isNaN()) previous = 1;
									else previous = messageSample.getValues()[4] + 1;
									messageSample.setValue("outcon", previous);
									if (new Double(messageSample.getValues()[9]).isNaN()) previous = volume;
									else previous = messageSample.getValues()[9] + volume;
									messageSample.setValue("temp", previous);
								}
							}
						}
					} catch (IOException e) {
						log.error(e.getMessage(),e);
						e.printStackTrace();
					}
				}

				Matcher deletedMatcher = StatisticMessage.deletedPattern.matcher(event);
				if (deletedMatcher.find()) {
					volume -= Long.parseLong(deletedMatcher.group(1));
					try {
						if (!rrddb.isClosed()) {
							
							if (messageSample == null) {							
								messageSample = rrddb.createSample(logEvent.timeStamp/1000);
								messageSample.setValue("temp", volume);
							} else {
								if (logEvent.timeStamp / 1000 > messageSample.getTime()) {
									if (rrddb.getLastUpdateTime() < messageSample.getTime()) {
										System.err.println(messageSample.dump());
										messageSample.update();									
									}
									messageSample = rrddb.createSample(logEvent.timeStamp / 1000);
									messageSample.setValue("temp", volume);
								} else {
									double previous;
									if (new Double(messageSample.getValues()[9]).isNaN()) previous = volume;
									else previous = messageSample.getValues()[9] + volume;
									messageSample.setValue("temp", previous);
								}
							}
						}
					} catch (IOException e) {
						log.error(e.getMessage(),e);
						e.printStackTrace();
					}
				}
				
				Matcher attachMatcher = StatisticAttachment.nameSizePattern.matcher(event);
				if (attachMatcher.find()) {
					session.incrementProcessedAttachments();
					volume += Long.parseLong(attachMatcher.group(3));
					try {
						if (!rrddb.isClosed()) {
							
							if (messageSample == null) {							
								messageSample = rrddb.createSample(logEvent.timeStamp/1000);
								messageSample.setValue("temp", volume);
							} else {
								if (logEvent.timeStamp / 1000 > messageSample.getTime()) {
									if (rrddb.getLastUpdateTime() < messageSample.getTime()) {
										System.err.println(messageSample.dump());
										messageSample.update();									
									}
									messageSample = rrddb.createSample(logEvent.timeStamp / 1000);
									messageSample.setValue("temp", volume);
								} else {
									double previous;
									if (new Double(messageSample.getValues()[9]).isNaN()) previous = volume;
									else previous = messageSample.getValues()[9] + volume;
									messageSample.setValue("temp", previous);
								}
							}
						}
					} catch (IOException e) {
						log.error(e.getMessage(),e);
						e.printStackTrace();
					}
					/*StatisticMessage m = null;
					if ((m = session.getMessage(Long.parseLong(attachMatcher.group(1)))) != null)
						m.addAttachment(new StatisticAttachment(attachMatcher.group(2), Long.parseLong(attachMatcher.group(3)),attachMatcher.group(4)));*/
				}

				Matcher savedMatcher = StatisticAttachment.savedPattern.matcher(event);
				if (savedMatcher.find()) {
					/*StatisticMessage m = null;
					if ((m = session.getMessage(Long.parseLong(savedMatcher.group(2)))) != null) {
						m.getAttachment(savedMatcher.group(1)).setSaved(true);
						m.getAttachment(savedMatcher.group(1)).setNbAttempt(Integer.parseInt(savedMatcher.group(4)));
					}*/
					session.incrementAttempt(Integer.parseInt(savedMatcher.group(4)));
				}
			} else {
				close();
				init();
			}
		}
	}
	
	/** Add the appropriate RRA to the RRD database. 
	 * @param rrddef The RRD database definition 
	 * @param elapsed The length of the statistical session */
	public void computeRRA(RrdDef rrddef, long elapsed) {
		int unit = ReportGenerator.SECOND;
		if (elapsed*1000 >= ReportGenerator.YEAR_TIME_LIMIT) unit = ReportGenerator.YEAR;
		else if (elapsed*1000 >= ReportGenerator.MONTH_TIME_LIMIT) unit = ReportGenerator.MONTH;
		else if (elapsed*1000 >= ReportGenerator.WEEK_TIME_LIMIT) unit = ReportGenerator.WEEK;
		else if (elapsed*1000 >= ReportGenerator.DAY_TIME_LIMIT) unit = ReportGenerator.DAY;
		else if (elapsed*1000 >= ReportGenerator.HOUR_TIME_LIMIT) unit = ReportGenerator.HOUR;
		else if (elapsed*1000 >= ReportGenerator.MINUTE_TIME_LIMIT) unit = ReportGenerator.MINUTE;
		
		switch (unit) {
		case ReportGenerator.YEAR:
			// Store every hour
			rrddef.addArchive(ConsolFun.AVERAGE, 0.99, 3600, (int)(elapsed/2)/60);
			break;
		case ReportGenerator.MONTH:
			// Store every 10 minutes
			rrddef.addArchive(ConsolFun.AVERAGE, 0.99, 600, (int)(elapsed/2)/60);
			break;
		case ReportGenerator.WEEK:
			// Store every 5 minute
			rrddef.addArchive(ConsolFun.AVERAGE, 0.99, 300, (int)(elapsed/2)/300);
			break;
		case ReportGenerator.DAY:
			// Store every minute
			rrddef.addArchive(ConsolFun.AVERAGE, 0.99, 60, (int)(elapsed/2)/60);
			break;
		case ReportGenerator.HOUR:
			// Store every 10 seconds
			rrddef.addArchive(ConsolFun.AVERAGE, 0.99, 10, (int)(elapsed/2)/10);
			break;
		case ReportGenerator.SECOND:
		case ReportGenerator.MINUTE:
			// Store every 2 seconds
			rrddef.addArchive(ConsolFun.AVERAGE, 0.99, 1, (int)(elapsed/2));
			break;
		}
	}
	
	/** Init the appender and the RRD database for the current session. 
	 * It is called at the end of each session. The RRD database is defined here.*/
	public void init() {
		volume = 0;
		File temp = new File(LightAttachment.config.getString("directory.temp"));
		for (File n : temp.listFiles()) volume += n.length();
		
		session = new StatisticSession();
		long time = System.currentTimeMillis();
		session.setBeginDate(time);
		session.setEndDate(addLimit(time));
		session.setFreespaceBefore(new File(".").getFreeSpace());
		
		report = new ReportGenerator(session);
		
		try {
			messageSample = null;
			
			log.info("Session "+session.hashCode()+" statistical database saved in stats/stat-"+time+".rrd");
			RrdDef rrddef = new RrdDef("stats/stat-"+time+".rrd");
			rrddef.setStartTime(time/1000);
			rrddef.setStep(2);
			// Messages received
			rrddef.addDatasource("messages", DsType.GAUGE, 40, Double.NaN, Double.NaN);
			// Messages processed
			rrddef.addDatasource("processed", DsType.GAUGE, 40, Double.NaN, Double.NaN);
			// Messages sizes
			rrddef.addDatasource("msize", DsType.GAUGE, 40, Double.NaN, Double.NaN);
			// Messages sizes
			//rrddef.addDatasource("tsize", DsType.GAUGE, 40, Double.NaN, Double.NaN);
			// Incoming connection
			rrddef.addDatasource("incon", DsType.GAUGE, 40, Double.NaN, Double.NaN);
			// Outcoming connection
			rrddef.addDatasource("outcon", DsType.GAUGE, 40, Double.NaN, Double.NaN);
			// Errors
			rrddef.addDatasource("errors", DsType.GAUGE, 40, Double.NaN, Double.NaN);
			// JVM Heap Memory Size
			rrddef.addDatasource("jvmh", DsType.GAUGE, 40, Double.NaN, Double.NaN);
			// JVM Non Heap Memory Size
			rrddef.addDatasource("jvm", DsType.GAUGE, 40, Double.NaN, Double.NaN);
			// JVM Memory Size
			rrddef.addDatasource("jvmt", DsType.GAUGE, 40, Double.NaN, Double.NaN);
			// Temp volume
			rrddef.addDatasource("temp", DsType.GAUGE, 40, Double.NaN, Double.NaN);
			// Receiving time
			rrddef.addDatasource("rtime", DsType.GAUGE, 40, Double.NaN, Double.NaN);
			// Parsing time
			rrddef.addDatasource("ptime", DsType.GAUGE, 40, Double.NaN, Double.NaN);
			// Saving time
			rrddef.addDatasource("stime", DsType.GAUGE, 40, Double.NaN, Double.NaN);
			// Injecting back time
			rrddef.addDatasource("itime", DsType.GAUGE, 40, Double.NaN, Double.NaN);
			// Forwarding back time
			rrddef.addDatasource("ftime", DsType.GAUGE, 40, Double.NaN, Double.NaN);
			// Cleaning time
			rrddef.addDatasource("ctime", DsType.GAUGE, 40, Double.NaN, Double.NaN);
			
			computeRRA(rrddef, session.getEndDate()/1000 - session.getBeginDate()/1000);
			
			System.err.println(rrddef.dump());
			rrddb = new RrdDb(rrddef);

		} catch (IOException e) {
			log.error(e.getMessage(), e);
			e.printStackTrace();
		}
		
		log.info("Session "+session.hashCode()+" started at "+new Date(time)+" will end at "+new Date(session.getEndDate()));
	}
	
	/** Close the appender and generates activity reports. */
	public void close() {		
		long time = System.currentTimeMillis();
		session.setEndDate(time);
		session.setFreespaceAfter(new File(".").getFreeSpace());
		log.info("Session "+session.hashCode()+" ended at "+new Date(time));
		try {
			
			if (messageSample != null && messageSample.getTime() != time/1000) messageSample.update();
			rrddb.close();
						
			if (LightAttachment.config.getString("report.format").equals("html")) report.generateHtmlReport();
			else if (LightAttachment.config.getString("report.format").equals("text")) report.generateTextReport();
			else if (LightAttachment.config.getString("report.format").equals("pdf")) report.generatePdfReport();
			
		} catch (IOException e) {
			e.printStackTrace();
			log.error(e.getMessage(),e);
		} 
	}
	
	/** Add the amount of time specified in <code>report.length<code> to the parameter.
	 * @param time to which <code>report.length<code> must be added 
	 * @return the addition result */
	public long addLimit(long time) {
		String limit = LightAttachment.config.getString("report.length");
		if (limit.matches("\\d+[a-z]")) {
			long add = Integer.parseInt(limit.substring(0,limit.length()-1));
			char unit = limit.charAt(limit.length()-1);
			
			switch (unit) {
			case 'd':
				return time+(add*86400000);
			case 'h': 
				return time+(add*3600000);
			case 'm': 
				return time+(add*60000);
			}
		}
		
		return time+StatisticSession.defaultLength;
	}
	
	public boolean requiresLayout() {
		return false;
	}

	public StatisticSession getSession() {
		return session;
	}

	public void setSession(StatisticSession session) {
		this.session = session;
	}
	
}
