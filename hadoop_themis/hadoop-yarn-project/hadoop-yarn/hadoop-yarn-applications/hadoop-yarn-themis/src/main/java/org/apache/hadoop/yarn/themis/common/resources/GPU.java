package org.apache.hadoop.yarn.themis.common.resources;

public class GPU {
    public double mLeaseStart; 
    public double mLeaseDuration;
    public double mLeaseEnd;
    public Job mJobUsingGPU;
    
    public int machineID;
    public String machineHostName;
    
    public int gpuID;
    
    public GPU(int gpuID, int machineID, String machineHostName) {
    	this.gpuID = gpuID;
    	this.machineID = machineID;
    	this.machineHostName = machineHostName;
    	this.init();
    }

    public void init() {
        mLeaseEnd = -1.0;
        mLeaseStart = -1.0;
        mJobUsingGPU = null;
    }
}