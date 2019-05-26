package systhemis;

import java.util.List;

public class Cluster {
    public List<GPU> mGpusInCluster;
    public List<Job> mRunningJobs;
    public List<Job> mRunnableJobs;
    public List<Job> mCompletedJobs;
}