public class Event implements Comparable<Event> {
    public enum EventType {
        edArrival,
        sortDeparture,
        registerDeparture,
        triageDeparture,
        zoneDeparture,
        decideToLWBS,

        reassessmentCheck,
        reassessmentDeparture

        }

    public double eventTime;
    public EventType type;
    public Patient patient;

    public Event(double eventTime, EventType type, Patient patient) {
        this.eventTime = eventTime;
        this.type = type;
        this.patient = patient;
    }

    @Override
    public int compareTo(Event other) {
        return Double.compare(this.eventTime, other.eventTime);
    }
}
