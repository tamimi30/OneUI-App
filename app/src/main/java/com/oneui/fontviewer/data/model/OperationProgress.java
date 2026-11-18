package com.oneui.fontviewer.data.model;

public class OperationProgress {
    public final int           current;       
    public final int           total;         
    public final OperationType operationType; 

    public OperationProgress(int current, int total, OperationType operationType) {
        this.current       = current;
        this.total         = total;
        this.operationType = operationType;
    }
}
