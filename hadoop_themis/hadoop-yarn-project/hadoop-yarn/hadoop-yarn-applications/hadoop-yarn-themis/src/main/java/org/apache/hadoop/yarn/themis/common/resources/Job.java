package org.apache.hadoop.yarn.themis.common.resources;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.hadoop.yarn.themis.policies.Event;

public class Job {
	public int mJobId; // Unique identifier for this job
	public double mJobStartTime; // Job start time
	public int mTotalExpectedIterations; // Total number of iterations job is expected to run
	public double mTimePerIteration; // Amount of time for a single iteration of job on 1 GPU
	public int mMaxParallelism; // Represents max GPUs job can request
	// public double mCrossSlotSlowdown; // Slowdown due to network b/w GPUs across slots
	public double mCrossMachineSlowdown; // Slowdown due to network b/w GPUs across machines
	// public double mCrossRackSlowdown; // Slowdown due to network b/w GPUs across slots
	public double mAttainedService; 
	public double mTi;
	public double mTs;
	public double mRho;
	public double mTimeStart;
	public double mFinishTime;
    
	public int mCurrentIteration;
    public List<GPU> mCurrentIterationGPUs;
    public List<GPU> mNextIterationGPUs;
    public boolean isLaunch;
    
    /* Themis Master Service Configurations */
    public String jobName;
	public String WorkerDockerImage;
	public String PsDockerImage;
	public String WorkerResources;
	public String PsResources;
	public String NumWorker;
	public String NumPS;
	public String WorkerLaunchCmd;
	public String PSLaunchCmd;
	public String WorkerLocalityPrefs;
	public String PsLocalityPrefs;
	public String InputDataPath;
	public String CheckpointDataPath;
	
	public Job() {
		mCurrentIterationGPUs = new ArrayList<GPU>();
		mNextIterationGPUs = new ArrayList<GPU>();
		isLaunch = true;
		mCurrentIteration = 0;
		mAttainedService = 0.0;
		mTimePerIteration = 1.5/60.0; 
		mCrossMachineSlowdown = 0.7;
		mTs = 0.0;
		mTi = (mTotalExpectedIterations - mCurrentIteration)*mTimePerIteration / mMaxParallelism;
		mRho = mTs/mTi;
	}
	
	public Job(Job job, int counter) {
		mJobId = job.mJobId + counter*100; 
		mTotalExpectedIterations = job.mTotalExpectedIterations;
		mMaxParallelism = job.mMaxParallelism;
		jobName = job.jobName + counter;
		WorkerDockerImage = job.WorkerDockerImage;
		PsDockerImage = job.PsDockerImage;
		WorkerResources = job.WorkerResources;
		PsResources = job.PsResources;
		NumWorker = job.NumWorker;
		PsResources = job.PsResources;
		NumWorker = job.NumWorker;
		NumPS = job.NumPS;
		WorkerLaunchCmd = job.WorkerLaunchCmd;
		PSLaunchCmd = job.PSLaunchCmd;
		WorkerLocalityPrefs = job.WorkerLocalityPrefs;
		PsLocalityPrefs = job.PsLocalityPrefs;
		InputDataPath = job.InputDataPath;
		CheckpointDataPath = job.CheckpointDataPath + counter*100;
		mCurrentIterationGPUs = new ArrayList<GPU>();
		mNextIterationGPUs = new ArrayList<GPU>();
		isLaunch = true;
		mCurrentIteration = 0;
		mAttainedService = 0.0;
		mTimePerIteration = 1.5/60.0; 
		mCrossMachineSlowdown = 0.7;
		mTs = 0.0;
		mTi = (mTotalExpectedIterations - mCurrentIteration)*mTimePerIteration / mMaxParallelism;
		mRho = mTs/mTi;
	}
	
	public void updateIter(double elapsedTime) {
		if (mCurrentIterationGPUs.size() > 0) {
			double adjustedIterTime = mTimePerIteration/(mCurrentIterationGPUs.size()*getPlacementSlowdown(mCurrentIterationGPUs));
			mCurrentIteration = mCurrentIteration + (int)(elapsedTime/adjustedIterTime);
		}
	}

