package ru.rb.eth.model;

public class TransactionStatus {

    public final static int PARSED = 1;
    public final static int PENDING = 2;
    public final static int CONFIRMED = 3;
    public final static int ERROR = -1;
    public final static int ADDRESS_PARSING_ERROR = -2;
    public final static int AMOUNT_PARSING_ERROR = -3;
}
