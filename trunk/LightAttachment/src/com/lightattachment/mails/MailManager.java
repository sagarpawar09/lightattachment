package com.lightattachment.mails;

import java.io.File;
import java.io.IOException;
import java.net.SocketException;
import java.util.ArrayList;

import javax.mail.MessagingException;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

import com.lightattachment.smtp.SMTPPostfixOutputConnector;
import com.lightattachment.stats.SendErrorReportThread;
import com.lightattachment.stats.StoppableThread;

/**
 * Distribute the messages to the right LightAttachment's component.
 * It receives messages from the <code>StreamedMailParser</code> instance via the <code>push</code> methods, 
 * send them to the <code>AttachmentSaver</code> instance, and launch a <code>SMTPPostfixOutputConnector</code>
 * instance for each message to send back.
 * 
 * @author Benoit Giannangeli
 * @version 0.1a
 * 
 */

public class MailManager extends Thread {

	/** The messages queue filled by a <code>StreamedMailParser</code> instance. */
	private ArrayList<MailSet> toSaveQueue;	

	/** Processed messages to send back to Postfix. */
	private ArrayList<MailSet> toInjectBackQueue;
	
	/** Not processed messages to send back to Postfix. */
	private ArrayList<MailSet> toInjectBackFromFileQueue;
	
	/** Messages to remove from the disk. */
	private ArrayList<MailSet> toCleanQueue;
	
	/** Messages hold in the <code>toSaveQueue</code> are pushed to this instance of <code>AttachmentSaver</code>. */
	private AttachmentSaver attachmentSaver;
	
	/** <code>SMTPPostfixOutputConnector</code> pool. */
	private ArrayList<SMTPPostfixOutputConnector> outputPool;
	
	/** <code>InjectBackThread</code> pool. */
	private ArrayList<InjectBackThread> injectPool;
	
	/** Count <code>InjectBackThread</code> running instance. */
	public static int injectRunning = 0;
	
	/** Set to <code>false</code> to shutdown the <code>MailManager</code>. */
	private boolean working;
	
	/** Logger used to trace activity. */
	static Logger log = Logger.getLogger(MailManager.class);

	/** Build a <code>MailManager</code> from scratch.
	 * Onced initialized, it launch an instance of <code>AttachmentSaver</code>. */
	public MailManager() {
		super();
		this.toSaveQueue = new ArrayList<MailSet>();
		this.toInjectBackQueue = new ArrayList<MailSet>();
		this.toInjectBackFromFileQueue = new ArrayList<MailSet>();
		this.toCleanQueue = new ArrayList<MailSet>();
		this.outputPool = new ArrayList<SMTPPostfixOutputConnector>();
		this.injectPool = new ArrayList<InjectBackThread>();
		this.attachmentSaver = new AttachmentSaver(this);
		this.attachmentSaver.start();
		this.working = true;
	}
	
	/** Safely shutdown the instance. */
	public synchronized void shutdown() throws IOException, InterruptedException, MessagingException {
		for (SMTPPostfixOutputConnector o : outputPool) o.shutdown();
		for (InjectBackThread i : injectPool) i.shutdown();
		working = false;
		attachmentSaver.shutdown();
		log.info("MailManager stopped");		
	}
	
