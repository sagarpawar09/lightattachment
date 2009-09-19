package com.lightattachment.mails;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Properties;
import java.util.StringTokenizer;

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

public class MassMailSenderScheduled {

	public static final int REC = 1;
	public static final int LSE = 2;
	public static final int BSE = 3;
	public static final int SE = 4;
	public static final int NT = 5;
	
	// TODO for now, can only send maximum 60 msg minute (1/s)
	public static ArrayList<ArrayList<Integer>> computePlanning(int rec, int lse, int se, float bse, long length) {
		ArrayList<ArrayList<Integer>> planning = new ArrayList<ArrayList<Integer>>();
		for (int i = 0; i < 60; i++) planning.add(new ArrayList<Integer>());
		for (int i = 0; i < 60; i++) planning.get(i).add(NT);
		
		int period, time;
		if (rec != 0) {
			int rand = 10+(int)(Math.random()*rec);
			if (rand > 0) {
			period = 60 / rand; 
			System.err.println("Rec period: 1/" + period + "s");
			time = 0;
			while (time < 60) {
				planning.get(time).add(REC);
				time += period;
			}
			}
		}

		if (lse != 0) {
			int rand = 5+(int)(Math.random()*lse);
			if (rand > 0) {
			period = 60 / rand;
			System.err.println("Lse period: 1/" + period + "s");
			time = 0;
			while (time < 60) {
				planning.get(time).add(LSE);
				time += period;
			}
			}
		}

		if (bse >= 1) {
			period = 60 / Math.round(bse);
			System.err.println("Bse period: 1/" + period + "s");
			time = 0;
			while (time < 60) {
				planning.get(time).add(BSE);
				time += period;
			}
		}

		if (se != 0) {
			int rand = 1+(int)(Math.random()*se);
			if (rand > 0) {
			period = 60 / rand;
			System.err.println("Se period: 1/" + period + "s");
			time = 0;
			while (time < 60) {
				planning.get(time).add(SE);
				time += period;
			}
			}
		}
		
		System.err.print("Planning for 1 minute: ");
		int j = 0;
		for (ArrayList<Integer> ar : planning) {
			for (int i : ar) {
				switch (i) {
				case REC:
					System.err.print(j+"-REC ");
					break;
				case LSE:
					System.err.print(j+"-LSE ");
					break;
				case BSE:
					System.err.print(j+"-BSE ");
					break;
				case SE:
					System.err.print(j+"-SE ");
					break;
				case NT:
					System.err.print(j+"-* ");
					break;
				}
			}
			j++;
			System.err.print(" / ");
		}			
		
		return planning;
	}
	
