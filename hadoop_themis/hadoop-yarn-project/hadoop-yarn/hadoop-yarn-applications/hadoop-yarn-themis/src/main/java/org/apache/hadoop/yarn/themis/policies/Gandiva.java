package org.apache.hadoop.yarn.themis.policies;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.module.jaxb.JaxbAnnotationModule.Priority;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.yarn.themis.ThemisEvent;
import org.apache.hadoop.yarn.themis.ThemisEventType;
import org.apache.hadoop.yarn.themis.ThemisMasterService;
import org.apache.hadoop.yarn.themis.common.resources.GPU;
import org.apache.hadoop.yarn.themis.common.resources.Job;

public class Gandiva implements Arbiter {
	
	private static final Log LOG = LogFactory.getLog(Gandiva.class);
	
    public List<Event> runLogic(SysThemis system) {
        List<Event> events = new ArrayList<Event>();

        // get all free GPUs
        List<GPU> availableGPUs = new ArrayList<GPU>();
        for (GPU gpu: system.mCluster.mGpusInCluster) {
            if (gpu.mJobUsingGPU == null) {
                availableGPUs.add(gpu);
                LOG.info("[Gandiva] Added to available GPU ID = " + gpu.gpuID);
            }
        }

        // initialize next iter GPU allocs
        for (Job job: system.mCluster.mRunningJobs) {
            job.mNextIterationGPUs = job.mCurrentIterationGPUs;
        }

        // allocate free GPUs to Jobs with best placement score
        Map<Job, List<GPU>> allocs = new HashMap<Job, List<GPU>>();
        for (Job job: system.mCluster.mRunningJobs) {
        	if (job.mMaxParallelism == job.mNextIterationGPUs.size())
        		continue;
        	Iterator<GPU> gpuIter = availableGPUs.iterator();
        	while (gpuIter.hasNext()) {
        		if (job.mMaxParallelism == job.mNextIterationGPUs.size())
            		break;
        		GPU gpu = gpuIter.next();
        		if (job.getFit(gpu)) {
        			job.mNextIterationGPUs.add(gpu);
        			gpu.mJobUsingGPU = job;
                    gpu.mLeaseStart = System.currentTimeMillis()/(60*1000) - system.startTime;
                    gpu.mLeaseEnd = gpu.mLeaseStart + system.leaseTime;
                    if (allocs.containsKey(job)) {
                        allocs.get(job).add(gpu);
                    } else {
                        List<GPU> gpuList = new ArrayList<GPU>();
                        gpuList.add(gpu);
                        allocs.put(job, gpuList);
                    }
                    gpuIter.remove();
        		}
        	}    	
        }
        
        for (GPU gpu: availableGPUs) {
            double affinityScoreBest = -1.0;
            Job bestJob = null;
            for (Job job: system.mCluster.mRunningJobs) {
            	if (job.mMaxParallelism == job.mNextIterationGPUs.size())
            		continue;
                double affinityScore = job.getAffinity(gpu) / job.getSpread();
                LOG.info("[Gandiva] Affinity Score = " + affinityScore + " GPU ID " + gpu.gpuID);
                if (affinityScore >= affinityScoreBest) {
                    affinityScoreBest = affinityScore;
                    bestJob = job;
                }
            }
            if (bestJob == null)
            	break;
            bestJob.mNextIterationGPUs.add(gpu);
            gpu.mJobUsingGPU = bestJob;
            gpu.mLeaseStart = System.currentTimeMillis()/(60*1000) - system.startTime;
            gpu.mLeaseEnd = gpu.mLeaseStart + system.leaseTime;
            if (allocs.containsKey(bestJob)) {
                allocs.get(bestJob).add(gpu);
            } else {
                List<GPU> gpuList = new ArrayList<GPU>();
                gpuList.add(gpu);
                allocs.put(bestJob, gpuList);
            }
        }

        for (Job job: allocs.keySet()) {
        	if (job.isLaunch) {
        		job.NumPS = "1";
        		job.NumWorker = Integer.toString(job.mNextIterationGPUs.size());
        		String wlp = "";
	        	String plp = "";
	        	for (GPU gpu: job.mNextIterationGPUs) {
	        		wlp += gpu.machineHostName + ",";
	        		plp = gpu.machineHostName;
	        	}
	        	wlp = wlp.substring(0, wlp.length() - 1);
	        	job.WorkerLocalityPrefs = wlp;
	        	job.PsLocalityPrefs = plp;
	        	events.add(new Event(job, "launchJob"));
	        	job.isLaunch = false;
	        	job.mCurrentIterationGPUs = job.mNextIterationGPUs;
        	} else {
        		job.NumPS = "1";
	        	job.NumWorker = Integer.toString(job.mNextIterationGPUs.size());
	        	String wlp = "";
	        	String plp = "";
	        	for (GPU gpu: job.mNextIterationGPUs) {
	        		wlp += gpu.machineHostName + ",";
	        		plp = gpu.machineHostName;
	        	}
	        	wlp = wlp.substring(0, wlp.length() - 1);
	        	job.WorkerLocalityPrefs = wlp;
	        	job.PsLocalityPrefs = plp;
	            events.add(new Event(job, "changeJob"));
	            job.mCurrentIterationGPUs = job.mNextIterationGPUs;
        	}
        }

        return events;
    }
}