	/** Push a new <code>MailSet</code> to save. 
	 * @param mail the <code>MailSet</code> to save */
	public synchronized void push(MailSet mail) {
		if (mail != null) {
			toSaveQueue.add(mail);
		}
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
	 * If a <code>StreamedMailParser.STOP</code> is popped, the manager is shutdowned.
	 * @return the first value of the save queue */
	private synchronized MailSet pop() throws IOException, InterruptedException, MessagingException { 
		if (toSaveQueue.size() > 0) {			
			MailSet ms = toSaveQueue.get(0);
			toSaveQueue.remove(0);
			
			if (ms != null) return ms;
			else return null;
		} else return null;
	}
	
	/** Push a <code>MailSet</code> to the inject back queue. 
	 * @param set the <code>MailSet</code> to inject back to Postfix. */
	public void pushToInject(MailSet set) throws MessagingException {	
		if (set != null) {			
			toInjectBackQueue.add(set);			
		}
	}
	
	/** Push a <code>MailSet</code> to the inject back queue. 
	 * The messages pushed to the queue with this method will be forwarded as received.
	 * @param set the <code>MailSet</code> to inject back to Postfix. */
	public void pushToInjectFromFile(MailSet set) throws MessagingException {		
		if (set != null) {			
			toInjectBackFromFileQueue.add(set);			
		}
	}
	
	public void pushToClean(MailSet set) throws MessagingException {		
		if (set != null) {			
			toCleanQueue.add(set);			
		}
	}
	
	/** Return the first value of the inject back queue without removing it.
	 * @return the first value of the inject back queue */
	@SuppressWarnings("unused")
	private synchronized MailSet peekToInject() {		
		if (toInjectBackQueue.size() > 0)
			return toInjectBackQueue.get(0);
		else
			return null;
	}
	
	/** Return the first value of the inject back queue and remove it.
	 * @return the first value of the inject back queue. */
	private synchronized MailSet popToInject() throws MessagingException { 
		if (toInjectBackQueue.size() > 0) {			
			MailSet ms = toInjectBackQueue.get(0);
			toInjectBackQueue.remove(0);
			return ms;
		} else {
			return null;
		}
	}	
	
	/** Return the first value of the inject back from file queue and remove it.
	 * @return the first value of the inject back from file queue. */
	private synchronized MailSet popToInjectFromFile() throws MessagingException { 
		if (toInjectBackFromFileQueue.size() > 0) {			
			MailSet ms = toInjectBackFromFileQueue.get(0);
			toInjectBackFromFileQueue.remove(0);
			return ms;
		} else {
			return null;
		}
	}
	
	private synchronized MailSet popToClean() throws MessagingException { 
		if (toCleanQueue.size() > 0) {
			MailSet ms = toCleanQueue.get(0);
			toCleanQueue.remove(0);
			return ms;
		} else {
			return null;
		}
	}
	
	/** Use an InjectBackThread which will inject back the specified <code>MailSet</code>.
	 * @param set the <code>MailSet</code> to inject back. */
	private synchronized void injectBack(MailSet set) throws IOException, MessagingException {		
		if (set != null) {			
			//InjectBackThread ibt = new InjectBackThread(false,false,set);
			//ibt.start();
			InjectBackThread ibt = selectInject();
			set.setFromFile(false);
			set.setClean(false);
			ibt.push(set);
		}
	}
	
	/** Use an InjectBackThread which will inject back the specified <code>MailSet</code> as received.
	 * @param set the <code>MailSet</code> to inject back. */
	private synchronized void injectBackFromFile(MailSet set) throws IOException, MessagingException {		
		if (set != null) {							
			//InjectBackThread ibt = new InjectBackThread(false,true,set);
			//ibt.start();
			InjectBackThread ibt = selectInject();
			set.setFromFile(true);
			set.setClean(false);
			ibt.push(set);
		}
	}
	
	/** Use an InjectBackThread which will clean the specified <code>MailSet</code> from the disk.
	 * @param set the <code>MailSet</code> to clean. */
	private synchronized void injectBackToClean(MailSet set) throws IOException, MessagingException {		
		if (set != null) {							
			//InjectBackThread ibt = new InjectBackThread(true,false,set);
			//ibt.start();
			InjectBackThread ibt = selectInject();
			set.setClean(true);
			set.setFromFile(false);
			ibt.push(set);
		}
	}
	
	/** Select a <code>SMTPPostfixOutputConnector</code> in <code>outputPool</code>.
	 * If the pool is empty, creates a new thread and return it.
	 * Else if one thread is free, return it.
	 * Else if limit not reached, creates a new thread and return it.
	 * Else select the thread with the smaller queue and return it.
	 * @return the selected <code>SMTPPostfixOutputConnector</code> */
	public synchronized SMTPPostfixOutputConnector selectOutput() throws SocketException, IOException {
		SMTPPostfixOutputConnector output = null;
		
		if (outputPool.size() == 0) {
			output = new SMTPPostfixOutputConnector(LightAttachment.config.getString("postfix.out-address[@host]"),
													LightAttachment.config.getInt("postfix.out-address[@port]"));
			output.start();
			outputPool.add(output);
		} else {
			for (SMTPPostfixOutputConnector o : outputPool) {
				if (o.size() == 0) return o;
				else if (output == null) output = o;
				else if (output.size() > o.size()) output = o;
			}
			
			if (output.size() > 0 && outputPool.size() < LightAttachment.config.getInt("message.output-limit")) {
				output = new SMTPPostfixOutputConnector(LightAttachment.config.getString("postfix.out-address[@host]"),
						LightAttachment.config.getInt("postfix.out-address[@port]"));
				output.start();
				outputPool.add(output);
			}
		}
		
		return output;
	}
	
	/** Select a <code>InjectBackThread</code> in <code>injectPool</code>.
	 * If the pool is empty, creates a new thread and return it.
	 * Else if one the thread is free, return it.
	 * Else if limit not reached, creates a new thread and return it.
	 * Else select the thread with the smaller queue and return it.
	 * @return the selected <code>InjectBackThread</code> */
	public synchronized InjectBackThread selectInject() {
		if (injectPool.size() == 0) {
			InjectBackThread ibt = new InjectBackThread();
			ibt.start();
			injectPool.add(ibt);
			return ibt;
		} else {
			InjectBackThread ibt = null;
			for (InjectBackThread i : injectPool) {
				if (i.size() == 0) ibt = i;
				else if (ibt == null) ibt = i;
				else if (i.size() < ibt.size()) ibt = i;
			}
			
			if (ibt.size() > 0 && injectPool.size() < LightAttachment.config.getInt("message.output-limit")) {
				ibt = new InjectBackThread();
				ibt.start();
				injectPool.add(ibt);
			}
			
			return ibt;
		}
	}
	
	/** Initialize the log system. */
	public static void initLog4J() {
		PropertyConfigurator.configure("config/log4j.lcf");
	}

	@Override
	public void run() {
		super.run();
		while (working) {
			
			try {				
				
				injectBackFromFile(this.popToInjectFromFile());
				injectBack(this.popToInject());
				injectBackToClean(this.popToClean());
				attachmentSaver.push(this.pop());
				
				sleep(100);
				
			} catch (IOException e) {
				log.error(e.getMessage(),e);
				e.printStackTrace();
				SendErrorReportThread sert = new SendErrorReportThread(null,
						"MailManager failed.",e);
				sert.start();
			} catch (MessagingException e) {
				log.error(e.getMessage(),e);
				e.printStackTrace();
				SendErrorReportThread sert = new SendErrorReportThread(null,
						"MailManager failed.",e);
				sert.start();
			} catch (InterruptedException e) {
				log.error(e.getMessage(),e);
				e.printStackTrace();
				SendErrorReportThread sert = new SendErrorReportThread(null,
						"MailManager failed.",e);
				sert.start();
			}
			
		}
	}		
	
	/** Used to inject back <code>MailSet</code>. */
	private class InjectBackThread extends StoppableThread {
		
		/** The <code>MailSet</code>s to inject back. */
		private ArrayList<MailSet> queue;
		
		/** Build a <code>InjectBackThread</code>. */
		public InjectBackThread() {
			super();
			this.queue = new ArrayList<MailSet>();
			injectRunning++;
			System.err.println("InjectBackThread++ count: "+injectRunning);
		}

		/** Shutdown the instance. */
		public void shutdown() {
			setDone(true);
			injectRunning--;
			System.err.println("InjectBackThread-- count: "+injectRunning);
		}
		
		/** Return the queue size.
		 * @return the queue size */
		public int size() {
			return queue.size();
		}
		
		/** Add a <code>MailSet</code> to the queue.
		 * @param set the <code>MailSet</code> to add */
		public void push(MailSet set) {
			if (set != null) queue.add(set);
		}
		
		/** Return the first <code>MailSet</code> of the queue without removing it.
		 * @return the first <code>MailSet</code> of the queue */
		public MailSet peek() {
			if (queue.size() > 0) return queue.get(0);
			else return null;
		}
		
		@Override
		public void run() {
			super.run();

			while (!isDone()) {
				MailSet set = peek();
				if (set != null) {
					try {

						if (set.isClean()) injectBackToClean(set);
						else if (set.isFromFile()) injectBackFromFile(set);
						else injectBack(set);

						queue.remove(set);
					} catch (IOException e) {
						log.error(e.getMessage(), e);
						e.printStackTrace();
						SendErrorReportThread sert = new SendErrorReportThread(set, "Error while injecting back to Postfix.", e);
						sert.start();
					} catch (MessagingException e) {
						log.error(e.getMessage(), e);
						e.printStackTrace();
						SendErrorReportThread sert = new SendErrorReportThread(set, "Error while injecting back to Postfix.", e);
						sert.start();
					} catch (InterruptedException e) {
						log.error(e.getMessage(), e);
						e.printStackTrace();
						SendErrorReportThread sert = new SendErrorReportThread(set, "Error while injecting back to Postfix.", e);
						sert.start();
					}
				} else {
					try {
						sleep(100);
					} catch (InterruptedException e) {
						log.error(e.getMessage(), e);
						e.printStackTrace();
						SendErrorReportThread sert = new SendErrorReportThread(set, "Error while injecting back to Postfix.", e);
						sert.start();
					}
				}
			}
		}
		
		/** Inject back a <code>MailSet</code> by using a <code>SMTPPostfixOutputConnector</code> and pushing to it the <code>MailSet</code>.
		 * @param set the <code>MailSet</code> to inject back. 
		 * @throws InterruptedException */
		private synchronized void injectBack(MailSet set) throws IOException, MessagingException, InterruptedException {					
			if (set != null) {
				long begin = System.currentTimeMillis();
				long min = new File(set.getOriginalMessages().get(0)).length();
				for (String f : set.getOriginalMessages()) {
					File fl = new File(f);
					if (fl.length() < min) min = fl.length();
				}
				
				if (set.getMessage().getSize() >= min*0.8) {
					log.warn("Modified message "+set.hashCode()+" has a suspicious size of "+set.getMessage().getSize()+" bytes."); 				
					log.warn("This could leads to performance issues, please restart LightAttachment as soon as possible.");
				}
				
				SMTPPostfixOutputConnector spoc = selectOutput();
				set.setFromFile(false);
				spoc.push(set);

				while (!set.isSent()) sleep(100);
				//setDone(true);
				long end = System.currentTimeMillis();
				log.info("Mail "+set.hashCode()+" injected back to Postfix in "+(end-begin)+" ms");
			}
		}
		
		/** Inject back a <code>MailSet</code> by using a <code>SMTPPostfixOutputConnector</code> and pushing 
		 * to it the <code>MailSet</code>.
		 * @param set the <code>MailSet</code> to inject back. 
		 * @throws InterruptedException */
		private synchronized void injectBackFromFile(MailSet set) throws IOException, MessagingException, InterruptedException {					
			if (set != null) {				
				long begin = System.currentTimeMillis();	
				
				SMTPPostfixOutputConnector spoc = selectOutput();
				set.setFromFile(true);
				spoc.push(set);
				
				while (!set.isSent()) sleep(100);
				//setDone(true);
				long end = System.currentTimeMillis();
				log.info("Mail "+set.hashCode()+" injected back to Postfix unchanged in "+(end-begin)+" ms");
			}
		}
		
		/** Clean a <code>MailSet</code> from the disk.
		 * @param set the <code>MailSet</code> to clean.
		 * @throws InterruptedException */
		private synchronized void injectBackToClean(MailSet set) throws InterruptedException {			
			if (set != null) {
				long begin = System.currentTimeMillis();
				while (!set.isSent()) sleep(100);
				for (String key : set.getParts().keySet()) {
					long size = new File(set.getParts().get(key)).length();
					long size2 = new File(key).length();
					if (new File(set.getParts().get(key)).delete()) log.info("Temporary file "+set.getParts().get(key)+" of "+size+" bytes deleted");
					if (new File(key).delete()) log.info("Temporary file "+key+" of "+size2+" bytes deleted");
				}
				for (String orig : set.getOriginalMessages()) {
					long size = new File(orig).length();
					if (new File(orig).delete()) log.info("Temporary file "+orig+" of "+size+" bytes deleted");
				}
				set.setSent(true);
				
				//setDone(true);
				long end = System.currentTimeMillis();
				log.info("Mail "+set.hashCode()+" deleted from disk in "+(end-begin)+" ms");
			}
		}
	}

}
