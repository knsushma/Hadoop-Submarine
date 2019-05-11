package systhemis;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class SysThemis {
    Cluster mCluster;
    EventHandler eventHandler;
    Arbiter arbiter;

    public void init() {
        mCluster = new Cluster();
        eventHandler = new EventHandler();
        arbiter = new Gandiva();
    }

    public void run() {
        double MAX_TIME = 60;
        long EPOCH_TIME = 2 * 60 * 1000;
        double startTime = System.currentTimeMillis() / (60 * 1000);
        double currentTime = 0;
        while (currentTime < MAX_TIME) {

            // update time
            double nowTime = System.currentTimeMillis() / (60 * 1000);
            currentTime = nowTime - startTime;

            // update job iterations
            for (Job job : mCluster.mRunningJobs) {
                int iter = eventHandler.readIterationEvent(job); //////////// interface with themis master service
                job.mCurrentIteration = iter;
            }

            // check if any job arrived
            Iterator<Job> runnableJobs = mCluster.mRunnableJobs.iterator();
            while (runnableJobs.hasNext()) {
                Job job = runnableJobs.next();
                if (job.mJobStartTime <= currentTime) {
                    runnableJobs.remove();
                    mCluster.mRunningJobs.add(job);
                }
            }

            Set<GPU> availableGPUs = new HashSet<GPU>();

            // check if any job ended
            Iterator<Job> runningJobs = mCluster.mRunningJobs.iterator();
            while (runningJobs.hasNext()) {
                Job job = runningJobs.next();
                if (job.mCurrentIteration >= job.mTotalExpectedIterations) {
                    eventHandler.killJobEvent(job); //////////// interface with themis master service
                    for (GPU gpu : job.mCurrentIterationGPUs) {
                        availableGPUs.add(gpu);
                    }
                    runningJobs.remove();
                    mCluster.mCompletedJobs.add(job);
                }
            }

            // check if any lease expired
            for (GPU gpu : mCluster.mGpusInCluster) {
                if (gpu.mLeaseEnd <= currentTime) {
                    gpu.init();
                    availableGPUs.add(gpu);
                }
            }

            // run simulator logic and get events
            List<Event> events = arbiter.runLogic();
            for (Event event : events) {
                if (event.eventType.equals("killJob")) {
                    eventHandler.killJobEvent(event.job); //////////// interface with themis master service
                } else if (event.eventType.equals("launchJob")) {
                    eventHandler.launchJobEvent(event.job, event.arguments); //////////// interface with themis master service
                } else if (event.eventType.equals("changeJob")) {
                    eventHandler.changeJobEvent(event.job, event.arguments);
                }
            }

            try {
                Thread.sleep(EPOCH_TIME);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}