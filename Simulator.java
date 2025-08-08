import javax.swing.plaf.basic.BasicTableHeaderUI;
import java.util.*;

public class Simulator {
    int debug = 0;
    int totalArrivals = 0;
    double currentTime = 0;
    double dayEnd = 24 * 60; // 24 hours in minutes
    double numDays = 1; // default to 1 day for simulation
    double simulationEndTime;
    int warmUpDays = 30;   // number of days to ignore as warm-up
    double warmUpEndTime; // cutoff time in minutes (warmUpDays * 24 * 60)
    double lwbsReevaluationPeriod = 30; // minutes after which patients re-evaluate their LWBS decision
    static boolean enableVariableEDArrivalRate = true; // whether to use variable arrival rates based on time of day

    // event calendar
    PriorityQueue<Event> eventList;
    List<Patient> edDisposedPatients;
    List<Patient> steadyStateDisposedPatients;
    Registration registration;
    SortNurse sortNurse;
    Triage triage;
    Zone fastTrackZone;
    Zone eruZone;
    Zone redZone;
    Zone greenZone;

    public ReassessmentZone reassessmentZone;

    enum StationName {
        SORT,
        REGISTRATION,
        TRIAGE,
        FAST_TRACK,
        ERU,
        RED,
        GREEN,
        BLUE,
        REASSESSMENT,
        ED,
        ZONE,
        NONE //default station name
    }
   

    public Simulator() {
        edDisposedPatients = new ArrayList<>();
        steadyStateDisposedPatients = new ArrayList<>();
        eventList = new PriorityQueue<>();

        warmUpEndTime = warmUpDays * 24 * 60;

        eruZone = new Zone(StationName.ERU, this);
        fastTrackZone = new Zone(StationName.FAST_TRACK, this);
        redZone = new Zone(StationName.RED, this);
        greenZone = new Zone(StationName.GREEN, this);

        reassessmentZone = new ReassessmentZone(this);

        triage = new Triage(this);
        registration = new Registration(this);
        sortNurse = new SortNurse(this);
        configureServiceTimes();
        scheduleNextEDArrival();
    }

    public void configureServiceTimes() {
        sortNurse.setServiceTime(4, 2);
        registration.setServiceTime(3, 2);
        triage.setServiceTime(5, 2);

        reassessmentZone.setServiceTime(5, 2);


        eruZone.setServiceTime(76, 42);
        redZone.setServiceTime(66.7, 29.91);
        greenZone.setServiceTime(38.67, 24.79);
        fastTrackZone.setServiceTime(21.38, 13.38);
    }


    public void begin() {
        simulationEndTime = numDays * dayEnd;

        while (currentTime < simulationEndTime) {
            if (!eventList.isEmpty()) {
                Event currentEvent = eventList.poll();
                currentTime = currentEvent.eventTime;


                //assign staff shifts based on time of day
                staff(currentTime);

                switch (currentEvent.type) {
                    case edArrival:
                        sortNurse.addPatient(currentEvent);
                        scheduleNextEDArrival();
                        break;
                    case sortDeparture:
                    case registerDeparture:
                    case triageDeparture:
                        getStationByName(currentEvent.patient.currentStationName).departServiceStation(currentEvent);
                        break;
                    case reassessmentCheck:
                        runReassessmentCycle(currentEvent);
                        break;
                    case reassessmentDeparture:
                    case zoneDeparture:
                        getStationByName(currentEvent.patient.currentStationName).departServiceStation(currentEvent);
                        break;
                    case decideToLWBS:
                        currentEvent.patient.processLWBSDecision(this);
                        break;
                    default:
                        System.out.println("[Simulator-ERROR]: unknown event");
                }
            }
        }


    }

    public void scheduleNextEDArrival() {
        double interEDArrivalTime = Utils.getExp(getArrivalRateByTime(currentTime));
        double nextEDArrivalTime = currentTime + interEDArrivalTime;
        Patient newPatient = new Patient(totalArrivals);
        eventList.add(new Event(nextEDArrivalTime, Event.EventType.edArrival, newPatient));
        totalArrivals++;
        if (debug == 1) {
            System.out.println("\n[Simulator]: Next ED-AT: " + nextEDArrivalTime + "\n");
        }
    }

