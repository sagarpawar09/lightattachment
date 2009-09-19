package com.lightattachment.mails;

import java.io.IOException;
import java.io.OutputStream;

public class StringOutputStream extends OutputStream {
    private final StringBuffer captureOfSysOut;

    public StringOutputStream(StringBuffer captureOfSysOut) {
        this.captureOfSysOut = captureOfSysOut;
    }

    public void write(int i) throws IOException {
        captureOfSysOut.append((char) i);
    }
    
    public String getString() {
    	return captureOfSysOut.toString();
    }
}
