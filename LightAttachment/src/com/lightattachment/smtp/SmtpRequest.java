package com.lightattachment.smtp;

/**
 * Contains an SMTP client request. Handles state transitions using the following state transition table.
 * <PRE>
 * -----------+-------------------------------------------------------------------------------------------------
 *            |                                 State
 *  Action    +-------------+-----------+-----------+--------------+---------------+---------------+------------
 *            | CONNECT     | GREET     | MAIL      | RCPT         | DATA_HDR      | DATA_BODY     | QUIT
 * -----------+-------------+-----------+-----------+--------------+---------------+---------------+------------
 * connect    | 220/GREET   | 503/GREET | 503/MAIL  | 503/RCPT     | 503/DATA_HDR  | 503/DATA_BODY | 503/QUIT
 * ehlo       | 503/CONNECT | 250/MAIL  | 503/MAIL  | 503/RCPT     | 503/DATA_HDR  | 503/DATA_BODY | 503/QUIT
 * mail       | 503/CONNECT | 503/GREET | 250/RCPT  | 503/RCPT     | 503/DATA_HDR  | 503/DATA_BODY | 250/RCPT
 * rcpt       | 503/CONNECT | 503/GREET | 503/MAIL  | 250/RCPT     | 503/DATA_HDR  | 503/DATA_BODY | 503/QUIT
 * data       | 503/CONNECT | 503/GREET | 503/MAIL  | 354/DATA_HDR | 503/DATA_HDR  | 503/DATA_BODY | 503/QUIT
 * data_end   | 503/CONNECT | 503/GREET | 503/MAIL  | 503/RCPT     | 250/QUIT      | 250/QUIT      | 503/QUIT
 * unrecog    | 500/CONNECT | 500/GREET | 500/MAIL  | 500/RCPT     | ---/DATA_HDR  | ---/DATA_BODY | 500/QUIT
 * quit       | 503/CONNECT | 503/GREET | 503/MAIL  | 503/RCPT     | 503/DATA_HDR  | 503/DATA_BODY | 250/CONNECT
 * blank_line | 503/CONNECT | 503/GREET | 503/MAIL  | 503/RCPT     | ---/DATA_BODY | ---/DATA_BODY | 503/QUIT
 * rset       | 250/GREET   | 250/GREET | 250/GREET | 250/GREET    | 250/GREET     | 250/GREET     | 250/GREET
 * vrfy       | 252/CONNECT | 252/GREET | 252/MAIL  | 252/RCPT     | 252/DATA_HDR  | 252/DATA_BODY | 252/QUIT
 * expn       | 252/CONNECT | 252/GREET | 252/MAIL  | 252/RCPT     | 252/DATA_HDR  | 252/DATA_BODY | 252/QUIT
 * help       | 211/CONNECT | 211/GREET | 211/MAIL  | 211/RCPT     | 211/DATA_HDR  | 211/DATA_BODY | 211/QUIT
 * noop       | 250/CONNECT | 250/GREET | 250/MAIL  | 250/RCPT     | 250|DATA_HDR  | 250/DATA_BODY | 250/QUIT
 * </PRE>
 */
public class SmtpRequest {
	/** SMTP action received from client. */
	private SmtpActionType action;

	/** Current state of the SMTP state table. */
	private SmtpState state;

	/** Additional information passed from the client with the SMTP action. */
	private String params;
	
	private String from;
	
	private String to;
	
	private static int rejectCount = 0;

	/**
	 * Create a new SMTP client request.
	 * @param actionType type of action/command
	 * @param params remainder of command line once command is removed
	 * @param state current SMTP server state
	 */
	public SmtpRequest(SmtpActionType actionType, String params, SmtpState state) {
		this.action = actionType;
		this.state = state;
		this.params = params;
		if (actionType.equals(SmtpActionType.MAIL)) {
			from = params;
		} else if (actionType.equals(SmtpActionType.RCPT)) {
			to = params;
		}
	}