    // dynamic arrival time
    public static double getArrivalRateByTime(double currentTime) {
        if (!enableVariableEDArrivalRate) {
            return 10.0 / 60.0; 
        }
        int hour = (int) ((currentTime / 60.0) % 24);

        // scaled rush hour 1–5pm (13–16) for higher patient load
        return switch (hour) {
            case 0, 7 -> 5.0 / 60.0;
            case 1 -> 4.5 / 60.0;
            case 3 -> 3.0 / 60.0;
            case 2, 4, 5, 6 -> 4.0 / 60.0;
            case 8, 22 -> 7.0 / 60.0;
            case 9 -> 10.0 / 60.0;
            case 10 -> 13.0 / 60.0;
            case 11, 12, 13 -> 14.0 / 60.0; //11am-1pm
            case 14, 15, 16-> 13.0 / 60.0; // 2–4pm
            case 17 -> 12.0 / 60.0; //5pm
            case 18, 19 -> 11.0 / 60.0;
            case 20 -> 9.0 / 60.0;
            case 21 -> 8.0 / 60.0;
            case 23 -> 7.0 / 60.0;
            default -> 9.0 / 60.0;
        };
    }

    // dynamic staffing
    public void staff(double currentTime) {
        int hour = (int) ((currentTime / 60.0) % 24);

        // Update staff counts
       if (hour >= 0 && hour < 7) {
            greenZone.setStaffAvailable(2);
            redZone.setStaffAvailable(3);
            fastTrackZone.setStaffAvailable(1);
            eruZone.setStaffAvailable(1);
        } else if (hour >= 7 && hour < 15) {
            greenZone.setStaffAvailable(2);
            redZone.setStaffAvailable(4);
            fastTrackZone.setStaffAvailable(1);
            eruZone.setStaffAvailable(4);
        } else {
            greenZone.setStaffAvailable(2);
            redZone.setStaffAvailable(5);
            fastTrackZone.setStaffAvailable(1);
            eruZone.setStaffAvailable(2);
        }


        //attempt treatment w/ updated staff
        greenZone.attemptToStartTreatmentForAll(currentTime);
        redZone.attemptToStartTreatmentForAll(currentTime);
        fastTrackZone.attemptToStartTreatmentForAll(currentTime);
        eruZone.attemptToStartTreatmentForAll(currentTime);
        triage.attemptToStartTreatmentForAll(currentTime);
    }

    //reassessment
    public void runReassessmentCycle(Event reassessmentEvent) {
        Patient p = reassessmentEvent.patient;
        double currentTime = reassessmentEvent.eventTime;

        if (p == null) {
            //if patient is null, do nothing or handle global reassessment
            return;
        }

        Zone zone = getZoneByPatient(p);
        if (zone == null) {
            if (debug == 1) System.out.println("[Reassessment] Patient " + p.id + " not found in any zone.");
            return;
        }

        //only reassess if patient is still waiting in the queue (not admitted to bed)
        if (zone.queue.contains(p) || zone.waitingForStaff.contains(p)) {
            p.timeInQueueBeforeReassessment += (currentTime - zone.getPatientArrivalTime(p));

            zone.queue.remove(p);
            zone.waitingForStaff.remove(p);  // just in case


            reassessmentZone.addPatient(new Event(currentTime, Event.EventType.reassessmentCheck, p));


            if (debug == 1) {
                System.out.println("[Reassessment Triggered] Patient " + p.id  + "from zone " + zone.zoneName);
            }

        } else {
            if (debug == 1) {
                System.out.println("[Reassessment] Patient " + p.id + " no longer waiting, skipping reassessment.");
            }
        }
    }

