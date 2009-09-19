package com.lightattachment.mails;

import org.apache.commons.configuration.XMLConfiguration;
import org.apache.log4j.Logger;

import com.lightattachment.smtp.streamed.StreamedMailParser;
import com.lightattachment.smtp.streamed.StreamedSMTPPostfixInputConnector;

/** Contains the main method to launch LightAttachment. Usage is: <code>LightAttachment <config-file></code>.
 * @author Benoit Giannangeli
 * @version 0.1a
 * */
public class LightAttachment {
	
	public static XMLConfiguration config;
	
	public static void main(String[] args) {

		try {
			config = new XMLConfiguration(args[0]);
			
			MailManager.initLog4J();
			
			Logger log = Logger.getLogger(LightAttachment.class);
			
			if (config.getLong("message.message-size.min-size") <= 0) log.warn("Minimum message size to process is set to 0, this is dangerous");
			
			MailManager manager = new MailManager();
			manager.start();
			
			StreamedMailParser parser = new StreamedMailParser(manager);
			parser.start();
			
			//StreamedSMTPPostfixInputConnector input = StreamedSMTPPostfixInputConnector.start(parser);
			StreamedSMTPPostfixInputConnector input = new StreamedSMTPPostfixInputConnector(parser);
			input.start();
			
			ShutdownHook sh = new ShutdownHook(manager,parser,input);
			Runtime.getRuntime().addShutdownHook(sh);	
		} catch (Exception e) {
			e.printStackTrace();
		}
		
	}
	
}
