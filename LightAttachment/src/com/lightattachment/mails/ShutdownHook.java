package com.lightattachment.mails;

import java.io.File;
import java.io.IOException;

import javax.mail.MessagingException;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.lightattachment.smtp.streamed.StreamedMailParser;
import com.lightattachment.smtp.streamed.StreamedSMTPPostfixInputConnector;
import com.lightattachment.stats.StoppableThread;

/** Shutdown hook called before the JVM terminates.
 * It safely shutdowns all LightAttachment's components.
 * 
 * @author Benoit Giannangeli
 * @version 0.1a
 * 
 */

public class ShutdownHook extends Thread {

	/** The current <code>MailManager</code>. */
	private MailManager manager;
	
	/** The current <code>StreamedMailParser</code>. */
	private StreamedMailParser parser;
	
	/** The current <code>StreamedSMTPPostfixInputConnector</code>. */
	private StreamedSMTPPostfixInputConnector input;
	
	/** Logger used to trace activity. */
	static Logger log = Logger.getLogger(ShutdownHook.class);

	/** Build a <code>ShutdownHook</code>.
	 * @param manager the <code>MailManager</code> to stop.
	 * @param parser the <code>StreamedMailParser</code> to stop.
	 * @param input the <code>StreamedSMTPPostfixInputConnector</code> to stop. */
	public ShutdownHook(MailManager manager, StreamedMailParser parser, StreamedSMTPPostfixInputConnector input) {
		super();
		this.manager = manager;
		this.parser = parser;
		this.input = input;
	}

	@Override
	public void run() {
		super.run();	
		try {
			System.out.println("LightAttachment is shutting down...");
			File delete = new File(LightAttachment.config.getString("directory.temp"));
			for (String s : delete.list()) {
				File f = new File(LightAttachment.config.getString("directory.temp")+s);
				if (f.delete()) System.err.println(s+" is deleted on shutdown");
				else System.err.println(s+" failed to be deleted on shutdown");
			}
			
			manager.shutdown();
			parser.shutdown();
			input.shutdown();
			sleep(100);
			LogManager.shutdown();
			
			// Wait for any other running thread to end
			ThreadGroup root = Thread.currentThread().getThreadGroup().getParent();
		    while (root.getParent() != null) {
		        root = root.getParent();
		    }
		    
		    // Visit each thread group
		    visit(root, 0);    

		} catch (IOException e) {
			log.error(e.getMessage(),e);
			e.printStackTrace();
		} catch (InterruptedException e) {
			log.error(e.getMessage(),e);
			e.printStackTrace();
		} catch (MessagingException e) {
			log.error(e.getMessage(),e);
			e.printStackTrace();
		}
	}
	
	// This method recursively visits all thread groups under `group'.
    public static void visit(ThreadGroup group, int level) throws InterruptedException {
        // Get threads in `group'
        int numThreads = group.activeCount();
        Thread[] threads = new Thread[numThreads*2];
        numThreads = group.enumerate(threads, false);
    
        // Enumerate each thread in `group'
        for (int i=0; i<numThreads; i++) {
            // Get thread
            Thread thread = threads[i];
            if (thread instanceof StoppableThread) while (!((StoppableThread)thread).isDone()) sleep(100);
            //else while (thread.isAlive()) sleep(100);
        }
    
        // Get thread subgroups of `group'
        int numGroups = group.activeGroupCount();
        ThreadGroup[] groups = new ThreadGroup[numGroups*2];
        numGroups = group.enumerate(groups, false);
    
        // Recursively visit each subgroup
        for (int i = 0; i < numGroups; i++) {
            visit(groups[i], level+1);
        }
    }

}
