package com.timekeeper.bibexpo.batch;

public class DuplicateBibException extends RuntimeException {

    private final String bibNumber;
    private final int rowNumber;

    public DuplicateBibException(String bibNumber, int rowNumber) {
        super("Duplicate BIB number '" + bibNumber + "' at row " + rowNumber);
        this.bibNumber = bibNumber;
        this.rowNumber = rowNumber;
    }

    public String getBibNumber() { return bibNumber; }
    public int getRowNumber() { return rowNumber; }
}
