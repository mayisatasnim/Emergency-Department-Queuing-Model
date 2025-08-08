import java.util.*;

public abstract class ServiceStation extends Metrics {
    protected int debug = 0;

    //change for multiple bed support in zones
    protected int numBeds; //servers
    protected int busyBeds = 0; //beds in use
     //time in minutes to decide to LWBS
    protected double meanServiceTime;
    protected double serviceStdDev;
    protected WaitingArea queue;
    protected List<Patient> departedPatients;
    protected TreeSet<Patient> arrivedPatients; // Changed to TreeSet for automatic sorting
    protected List<Patient> lwbsPatients;
    protected PriorityQueue<Event> eventList;
    protected Simulator simulator;


    public ServiceStation(Simulator.StationName stationName, double meanServiceTime, double serviceStdDev, int numBeds, Simulator simulator) {
        super(stationName);
        this.stationName = stationName;
        setServiceTime(meanServiceTime, serviceStdDev);

        //for multiple bed support
        this.numBeds = numBeds;
        this.busyBeds = 0;

        this.queue = new WaitingArea(WaitingArea.PrioritizationPolicy.HIGHER_ACUITY_FIRST);
        this.departedPatients = new ArrayList<>();
        this.arrivedPatients = new TreeSet<>(getArrivalTimeComparator());
        this.lwbsPatients = new ArrayList<>();
        this.eventList = simulator.eventList;
        this.simulator = simulator;
    }

    public void setServiceTime(double meanServiceTime, double serviceStdDev) {
        this.meanServiceTime = meanServiceTime;
        this.serviceStdDev = serviceStdDev;
    }

    public void addPatient(Event currentEvent) {
        Patient patient = currentEvent.patient;
        queue.add(patient);
        
        // CRITICAL: Set arrival time BEFORE adding to TreeSet so sorting works correctly!
        setPatientArrivalTime(patient, currentEvent.eventTime);
        arrivedPatients.add(patient);
        totalArrivals++;
        updatePatientLocation(patient);
        patient.scheduleDecideToLWBS(simulator);
        setPatientDepartureTime(patient, Double.POSITIVE_INFINITY);

        if (debug == 1) {
            System.out.println("[" + stationName + "]: Added " + patient.id + " to queue @T: " + currentEvent.eventTime);
        }

        // if available bed
        if (busyBeds < numBeds) {
            scheduleNextDeparture(currentEvent.eventTime);
        }

    }

    protected void scheduleNextDeparture(double currentTime) {
        Patient nextPatient = queue.poll();
        setPatientProcessingTime(nextPatient, currentTime);

        double serviceTime = Utils.getNormal(meanServiceTime, serviceStdDev);

        //double serviceTime = Utils.getExp((1.0/4.0));


        double nextDepartureTime = currentTime + serviceTime;
        eventList.add(new Event(nextDepartureTime, getDepartureEventType(), nextPatient));
        busyBeds++;
        if (debug == 1) {
            System.out.println("[" + stationName + "]: Next departure: " + nextDepartureTime);
        }
    }

    protected void updatePatientLocation(Patient patient) {
        patient.currentStationName = stationName;
    }

    public void departServiceStation(Event currentEvent) {

        if (debug == 1) {
            System.out.println(currentEvent.patient.id + " DP_" + stationName + ": " + currentEvent.eventTime);
        }

        sendToAppropriateNextStation(currentEvent);
        setPatientDepartureTime(currentEvent.patient, currentEvent.eventTime);
        departedPatients.add(currentEvent.patient);
        busyBeds--;

        //start service for another patient if queue isn't empty
        if (!queue.isEmpty()) {
            scheduleNextDeparture(currentEvent.eventTime);
        }


    }

    public double getArrivalTime(Patient patient) {
        return switch(stationName) {
            case ED, SORT -> patient.sortingAT;
            case REGISTRATION -> patient.registrationAT;
            case TRIAGE -> patient.triageAT;
            case REASSESSMENT -> patient.reassessmentAT;
            case ZONE, ERU, FAST_TRACK, RED, GREEN, BLUE -> patient.zoneAT;
            default -> patient.sortingAT;
        };
    }

    public double getProcessingTime(Patient patient) {
        return switch(stationName) {
            case ED, SORT -> patient.sortingPT;
            case REGISTRATION -> patient.registrationPT;
            case TRIAGE -> patient.triagePT;
            case REASSESSMENT -> patient.reassessmentPT;
            case ZONE, ERU, FAST_TRACK, RED, GREEN, BLUE -> patient.zonePT;
            default -> patient.sortingPT;
        };
    }

    public double getDepartureTime(Patient patient) {
        return switch(stationName) {
            case ED, SORT -> patient.sortingDT;
            case REGISTRATION -> patient.registrationDT;
            case TRIAGE -> patient.triageDT;
            case REASSESSMENT -> patient.reassessmentDT;
            case ZONE, ERU, FAST_TRACK, RED, GREEN, BLUE -> patient.zoneDT;
            default -> patient.sortingDT;
        };
    }

