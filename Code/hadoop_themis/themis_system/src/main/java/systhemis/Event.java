package systhemis;

public class Event {
    String eventType;
    Job job; 
    String arguments;

    public Event(String eventType) {
        this.eventType = eventType;
    }

    public Event(String eventType, Job job) {
        this.job = job;
        this.eventType = eventType;
    }

    public Event(String eventType, Job job, String arguments) {
        this.job = job;
        this.arguments = arguments;
        this.eventType = eventType;
    }
}