	/**
	 * Execute the SMTP request returning a response. This method models the state transition table for the SMTP server.
	 * @return reponse to the request
	 */
	public SmtpResponse execute() {
		SmtpResponse response = null;
		//if (!action.toString().contains("Unrecognized"))
		//	System.err.println(action.toString());
		if (action.isStateless()) {
			if (SmtpActionType.EXPN == action || SmtpActionType.VRFY == action) {
				response = new SmtpResponse(252, "Not supported", this.state);
			} else if (SmtpActionType.HELP == action) {
				response = new SmtpResponse(211, "No help available", this.state);
			} else if (SmtpActionType.NOOP == action) {
				response = new SmtpResponse(250, "OK", this.state);
			} else if (SmtpActionType.VRFY == action) {
				response = new SmtpResponse(252, "Not supported", this.state);
			} else if (SmtpActionType.RSET == action) {
				//response = new SmtpResponse(250, "OK", SmtpState.GREET);
				response = new SmtpResponse(250, "OK", SmtpState.MAIL);
			} else {
				response = new SmtpResponse(500, "Command not recognized", this.state);
			}
		} else { // Stateful commands
			if (SmtpActionType.CONNECT == action) {
				if (SmtpState.CONNECT == state) {
					response = new SmtpResponse(220, "LightAttachment SMTP service ready", SmtpState.GREET);
				} else {
					rejectCount++;
					System.err.println("REJECTED not CONNECT: "+rejectCount);
					response = new SmtpResponse(503, "Bad sequence of commands: " + action, this.state);
				}
			} else if (SmtpActionType.EHLO == action) {
				if (SmtpState.GREET == state) {
					response = new SmtpResponse(250, "OK", SmtpState.MAIL);
				} else {
					rejectCount++;
					System.err.println("REJECTED: not GREET> "+rejectCount);
					response = new SmtpResponse(503, "Bad sequence of commands: " + action, this.state);
				}
			} else if (SmtpActionType.MAIL == action) {
				if (SmtpState.MAIL == state || SmtpState.QUIT == state) {
					response = new SmtpResponse(250, "OK", SmtpState.RCPT);
				} else {
					rejectCount++;
					System.err.println("REJECTED: not MAIL or QUIT> "+rejectCount);
					response = new SmtpResponse(503, "Bad sequence of commands: " + action, this.state);
				}
			} else if (SmtpActionType.RCPT == action) {
				if (SmtpState.RCPT == state) {
					response = new SmtpResponse(250, "OK", this.state);
				} else {
					rejectCount++;
					System.err.println("REJECTED: not RCOT> "+rejectCount);
					response = new SmtpResponse(503, "Bad sequence of commands: " + action, this.state);
				}
			} else if (SmtpActionType.DATA == action) {
				if (SmtpState.RCPT == state) {
					response = new SmtpResponse(354, "Start mail input; end with <CRLF>.<CRLF>", SmtpState.DATA_HDR);
				} else {
					rejectCount++;
					System.err.println("REJECTED: not RCPT> "+rejectCount);
					response = new SmtpResponse(503, "Bad sequence of commands: " + action, this.state);
				}
			} else if (SmtpActionType.UNRECOG == action) {
				if (SmtpState.DATA_HDR == state || SmtpState.DATA_BODY == state) {
					response = new SmtpResponse(-1, "", this.state);
				} else {
					response = new SmtpResponse(500, "Command not recognized", this.state);
				}
			} else if (SmtpActionType.DATA_END == action) {
				if (SmtpState.DATA_HDR == state || SmtpState.DATA_BODY == state) {
					response = new SmtpResponse(250, "OK", SmtpState.QUIT);
				} else {
					rejectCount++;
					System.err.println("REJECTED: not DATA_HDR or DATA_BODY> "+rejectCount);
					response = new SmtpResponse(503, "Bad sequence of commands: " + action, this.state);
				}
			} else if (SmtpActionType.BLANK_LINE == action) {
				if (SmtpState.DATA_HDR == state) {
					response = new SmtpResponse(-1, "", SmtpState.DATA_BODY);
				} else if (SmtpState.DATA_BODY == state) {
					response = new SmtpResponse(-1, "", this.state);
				} else {
					rejectCount++;
					System.err.println("REJECTED: not DATA_BODY> "+rejectCount);
					response = new SmtpResponse(503, "Bad sequence of commands: " + action, this.state);
				}
			} else if (SmtpActionType.QUIT == action) {
				if (SmtpState.QUIT == state) {
					response = new SmtpResponse(221, "localhost Dumbster service closing transmission channel", SmtpState.CONNECT);
				} else {
					rejectCount++;
					System.err.println("REJECTED: not QUIT> "+rejectCount);
					response = new SmtpResponse(503, "Bad sequence of commands: " + action, this.state);
				}
			} else {
				rejectCount++;
				System.err.println("REJECTED: "+rejectCount);
				response = new SmtpResponse(500, "Command not recognized", this.state);
			}
		}
		return response;
	}

	/**
	 * Create an SMTP request object given a line of the input stream from the client and the current internal state.
	 * @param s line of input
	 * @param state current state
	 * @return a populated SmtpRequest object
	 */
	public static SmtpRequest createRequest(String s, SmtpState state) {
		SmtpActionType action = null;
		String params = null;

		if (state == SmtpState.DATA_HDR) {
			if (s.equals(".")) {
				action = SmtpActionType.DATA_END;
			} else if (s.length() < 1) {
				action = SmtpActionType.BLANK_LINE;
				params = "\n";
			} else {
				action = SmtpActionType.UNRECOG;
				params = s+"\n";
			}
		} else if (state == SmtpState.DATA_BODY) {
			if (s.equals(".")) {
				action = SmtpActionType.DATA_END;
			} else {
				action = SmtpActionType.UNRECOG;
				if (s.length() < 1) {
					params = "\n";
				} else {
					params = s+"\n";
				}
			}
		} else {
			String su = s.toUpperCase();
			if (su.startsWith("EHLO ") || su.startsWith("HELO")) {
				action = SmtpActionType.EHLO;
				params = s.substring(5);
			} else if (su.startsWith("MAIL FROM:")) {
				action = SmtpActionType.MAIL;
				params = s.substring(10);
			} else if (su.startsWith("RCPT TO:")) {
				action = SmtpActionType.RCPT;
				params = s.substring(8);
			} else if (su.startsWith("DATA")) {
				action = SmtpActionType.DATA;
			} else if (su.startsWith("QUIT")) {
				action = SmtpActionType.QUIT;
			} else if (su.startsWith("RSET")) {
				action = SmtpActionType.RSET;
			} else if (su.startsWith("NOOP")) {
				action = SmtpActionType.NOOP;
			} else if (su.startsWith("EXPN")) {
				action = SmtpActionType.EXPN;
			} else if (su.startsWith("VRFY")) {
				action = SmtpActionType.VRFY;
			} else if (su.startsWith("HELP")) {
				action = SmtpActionType.HELP;
			} else {
				action = SmtpActionType.UNRECOG;
			}
		}

		SmtpRequest req = new SmtpRequest(action, params, state);
		return req;
	}

	/**
	 * Get the parameters of this request (remainder of command line once the command is removed.
	 * @return parameters
	 */
	public String getParams() {
		return params;
	}

	public String getFrom() {
		return from;
	}

	public void setFrom(String from) {
		this.from = from;
	}

	public String getTo() {
		return to;
	}

	public void setTo(String to) {
		this.to = to;
	}

	public SmtpActionType getAction() {
		return action;
	}
	
}
