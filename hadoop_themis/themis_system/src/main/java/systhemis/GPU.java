package systhemis;

public class GPU {
    public double mLeaseStart; 
    public double mLeaseDuration;
    public double mLeaseEnd;
    public Job mJobUsingGPU;

    public void init() {
        mLeaseEnd = -1.0;
        mJobUsingGPU = null;
    }
}