    // method to find the zone the patient currently is waiting in:
    private Zone getZoneByPatient(Patient p) {
        List<Zone> zones = List.of(eruZone, redZone, greenZone, fastTrackZone);

        for (Zone z : zones) {
            if (z.queue.contains(p) || z.waitingForStaff.contains(p)) {
                return z;
            }
        }
        return null;
    }





    //used to get patients after warm up stage
    public void addDisposedPatient(Patient patient) {
        if (!patient.isCountedDisposed) {
            edDisposedPatients.add(patient);
            patient.isCountedDisposed = true;
            if (patient.zoneDT >= warmUpEndTime) {
                steadyStateDisposedPatients.add(patient);
            }
        }
    }


    public double getTotalDeaths(){
        return (double)Statistics.countDeaths(edDisposedPatients);
    }


    public double getTotalLWBS(){
        return (double)(getTotalLWBSPatients());
    }


    public double getLWBSRate() {
        if (totalArrivals == 0) return 0.0;
        return (getTotalLWBS() / (double) totalArrivals) * 100.0;
    }

    public ServiceStation getStationByName(Simulator.StationName stationName) {
        return switch (stationName) {
            case SORT -> sortNurse;
            case REGISTRATION -> registration;
            case TRIAGE -> triage;
            case REASSESSMENT -> reassessmentZone;
            case FAST_TRACK -> fastTrackZone;
            case GREEN -> greenZone;
            case RED -> redZone;
            case ERU -> eruZone;
            default -> throw new IllegalArgumentException("Unknown station name: " + stationName);
        };
    }

    public void printQuickStats() {
        System.out.println("========== ED SIMULATION SUMMARY ==========");
        System.out.printf("Days simulated: %d%n", (int) numDays);
        System.out.println("-------------------------------------------");
        System.out.printf("Total arrivals: %d%n", totalArrivals);
        System.out.printf("Avg arrivals per day: %.2f%n", totalArrivals / numDays);
        System.out.printf("Total patients disposed by ED: %d%n", edDisposedPatients.size());
        System.out.printf("Avg patients disposed per day: %.2f%n", edDisposedPatients.size() / numDays);
        System.out.printf("%% Disposed: %.2f%%%n", (edDisposedPatients.size() / (double) totalArrivals) * 100);

        System.out.println("-------------------------------------------");
        System.out.printf("Avg ED Door-to-Provider time: %s%n",
            Utils.formatMinsToHours(Statistics.calculateMean(this, Simulator.StationName.ED, Statistics.Property.DOOR_TO_PROVIDER_TIME)));
        System.out.printf("Avg ED LOS time: %s%n",
            Utils.formatMinsToHours(Statistics.calculateMean(this, Simulator.StationName.ED, Statistics.Property.RESPONSE_TIME)));


        System.out.println("-------------------------------------------");
        System.out.println("Misdiagnosed: " + triage.numMisdiagnosed);
        System.out.println("Misdiagnosis Rate: " + String.format("%.2f", (triage.numMisdiagnosed / (double) totalArrivals) * 100) + "%");
        System.out.println("Underdiagnosed: " + triage.numUnderDiagnosed);
        System.out.println("Overdiagnosed: " + triage.numOverDiagnosed);
        System.out.println("ESI 1 Misdiagnosed: " + triage.num1MD);
        System.out.println("ESI 2 Misdiagnosed: " + triage.num2MD);
        System.out.println("ESI 3 Misdiagnosed: " + triage.num3MD);
        System.out.println("ESI 4 Misdiagnosed: " + triage.num4MD);
        System.out.println("ESI 5 Misdiagnosed: " + triage.num5MD);

        System.out.println("-------------------------------------------");
        System.out.println("Number of patients reassessed: " + reassessmentZone.numReassessedPatients);
        System.out.println("Percent of misdiagnosed patients caught: " + String.format("%.2f", ((double)(reassessmentZone.numUptriaged + reassessmentZone.numDowntriaged)/triage.numMisdiagnosed) * 100) + "%");
        System.out.println("Number of patients up triaged: " +  reassessmentZone.numUptriaged);
        System.out.println("Number of patients down triaged: " + reassessmentZone.numDowntriaged);
        System.out.println("Number of patients w/ no change: " + reassessmentZone.numNoChange);
        System.out.println("Number of reassessed from ERU: " + reassessmentZone.numEruR);
        System.out.println("Number of reassessed from Red: " + reassessmentZone.numRedR);
        System.out.println("Number of reassessed from Green: " + reassessmentZone.numGreenR);
        System.out.println("Number of reassessed from Fast Track: " + reassessmentZone.numFTR);


        System.out.println("-------------------------------------------");
        int totalDeaths = Statistics.countDeaths(edDisposedPatients);
        System.out.printf("Total deaths: %d%n", totalDeaths);
        System.out.printf("Death Rate: %.2f%%%n", ((double) totalDeaths / totalArrivals) * 100.0);
        System.out.printf("Avg deaths per day: %.2f%n", totalDeaths / numDays);

        System.out.println("-------------------------------------------");
        double lwbs = getTotalLWBSPatients();
        System.out.printf("Total LWBS: %.0f%n", lwbs);
        System.out.printf("Avg LWBS per day: %.2f%n", lwbs / numDays);
        System.out.printf("%% LWBS: %.2f%%%n", (lwbs / totalArrivals) * 100);

        System.out.println("-------------------------------------------");
        double totalUnprocessedPatients = (
            sortNurse.queue.size() +
            registration.queue.size() +
            triage.queue.size() + getTotalPatientsInWaitingAreas()
        );
        System.out.printf("Total unprocessed patients in ED: %.0f%n", totalUnprocessedPatients);
        System.out.printf("Last event time: %.2f mins%n", currentTime);
        System.out.printf("Events unprocessed: %d%n", eventList.size());
        System.out.println("===========================================");
    }

