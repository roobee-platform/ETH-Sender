package ru.rb.eth.model;

import java.math.BigDecimal;
import java.math.BigInteger;

public class Transaction {

    private int rowIndex;

    private String to;
    private String amount;
    private int status;

    private String errorMsg;

    private BigInteger gasUsed;
    private String hash;

    public Transaction(int rowIndex, String to, String amount) {
        this.rowIndex = rowIndex;
        this.to = to;
        this.amount = amount;
        status = TransactionStatus.PARSED;
    }

    public void updateStatus(int newStatus) {
        status = newStatus;
    }

    public void setErrorMsg(String errorMsg) {
        this.errorMsg = errorMsg;
        status = TransactionStatus.ERROR;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    public void setGasUsed(BigInteger gasUsed) {
        this.gasUsed = gasUsed;
    }

    public int getRowIndex() {
        return rowIndex;
    }

    public String getTo() {
        return to;
    }

    public BigDecimal getAmountDec() {
        return new BigDecimal(amount);
    }

    public String getAmount() {
        return amount;
    }

    public int getStatus() {
        return status;
    }

    public String getErrorMsg() {
        return errorMsg;
    }

    public String getHash() {
        return hash;
    }

    public BigInteger getGasUsed() {
        return gasUsed;
    }
}
