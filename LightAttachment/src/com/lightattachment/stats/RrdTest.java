package com.lightattachment.stats;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.NetworkInterface;
import java.util.Enumeration;

import org.rrd4j.ConsolFun;
import org.rrd4j.graph.RrdGraph;
import org.rrd4j.graph.RrdGraphConstants;
import org.rrd4j.graph.RrdGraphDef;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Font;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;

public class RrdTest {

	public static void main(String[] args) {
		try {
			
			/*long time = System.currentTimeMillis()/1000;
			
			RrdDef rrddef = new RrdDef("test.rrd");
			rrddef.setStartTime(time);
			rrddef.setStep(10);
			rrddef.addDatasource("messages", DsType.ABSOLUTE, 5, Double.NaN, Double.NaN);
			rrddef.addArchive(ConsolFun.AVERAGE, 0.5, 1, 12);
			//rrddef.addArchive(ConsolFun.LAST, 0.5, 1, 120);
			RrdDb rrddb = new RrdDb(rrddef);
			
			Sample sample = rrddb.createSample();
			for (int i = 1; i < 120; i++) {
				if (Math.random()*5 > 3) {
					//System.err.println((time+i)+":1");
					//sample.setAndUpdate(StatisticAppender.milliToSeconds(time + i) + ":1");
					sample.setAndUpdate((time + i) + ":1");
				} else {
					//System.err.println((time+i)+":NaN");
				}
				//sample.setAndUpdate((time + i) + ":1");
			}
			
			FetchRequest fetchRequest = rrddb.createFetchRequest(ConsolFun.AVERAGE, time, time+120);
			FetchData fetchData = fetchRequest.fetchData();
			System.out.println(fetchData.dump());
			
			rrddb.close();
			
			RrdGraphDef graphDef = new RrdGraphDef();
			graphDef.setTimeSpan(time,time+120);
			graphDef.datasource("msg", "test.rrd", "messages", ConsolFun.AVERAGE);
			graphDef.datasource("mmsg","10,msg,*");
			graphDef.setVerticalLabel("messages/s");
			graphDef.area("mmsg", new Color(0xFF, 0, 0), "Messages Received");
			graphDef.setFilename("test.png");
			graphDef.setTitle("Test");
			graphDef.setAntiAliasing(true);
			graphDef.setImageFormat("png");
			graphDef.setUnit("msg");
			
			RrdGraph graph = new RrdGraph(graphDef);
			BufferedImage bi = new BufferedImage(graph.getRrdGraphInfo().getWidth(), graph.getRrdGraphInfo().getHeight(),
					BufferedImage.TYPE_INT_RGB);
			Graphics2D g2d = (Graphics2D) bi.getGraphics();
			g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			graph.render(g2d);*/
			
			RrdGraphDef graphDef = new RrdGraphDef();
			
			graphDef.datasource("in", "stats/stat-"+1218648053033L+".rrd", "incon", ConsolFun.AVERAGE);
			graphDef.datasource("out", "stats/stat-"+1218648053033L+".rrd", "outcon", ConsolFun.AVERAGE);
			graphDef.datasource("err", "stats/stat-"+1218648053033L+".rrd", "errors", ConsolFun.AVERAGE);
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
			graphDef.setTitle("Session #");
			graphDef.setShowSignature(false);
			graphDef.setFilename("stats/session.png");
			graphDef.setAntiAliasing(true);
			graphDef.setImageFormat("PNG");
			graphDef.setWidth(800);
			graphDef.setHeight(250);
			
			RrdGraph graph = new RrdGraph(graphDef);
			
			BufferedImage bi = new BufferedImage(graph.getRrdGraphInfo().getWidth(),graph.getRrdGraphInfo().getHeight(),BufferedImage.TYPE_INT_RGB);
			Graphics2D g2d = (Graphics2D)bi.getGraphics();
			g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			graph.render(g2d);
			
			graphDef = new RrdGraphDef();
			
			graphDef.datasource("msg", "stats/stat-"+1218648053033L+".rrd", "messages", ConsolFun.AVERAGE);
			graphDef.datasource("prc", "stats/stat-"+1218648053033L+".rrd", "processed", ConsolFun.AVERAGE);
			graphDef.datasource("mmsg","1,msg,*");
			graphDef.datasource("mprc","1,prc,*");
			graphDef.datasource("amsg", "mmsg,UN,0,mmsg,IF");
			graphDef.datasource("aprc", "mprc,UN,0,mprc,IF");
			graphDef.setVerticalLabel("messages");
			graphDef.area("amsg", new Color(62, 155, 210), "Messages Received"/*,2.0F*/);
			graphDef.line("aprc", new Color(255, 215, 0), "Messages Processed",1.8F);

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
			graphDef.setFilename("stats/message.png");
			graphDef.setAntiAliasing(true);
			graphDef.setWidth(800);
			graphDef.setHeight(250);
			graphDef.setImageFormat("PNG");
			
			graph = new RrdGraph(graphDef);
			
			bi = new BufferedImage(graph.getRrdGraphInfo().getWidth(),graph.getRrdGraphInfo().getHeight(),BufferedImage.TYPE_INT_RGB);
			g2d = (Graphics2D)bi.getGraphics();
			g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			graph.render(g2d);
			
			graphDef = new RrdGraphDef();
			
			graphDef.datasource("sz", "stats/stat-"+1218648053033L+".rrd", "msize", ConsolFun.AVERAGE);
			graphDef.datasource("vol", "stats/stat-"+1218648053033L+".rrd", "temp", ConsolFun.AVERAGE);
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
			graphDef.comment("Free space on disk: "+new File(".").getFreeSpace()+" bytes");
			graphDef.setFilename("stats/messages-size.png");

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
			graphDef.setImageFormat("PNG");
			graph = new RrdGraph(graphDef);
			
			bi = new BufferedImage(graph.getRrdGraphInfo().getWidth(),graph.getRrdGraphInfo().getHeight(),BufferedImage.TYPE_INT_RGB);
			g2d = (Graphics2D)bi.getGraphics();
			g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			graph.render(g2d);
			
			graphDef = new RrdGraphDef();
			
			graphDef.datasource("j", "stats/stat-"+1218648053033L+".rrd", "jvm", ConsolFun.AVERAGE);
			graphDef.datasource("jh", "stats/stat-"+1218648053033L+".rrd", "jvmh", ConsolFun.AVERAGE);
			graphDef.datasource("jt", "stats/stat-"+1218648053033L+".rrd", "jvmt", ConsolFun.AVERAGE);
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
			graphDef.setFilename("stats/memory.png");

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
			graphDef.setImageFormat("PNG");
			graphDef.setHeight(250);
			graph = new RrdGraph(graphDef);
			
			bi = new BufferedImage(graph.getRrdGraphInfo().getWidth(),graph.getRrdGraphInfo().getHeight(),BufferedImage.TYPE_INT_RGB);
			g2d = (Graphics2D)bi.getGraphics();
			g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			graph.render(g2d);
			
			graphDef = new RrdGraphDef();
			
			graphDef.datasource("rt", "stats/stat-"+1218648053033L+".rrd", "rtime", ConsolFun.AVERAGE);
			graphDef.datasource("pt", "stats/stat-"+1218648053033L+".rrd", "ptime", ConsolFun.AVERAGE);
			graphDef.datasource("st", "stats/stat-"+1218648053033L+".rrd", "stime", ConsolFun.AVERAGE);
			graphDef.datasource("it", "stats/stat-"+1218648053033L+".rrd", "itime", ConsolFun.AVERAGE);
			graphDef.datasource("ft", "stats/stat-"+1218648053033L+".rrd", "ftime", ConsolFun.AVERAGE);
			graphDef.datasource("ct", "stats/stat-"+1218648053033L+".rrd", "ctime", ConsolFun.AVERAGE);
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
			graphDef.area("amrt", new Color(1, 1, 230), "Receiving time");
			graphDef.stack("ampt", new Color(1, 108, 254), "Parsing time");
			graphDef.stack("amst", new Color(150, 150, 254), "Saving time");
			graphDef.stack("amit", new Color(254, 177, 107), "Injecting back time");
			graphDef.stack("amft", new Color(254, 200, 159), "Forwarding time");
			graphDef.stack("amct", new Color(254, 230, 210), "Cleaning time");
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
			graphDef.setFilename("stats/time.png");

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
			graphDef.setImageFormat("PNG");
			graph = new RrdGraph(graphDef);
			
			bi = new BufferedImage(graph.getRrdGraphInfo().getWidth(),graph.getRrdGraphInfo().getHeight(),BufferedImage.TYPE_INT_RGB);
			g2d = (Graphics2D)bi.getGraphics();
			g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			graph.render(g2d);
			
			//toPDF();
			
		} catch (IOException e) {
			e.printStackTrace();
		}/* catch (DocumentException e) {
			e.printStackTrace();
		}*/
	}
	
