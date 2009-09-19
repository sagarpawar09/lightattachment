package com.lightattachment.smtp;

/**
 * SMTP response container.
 */
public class SmtpResponse {
	/** Response code - see RFC-2821. */
	private int code;

	/** Response message. */
	private String message;

	/** New state of the SMTP server once the request has been executed. */
	private SmtpState nextState;

	/**
	 * Constructor.
	 * @param code response code
	 * @param message response message
	 * @param next next state of the SMTP server
	 */
	public SmtpResponse(int code, String message, SmtpState next) {
		this.code = code;
		this.message = message;
		this.nextState = next;
	}

	/**
	 * Get the response code.
	 * @return response code
	 */
	public int getCode() {
		return code;
	}

	/**
	 * Get the response message.
	 * @return response message
	 */
	public String getMessage() {
		return message;
	}

	/**
	 * Get the next SMTP server state.
	 * @return state
	 */
	public SmtpState getNextState() {
		return nextState;
	}
}
