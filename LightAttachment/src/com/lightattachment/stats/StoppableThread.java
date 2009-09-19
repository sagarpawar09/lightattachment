package com.lightattachment.stats;

public class StoppableThread extends Thread {

	private boolean done;
	
	private long begin;
	
	private long end;
	
	public StoppableThread() {
		super();
		done = false;
		begin = System.currentTimeMillis();
	}
	
	public void run() {
		super.run();
	}

	public boolean isDone() {
		return done;
	}

	public void setDone(boolean done) {
		this.done = done;
		if (done) end = System.currentTimeMillis();
	}

	public long getBegin() {
		return begin;
	}

	public long getEnd() {
		return end;
	}
	
}