	public static void toPDF() throws DocumentException, MalformedURLException, IOException {
		Document document = new Document();
		PdfWriter.getInstance(document, 
			new FileOutputStream("test.pdf"));
		document.open();
		
		Paragraph p = new Paragraph();
        p.add(new Chunk("LightAttachment Report\n", new Font(Font.HELVETICA, 22, Font.BOLD)));
        Paragraph p2 = new Paragraph();
        p2.add(new Chunk("Session ", new Font(Font.HELVETICA, 18)));
        p2.add(new Chunk("#454654", new Font(Font.COURIER, 18)));
        p2.add(new Chunk(" on ", new Font(Font.HELVETICA, 18)));
        
        String host = "";
        Enumeration<NetworkInterface> net = NetworkInterface.getNetworkInterfaces();
        while (net.hasMoreElements()) {
        	NetworkInterface ni = net.nextElement();
        	if (ni.getDisplayName().equals("localhost")) {
        		Enumeration<InetAddress> addr = ni.getInetAddresses(); 
        		host = addr.nextElement().getHostAddress();
        	}
        }      
        
        p2.add(new Chunk(host, new Font(Font.COURIER, 18)));
        p.setAlignment(Paragraph.ALIGN_CENTER);
        p2.setAlignment(Paragraph.ALIGN_CENTER);

		com.lowagie.text.Image image = 
			com.lowagie.text.Image.getInstance("session.png");
		image.scalePercent(60);
		com.lowagie.text.Image image2 = 
			com.lowagie.text.Image.getInstance("message.png");
		image2.scalePercent(60);
		com.lowagie.text.Image image3 = 
			com.lowagie.text.Image.getInstance("messages-size.png");
		image3.scalePercent(60);
		
		document.add(p);
		document.add(p2);
		document.add(image);
		document.add(image2);
		document.add(image3);
		
		document.close();

	}
	
}