    public double getWaitingTime(Patient patient) {
        return getProcessingTime(patient) - getArrivalTime(patient);
    }

    public double getServiceTime(Patient patient) {
        return getDepartureTime(patient) - getProcessingTime(patient);
    }

    public double getResponseTime(Patient patient) {
        return getDepartureTime(patient) - getArrivalTime(patient);
    }

    public double totalInterArrivalTime() {
        if (arrivedPatients.size() <= 1) return 0.0;

        double sum = 0.0;
        Patient previous = null;
        
        // TreeSet iteration is already in sorted order!
        for (Patient current : arrivedPatients) {
            if (previous != null) {
                double currentTime = getArrivalTime(current);
                double previousTime = getArrivalTime(previous);
                sum += currentTime - previousTime;
            }
            previous = current;
        }
        
        return sum;
    }

    public Comparator<Patient> getArrivalTimeComparator() {
        return (p1, p2) -> {
            double time1 = getArrivalTime(p1);
            double time2 = getArrivalTime(p2);
            int timeCompare = Double.compare(time1, time2);
            
            // If arrival times are equal, compare by registration number to maintain order
            if (timeCompare == 0) {
                return Integer.compare(p1.regNo, p2.regNo);
            }
            return timeCompare;
        };
    }
    public void printQuickStats() {
        computeMetrics();
        System.out.println("\n[" + stationName + "]: Quick Stats");
        System.out.printf("Total arrivals: %d%n", totalArrivals);
        System.out.printf("Total processed: %d%n", departedPatients.size());
        System.out.printf("Current Queue size [waiting]: %d%n", queue.size());
        System.out.printf("Avg arrivals per day: %.2f%n", (totalArrivals / (double) simulator.numDays));
        System.out.printf("%% arrivals at this station: %.2f%%%n", (totalArrivals * 100.0 / simulator.totalArrivals));
        if (stationName != Simulator.StationName.SORT && stationName != Simulator.StationName.REASSESSMENT) {
            System.out.printf("%% Arrivals rel to %s output: %.2f%%%n", getPrecedingStation().stationName, (totalArrivals * 100.0 / getPrecedingStation().totalArrivals));
        }
        if (stationName == Simulator.StationName.ERU) {
            System.out.printf("%% Arrivals rel to SORT: %.2f%%%n", (totalArrivals * 100.0 / simulator.sortNurse.totalArrivals));
        }
        System.out.printf("[R]Mean %s waiting time: %s%n", stationName, Utils.formatMinsToHours(realMeanWaitingTime));
        System.out.printf("[R]Mean %s service time: %s%n", stationName, Utils.formatMinsToHours(realMeanServiceTime));
        System.out.printf("[E]Mean %s service time: %s%n", stationName, Utils.formatMinsToHours(meanServiceTime));
        System.out.printf("[R]Mean %s LOS [ResponseTime]: %s%n", stationName, Utils.formatMinsToHours(realResponseTime));
        System.out.printf("[R]Mean %s Inter-Arrival Time: %s%n", stationName, Utils.formatMinsToHours(realMeanInterArrivalTime));
        System.out.printf("Utilization (ρ): %.2f%n", utilization);
        System.out.printf("Efficiency: %.2f%%%n", efficiency * 100.0);
    }

    public void computeMetrics() {
        realMeanWaitingTime = Statistics.calculateMean(simulator, stationName, Statistics.Property.WAITING_TIME);
        realMeanServiceTime = Statistics.calculateMean(simulator, stationName, Statistics.Property.PROCESSING_TIME);
        realResponseTime = Statistics.calculateMean(simulator, stationName, Statistics.Property.RESPONSE_TIME);
        realMeanInterArrivalTime = Statistics.calculateMean(simulator, stationName, Statistics.Property.INTER_ARRIVAL_TIME);
        totalProcessed = departedPatients.size(); // (X) - Throughput
        currentQueueSize = queue.size(); // (NQ) - Current Queue Size
        realServiceRate = (realMeanServiceTime > 0) ? 1.0 / realMeanServiceTime : 0;
        realArrivalRate = (realMeanInterArrivalTime > 0) ? 1.0 / realMeanInterArrivalTime : 0;
        utilization = (realServiceRate > 0) ? realArrivalRate / realServiceRate : 0;  // Utilization (ρ) = λ / μ
        efficiency = totalProcessed * 1.0 / (double) totalArrivals; // Efficiency = Throughput / Arrivals

    }

    protected abstract void setPatientArrivalTime(Patient patient, double time);
    protected abstract void setPatientDepartureTime(Patient patient, double time);
    protected abstract void setPatientProcessingTime(Patient patient, double time);
    protected abstract Event.EventType getDepartureEventType();
    protected abstract void sendToAppropriateNextStation(Event currentEvent);
    protected abstract double getPatientArrivalTime(Patient patient);
    protected abstract ServiceStation getPrecedingStation();
}