	// only called after mNextIterationGPUs is initialized and not updated for offered gpu
	public void updateRho() {
		double mTimeElapsed = System.currentTimeMillis()/(60*1000) - mTimeStart;
		mTs = mTimeElapsed + ((mTotalExpectedIterations - mCurrentIteration)*mTimePerIteration)/(mNextIterationGPUs.size()*getPlacementSlowdown(mNextIterationGPUs));
		mRho = mTs/mTi;
	}

	// only called after mNextIterationGPUs is initialized and not updated for offered gpu
	public int getAffinity(GPU gpu) {
		if (mNextIterationGPUs == null || mNextIterationGPUs.size() == 0) 
			return 1;
		
		Iterator<GPU> gpuIter = mNextIterationGPUs.iterator();
		while (gpuIter.hasNext()) {
			GPU mygpu = gpuIter.next();
			if (gpu.machineID == mygpu.machineID)
				return 1;
		}
		return 0;
	}

	// only called after mNextIterationGPUs is initialized
	public double getSpread() {
		Set<Integer> map = new HashSet<Integer>();
		Iterator<GPU> gpuIter = mNextIterationGPUs.iterator();

		// Check if across machines
		while (gpuIter.hasNext()) {
			GPU gpu = gpuIter.next();
			map.add(gpu.machineID);
		}

		double spread = map.size()*1.0;
		if (spread == 0.0) {
			spread = 1.0;
		}
		return spread;
	}
	
	public boolean getFit(GPU potentialGPU) {
		Set<Integer> map = new HashSet<Integer>();
		Iterator<GPU> gpuIter = mNextIterationGPUs.iterator();

		// Check if across machines
		while (gpuIter.hasNext()) {
			GPU gpu = gpuIter.next();
			map.add(gpu.machineID);
		}
		int oldSpread = map.size();
		if (oldSpread == 0) {
			oldSpread = 1;
		}
		
		map.add(potentialGPU.machineID);
		int newSpread = map.size();
		
		return (newSpread == oldSpread);
	}

	// only called after mNextIterationGPUs is initialized
	public double getPlacementSlowdown(List<GPU> gpus) {
		Set<Integer> map = new HashSet<Integer>();
		Iterator<GPU> gpuIter = gpus.iterator();

		// Check if across machines
		while (gpuIter.hasNext()) {
			GPU gpu = gpuIter.next();
			map.add(gpu.machineID);
			if (map.size() > 1) {
				return mCrossMachineSlowdown;
			}
		}
	
		return 1.0;
	}
	
	public void updatePrefs(List<GPU> newGPUs) {
		if (isLaunch) {
    		NumPS = "1";
    		NumWorker = Integer.toString(newGPUs.size());
    		String wlp = "";
        	String plp = "";
        	for (GPU gpu: newGPUs) {
        		wlp += gpu.machineHostName + ",";
        		plp = gpu.machineHostName;
        	}
        	if (wlp.length() > 0) {
        		wlp = wlp.substring(0, wlp.length() - 1);
        	} else {
        		NumPS = "0";
        		wlp = null;
        		plp = null;
        	}
        	WorkerLocalityPrefs = wlp;
        	PsLocalityPrefs = plp;
        	isLaunch = false;
    	} else {
    		NumPS = "1";
        	NumWorker = Integer.toString(newGPUs.size());
        	String wlp = "";
        	String plp = "";
        	for (GPU gpu: newGPUs) {
        		wlp += gpu.machineHostName + ",";
        		plp = gpu.machineHostName;
        	}
        	if (wlp.length() > 0) {
        		wlp = wlp.substring(0, wlp.length() - 1);
        	} else {
        		NumPS = "0";
        		wlp = null;
        		plp = null;
        	}
        	WorkerLocalityPrefs = wlp;
        	PsLocalityPrefs = plp;
    	}
	}
}