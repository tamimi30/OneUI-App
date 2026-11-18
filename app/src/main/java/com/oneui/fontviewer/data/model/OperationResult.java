package com.oneui.fontviewer.data.model;

public class OperationResult {
    public final int           succeeded;     
    public final int           failed;        
    public final OperationType operationType; 
    public final boolean       wasCancelled;  

    public OperationResult(int succeeded, int failed,
                           OperationType operationType, boolean wasCancelled) {
        this.succeeded     = succeeded;
        this.operationType = operationType;
        this.failed        = failed;
        this.wasCancelled  = wasCancelled;
    }
}