    public void printQuickStats(Simulator.StationName stationName) {
        if(stationName == Simulator.StationName.ED) {
            this.printQuickStats();
            return;
        }
        ServiceStation station = getStationByName(stationName);
        if (station != null) {
            System.out.println("\n====== " + stationName + " STATION SUMMARY ======");
            station.printQuickStats();
            return;
        }
    }

    public void printQuickStats(Simulator.StationName[] stationNames) {
        for (Simulator.StationName stationName : stationNames) {
            printQuickStats(stationName);
        }
    }

    public int getTotalPatientsInWaitingAreas(){
        return eruZone.queue.size() + redZone.queue.size() + greenZone.queue.size() + fastTrackZone.queue.size() + triage.queue.size() + registration.queue.size() + sortNurse.queue.size();
    }

    public int getTotalLWBSPatients() {
        int totalLWBS = 0;
        for (ServiceStation station : List.of(sortNurse, registration, triage, eruZone, redZone, greenZone, fastTrackZone)) {
            if (station.lwbsPatients != null) {
                totalLWBS += station.lwbsPatients.size();
            }
        }
        return totalLWBS;
    }

    public void runForDays(int numDays) {
        this.numDays = numDays;
        begin();
    }

    public void printDisposedPatientsLWBSProb(int numPatients) {
        for (int i = 0; i < numPatients && i < edDisposedPatients.size(); i++) {
            Patient patient = edDisposedPatients.get(i);
            System.out.println("\n====== Patient " + patient.id + " Debug Info ======");
            patient.printDebugInfo();

        }
    }

    public static void main(String[] args) {
        Simulator sim = new Simulator();
        sim.runForDays(365);
        sim.printQuickStats(new Simulator.StationName[]{
            Simulator.StationName.ED,
                Simulator.StationName.REASSESSMENT,
                Simulator.StationName.SORT,
                Simulator.StationName.REGISTRATION,
              Simulator.StationName.TRIAGE,
            Simulator.StationName.FAST_TRACK,
            Simulator.StationName.RED,
            Simulator.StationName.GREEN,
            Simulator.StationName.ERU
        });
        // sim.printDisposedPatientsLWBSProb(10);
    }
}
