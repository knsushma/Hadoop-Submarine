package org.apache.hadoop.yarn.themis.policies;

import org.apache.hadoop.yarn.themis.common.resources.Job;

public class Event {
    String eventType;
    Job job; 
    String arguments;

    public Event(String eventType) {
        this.eventType = eventType;
    }

    public Event(Job job, String eventType) {
        this.job = job;
        this.eventType = eventType;
    }
}