package systhemis;

import java.util.List;

public class Job {
	public int mJobId; // Unique identifier for this job
	public double mJobStartTime; // Job start time
	public int mTotalExpectedIterations; // Total number of iterations job is expected to run
	// public double mTimePerIteration; // Amount of time for a single iteration of job on 1 GPU
	public int mMaxParallelism; // Represents max GPUs job can request
	// public double mCrossSlotSlowdown; // Slowdown due to network b/w GPUs across slots
	// public double mCrossMachineSlowdown; // Slowdown due to network b/w GPUs across machines
    // public double mCrossRackSlowdown; // Slowdown due to network b/w GPUs across slots
    public int mCurrentIteration;

    public List<GPU> mCurrentIterationGPUs;
    public List<GPU> mNextIterationGPUs;
}