	/** Usage is: <code>MassMailSender from to file smtp times.
	 * Example: MassMailSender stage1@e-logiq.net root@vmsource.e-logiq.net c:\fichier.rar 192.168.253.57 10</code>*/
	public static void main(String[] args) {
		if (args.length >= 9) {

			String from = args[0];
			String to = args[1];
			String smtp = args[3];
			
			int received = Integer.parseInt(args[4]);
			int littleSent = Integer.parseInt(args[5]);
			int sent = Integer.parseInt(args[6]);
			double bigSent = Double.parseDouble(args[7]);
			long length = Long.parseLong(args[8]);
			
			// Period for big mails (in minutes)
			int bigPeriod = -1;
			if (bigSent < 1) {
				bigPeriod = (int)(1000/(1000*bigSent));
				System.err.println("Big period: 1/"+bigPeriod+"min");
			}
			
			ArrayList<ArrayList<Integer>> planning = computePlanning(received,littleSent,sent,(float)bigSent,length);			
			
			/*try {
				Thread.sleep(3000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}*/
			
			AskThread ask = new AskThread(received,littleSent,sent,(float)bigSent,length);
			ask.start();
			
			long totalWait = 0;
			long minute = 0;
			// 65 hours = 234000000 ms
			while (true && totalWait <= length) {
				// 1 min
				if (bigPeriod > -1 && minute == bigPeriod) {
					System.err.println("Send BSE");
					new SendThread(from, to, args[2]+"bse", smtp).start();
					minute = 0;
				}
				planning = computePlanning(received,littleSent,sent,(float)bigSent,length);
				//planning = ask.getPlanning();
				bigPeriod = ask.getBigPeriod();
				for (ArrayList<Integer> ar : planning) {
					for (Integer time : ar) {
						switch (time) {
						case REC:
							//System.err.println("Send REC");
							new SendThread("someone@notofthatdomain.com", to, args[2]+"rec", smtp).start();
							break;
						case LSE:
							//System.err.println("Send LSE");
							new SendThread(from, to, args[2]+"lse", smtp).start();
							break;
						case BSE:
							//System.err.println("Send BSE");
							new SendThread(from, to, args[2]+"bse", smtp).start();
							break;
						case SE:
							//System.err.println("Send SE");
							new SendThread(from, to, args[2]+"se", smtp).start();
							break;
						case NT:
							//System.err.println("Nothing");
							break;
						}
					}
					try {
						Thread.sleep(1000);
						totalWait += 1000;
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
				minute++;
				/*for (String filename : folder.list()) {
					try {
						long wait = Math.round(Math.random()*180000);
						totalWait += wait;
						
						if (wait % 5 == 0 && Math.random()*100 == 50) {
							System.err.println("** "+filename+" sent to nobody...");
							for (int i = 0; i < times; i++) {
								new SendThread(from, "giann008@free.fr", args[2]+filename, smtp).start();
								Thread.sleep(Math.round(Math.random()*10000));
							}
						} else {
							System.err.println("** "+filename+" sent to "+to+"...");
							for (int i = 0; i < times; i++) {
								new SendThread(from, to, args[2]+filename, smtp).start();
								Thread.sleep(Math.round(Math.random()*10000));
							}
						}
						System.err.println("** Sleep "+wait);
						Thread.sleep(wait);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}*/
			}
		} 
	}
	
	private static class AskThread extends Thread {
		
		private int rec, lse, se, bigPeriod;
		private float bse;
		private long length;
		
		private ArrayList<ArrayList<Integer>> planning;
		
		private BufferedReader r;
		
		public AskThread(int rec, int lse, int se, float bse, long length) {
			super();
			this.rec = rec;
			this.lse = lse;
			this.se = se;
			this.bse = bse;
			this.length = length;
			this.bigPeriod = -1;
			if (bse < 1) {
				bigPeriod = (int)(1000/(1000*bse));
				System.err.println("Big period: 1/"+bigPeriod+"min");
			}
			planning = computePlanning(rec, lse, se, bse, length);
			r = new BufferedReader(new InputStreamReader(System.in));
		}
		
		public void run() {
			while (true) {
				String resp = null;
				while (resp == null) {
					System.out.println("rec,lse,se,bse: ");
					try {					
						resp = r.readLine();						
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
				StringTokenizer tk = new StringTokenizer(resp,",");
				rec = Integer.parseInt(tk.nextToken());
				lse = Integer.parseInt(tk.nextToken());
				se = Integer.parseInt(tk.nextToken());
				bse = Float.parseFloat(tk.nextToken());
				
				planning = computePlanning(rec, lse, se, bse, length);
				this.bigPeriod = -1;
				if (bse < 1) {
					bigPeriod = (int)(1000/(1000*bse));
					System.err.println("Big period: 1/"+bigPeriod+"min");
				}
			}
		}

		public ArrayList<ArrayList<Integer>> getPlanning() {
			return planning;
		}

		public int getBigPeriod() {
			return bigPeriod;
		}
		
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
				System.err.println("Fail sending: "+e.getMessage());
			}
		}
		
		public void send(Session session, String file) throws AddressException, MessagingException {
			// Définie le message
			Message message = new MimeMessage(session);
			//System.out.println("Building message " + message.hashCode() + "("+file+")");
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
			//System.out.println(" Message " + message.hashCode() + " sent");
			//System.out.print("*");
		}

	}

}
