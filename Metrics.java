public abstract class Metrics {
    protected double realMeanWaitingTime; // (TQ)-delay| E Waiting Time | E Door-to-Provider time
    protected double realMeanServiceTime; //E[S] - Expected Service Time
    protected double realServiceRate; //(μ)-Service Rate
    protected double realMeanInterArrivalTime; 
    protected double realArrivalRate; //(λ)-Arrival Rate
    protected double realResponseTime; // E[T] - Expected Response Time
    protected int totalProcessed; // (X) - Throughput
    protected int currentQueueSize; // (NQ) - Current Queue Size
    protected double utilization; // (ρ) - Utilization
    protected double patientsInSystem; // (N) - Total Patients in System
    protected Simulator.StationName stationName;
    protected int totalArrivals;
    protected double efficiency;

    protected Metrics(Simulator.StationName stationName) {
        this.stationName = stationName;
        totalArrivals = 0;
        realMeanWaitingTime = 0.0;
        realMeanServiceTime = 0.0;
        realServiceRate = 0.0;
        realMeanInterArrivalTime = 0.0;
        realArrivalRate = 0.0;
        realResponseTime = 0.0;
        totalProcessed = 0;
        currentQueueSize = 0;
        utilization = 0.0;
        patientsInSystem = 0.0;
        efficiency = 0.0;
    }
}
