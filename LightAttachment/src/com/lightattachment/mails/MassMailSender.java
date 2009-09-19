package com.lightattachment.mails;

import java.io.File;
import java.util.Properties;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.FileDataSource;
import javax.mail.BodyPart;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

/** Tool to send a bunch of mails at the same time.
 * 
 * @author Benoit Giannangeli
 * @version 0.1a
 * 
 */

public class MassMailSender {

	/** Usage is: <code>MassMailSender from to file smtp times.
	 * Example: MassMailSender stage1@e-logiq.net root@vmsource.e-logiq.net c:\fichier.rar 192.168.253.57 10</code>*/
	public static void main(String[] args) {
		if (args.length >= 4) {

			String from = args[0];
			String to = args[1];
			String filename = args[2];
			String smtp = args[3];
			int times = Integer.parseInt(args[4]);

			for (int i = 0; i < times; i++) {
				SendThread st = new SendThread(from, to, filename, smtp);
				st.start();
			}

		} else
			System.out.println("Usage:\n  send <from> <to> <file> <smtp> <times>");
	}

	/** A <code>SendReportThread</code> is launched for each mail to send. */
	private static class SendThread extends Thread {

		private String from, to, filename, smtp;

		public SendThread(String from, String to, String filename, String smtp) {
			super();
			this.from = from;
			this.to = to;
			this.filename = filename;
			this.smtp = smtp;
		}

		public void run() {
			super.run();
			// Récupére les propriétés du systéme
			Properties props = System.getProperties();

			// Spécification du serveur mail
			props.put("mail.smtp.host", smtp);

			// Récupère la session
			Session session = Session.getDefaultInstance(props, null);
		
			try {
				File f = new File(filename);
				if (f.isDirectory()) {
					for (String s : f.list()) {
						System.out.println(filename+s);
						File ff = new File(filename+s);
						if (!ff.isDirectory()) send(session, filename+s);
					}
				} else send(session, filename);
			} catch (AddressException e) {
				e.printStackTrace();
			} catch (MessagingException e) {
				e.printStackTrace();
			}
		}
		
		public void send(Session session, String file) throws AddressException, MessagingException {
			// Définie le message
			Message message = new MimeMessage(session);
			System.out.println("Building message " + message.hashCode());
			message.setFrom(new InternetAddress(from));
			message.addRecipient(Message.RecipientType.TO, new InternetAddress(to));
			message.setSubject("MassMailSender");

			// Première partie du message
			BodyPart messageBodyPart = new MimeBodyPart();

			// Contenu du message
			messageBodyPart.setText("Dumb body");

			// Ajout de la première partie du message dans un objet Multipart
			Multipart multipart = new MimeMultipart();
			multipart.addBodyPart(messageBodyPart);

			// Partie de la pièce jointe
			messageBodyPart = new MimeBodyPart();
			DataSource source = new FileDataSource(file);
			messageBodyPart.setDataHandler(new DataHandler(source));
			messageBodyPart.setFileName(file);
			// Ajout de la partie pièce jointe
			multipart.addBodyPart(messageBodyPart);

			message.setContent(multipart);

			// Envoie du message
			Transport.send(message);
			System.out.println(" Message " + message.hashCode() + " sent");
		}

	}

}
