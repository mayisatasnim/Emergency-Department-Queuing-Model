import javax.swing.plaf.basic.BasicTableHeaderUI;
import java.util.*;

public class Zone extends ServiceStation {

    public int reassessmentDelay = BatchRunner.reassessmentDelay;

    public List<Patient> zoneDepartedPatients;
    private List<Patient> edDisposedPatients;
    protected Simulator.StationName zoneName;

    //beds and staff treatment handling
    protected  Queue<Patient> waitingForStaff;  // patients admitted to a bed but waiting for staff
    protected int maxStaffAvailable;
    protected int activeTreatments = 0;
    protected int busyBeds = 0; //occupied beds


    public Zone(Simulator.StationName zoneName, Simulator simulator) {
        super(zoneName, 4, 1.0, getZoneCapacity(zoneName), simulator);
        this.edDisposedPatients = simulator.edDisposedPatients;
        this.zoneName = zoneName;
        this.zoneDepartedPatients = this.departedPatients;
        this.waitingForStaff = new LinkedList<>();
    }

    private static int getZoneCapacity(Simulator.StationName zoneName) {
        switch (zoneName) {
            case ERU: return 14;
            case FAST_TRACK: return 19;
            case RED: return 34;
            case GREEN: return 10;
            default: return 1;
        }
    }

    @Override
    protected void setPatientArrivalTime(Patient patient, double time) {
        patient.zoneAT = time;
    }

    @Override
    protected void setPatientDepartureTime(Patient patient, double time) {
        patient.zoneDT = time;
    }

    @Override
    protected void setPatientProcessingTime(Patient patient, double time) {
        patient.zonePT = time;
    }

    @Override
    protected Event.EventType getDepartureEventType() {
        return Event.EventType.zoneDeparture;
    }
    @Override
    public ServiceStation getPrecedingStation() {
        return simulator.triage;
    }
    @Override
    protected void sendToAppropriateNextStation(Event currentEvent) {
        if (!currentEvent.patient.isCountedDisposed) {
            simulator.addDisposedPatient(currentEvent.patient);
        }
    }

    public void setStaffAvailable(int staffCount) {
        this.maxStaffAvailable = staffCount;
    }


    public void queuePatientFromAnotherStation(Event currentEvent) {
        Patient patient = currentEvent.patient;
        double currentTime = currentEvent.eventTime;

//        setPatientArrivalTime(patient, currentTime); // optional if Zone-specific time needed
//        patient.currentStationName = stationName;
//        totalArrivals++;
//        arrivedPatients.add(patient);

        // CRITICAL: Set arrival time BEFORE adding to TreeSet so sorting works correctly!
        setPatientArrivalTime(patient, currentEvent.eventTime);
        arrivedPatients.add(patient);
        totalArrivals++;
        updatePatientLocation(patient);
        patient.scheduleDecideToLWBS(simulator);
        setPatientDepartureTime(patient, Double.POSITIVE_INFINITY);

        if ((busyBeds < numBeds)) {
            busyBeds++;
            waitingForStaff.add(patient);
        } else queue.add(patient);


        if (BatchRunner.reassessmentEnabled && !patient.reassessed) {
          //  eventList.add(new Event(currentTime + reassessmentDelay, Event.EventType.reassessmentCheck, patient));

            Event reassessEvt = new Event(currentTime + reassessmentDelay, Event.EventType.reassessmentCheck, patient);
            eventList.add(reassessEvt);
            patient.reassessmentEvent = reassessEvt;
        }

        attemptToStartTreatmentForAll(currentTime);
    }

    @Override
    public void addPatient(Event currentEvent) {
        Patient patient = currentEvent.patient;
        double currentTime = currentEvent.eventTime;
//
//        setPatientArrivalTime(patient, currentTime);
//        patient.currentStationName = this.stationName;
//
//        totalArrivals++;
//        arrivedPatients.add(patient);


        // CRITICAL: Set arrival time BEFORE adding to TreeSet so sorting works correctly!
        setPatientArrivalTime(patient, currentEvent.eventTime);
        arrivedPatients.add(patient);
        totalArrivals++;
        updatePatientLocation(patient);
        patient.scheduleDecideToLWBS(simulator);
        setPatientDepartureTime(patient, Double.POSITIVE_INFINITY);

        // admit if a bed is available
        if (busyBeds < numBeds) {
            busyBeds++;
            waitingForStaff.add(patient);
            attemptToStartTreatment(patient, currentTime);
        } else queue.add(patient);

        // Schedule reassessment event for all patients who haven't been reassessed yet
        if (BatchRunner.reassessmentEnabled && !patient.reassessed ) {
           // eventList.add(new Event(currentTime + reassessmentDelay, Event.EventType.reassessmentCheck, patient));

            Event reassessEvt = new Event(currentTime + reassessmentDelay, Event.EventType.reassessmentCheck, patient);
            eventList.add(reassessEvt);
            patient.reassessmentEvent = reassessEvt;
        }

    }


    //start treatment
    private void attemptToStartTreatment(Patient patient, double currentTime) {
        if (activeTreatments < maxStaffAvailable) {

            if (patient.reassessmentEvent != null) {
                eventList.remove(patient.reassessmentEvent);  // cancel scheduled reassessment event
                patient.reassessmentEvent = null;
            }

            patient.hasScheduledLWBSCheck = false;


            //begin treatment
            waitingForStaff.remove(patient);
            setPatientProcessingTime(patient, currentTime);

            double serviceTime = Utils.getNormal(meanServiceTime, serviceStdDev);

            serviceTime *= misdiagnosisPenaltyFactor(patient);



            double nextDeparture = currentTime + serviceTime;
            eventList.add(new Event(nextDeparture, getDepartureEventType(), patient));
            activeTreatments++;

            if (debug == 1) {
                System.out.println("[" + stationName + "]: Started treatment for " + patient.id + " @T: " + currentTime + ", departs @T: " + nextDeparture);
            }
        }
    }

    private double misdiagnosisPenaltyFactor(Patient p) {
        if (!p.wasMisdiagnosed) return 1.0;

        double delta = Math.abs(p.misdiagnosisDelta);   // how many ESI levels off
        boolean under = p.underDiagnosed;

        double alpha = 0.30;          // base strength per level
        double beta  = 1.4;           // >1 = super‑linear growth with delta
        double cap   = 3.0;           // never inflate more than 3x, for stability
        double underMult = 1.6;       // under-dx costs more
        double overMult  = 1.2;       // over-dx still costs, but less

        double esiWeight = switch (p.ESILevel) {
            case 1 -> 1.3;
            case 2 -> 1.3;
            case 3 -> 1.2;
            default -> 1.0;
        };

        double misdxCore = 1.0 + alpha * Math.pow(delta, beta);
        double dirMult   = under ? underMult : overMult;

        double factor = misdxCore * dirMult * esiWeight;

        //if (p.reassessed) factor = 1.0 + (factor - 1.0) * 0.3; // keep 30% of the penalty

        if (p.reassessed) factor = 1.0;


        return Math.min(factor, cap);
    }



    //fill staff slots from queue
    public void attemptToStartTreatmentForAll(double currentTime) {
        while (!waitingForStaff.isEmpty() && activeTreatments < maxStaffAvailable) {
            Patient next = waitingForStaff.poll();
            attemptToStartTreatment(next, currentTime);
        }
    }

    // free bed + staff, and treat next
    @Override
    public void departServiceStation(Event currentEvent) {
        Patient patient = currentEvent.patient;
        double currentTime = currentEvent.eventTime;
        int ESI = patient.ESILevel;

        if (ESI >= 1 && ESI <= 3) {
            double baseRisk;
            if (ESI == 1) baseRisk = 0.02;
            else if (ESI == 2) baseRisk = 0.01;
            else baseRisk = 0.004;

            double timeInTreatment = currentTime - patient.zonePT;
            double waitBeforeTreatment = patient.zonePT - patient.zoneAT;

            // adjust for underdiagnosis
            if (patient.underDiagnosed) {
                if (ESI == 1) baseRisk *= 3.0;  // sharply increased
                else if (ESI == 2) baseRisk *= 1.75;
                else baseRisk *= 1.25;
            }

            //logistic decay (0–1) for time waiting before treatment
            double delayFactor = 1.0 / (1.0 + Math.exp(-(waitBeforeTreatment - 120.0) / 60.0)); // midpoint at 2 hrs

            //exponential decay for insufficient treatment
            double treatmentFactor = 1.0 - Math.exp(-timeInTreatment / 180.0); // 3 hours to 63%

            //adjust based on zone congestion (makes overloaded zones riskier)
            double congestionFactor = 1.0;
            if (busyBeds >= numBeds) congestionFactor += 0.25;
            if (activeTreatments >= maxStaffAvailable) congestionFactor += 0.25;

            double combinedRisk = baseRisk * (0.6 * treatmentFactor + 0.4 * delayFactor) * congestionFactor;

            if (Math.random() < combinedRisk) {
                patient.died = true;
                patient.deathTime = currentTime;
                activeTreatments--;
                busyBeds--;
                if (!patient.isCountedDisposed) simulator.addDisposedPatient(patient);
//                if (debug == 1) {
//                    System.out.printf("[Death] %d (ESI %d) died @%s @T: %.2f, risk: %.4f\n",
//                            patient.id, patient.ESILevel, stationName, currentTime, combinedRisk);
//                }
                return;
            }
        }



        setPatientDepartureTime(patient, currentTime);
        departedPatients.add(patient);

        activeTreatments--;
        busyBeds--;

        sendToAppropriateNextStation(currentEvent);

        if (patient.isCountedDisposed) {
            patient.edDepartureTime = currentTime;
        }

        // if patients are waiting for beds, move one into bed
        if (!queue.isEmpty()) {
            Patient next = queue.poll();
            busyBeds++;
            waitingForStaff.add(next);
            if (debug == 1) {
                System.out.println("[" + stationName + "] Patient " + next.id + " got bed after departure @T: " + currentTime);
            }
        }

        //try to fill any available staff slots
        attemptToStartTreatmentForAll(currentTime);
    }

    @Override
    protected double getPatientArrivalTime(Patient patient) {
        return patient.zoneAT;
    }

    public boolean isFull() {
        return (this.busyBeds >= this.getZoneCapacity(this.zoneName)) || (this.activeTreatments >= this.maxStaffAvailable);
    }

    public List<Patient> getPatientsWaitingTooLong(double currentTime) {
        List<Patient> result = new ArrayList<>();
        for (Patient p : queue) {
            if ((currentTime - p.zoneAT) > 240) {
                result.add(p);
            }
        }
        for (Patient p : waitingForStaff) {
            if ((currentTime - p.zoneAT) > 120) {
                result.add(p);
            }
        }
        return result;
    }


    public int countDeaths() {
        int deaths = 0;
        for (Patient p : edDisposedPatients) {
            if (p.died && p.currentStationName == this.zoneName) {
                deaths++;
            }
        }
        return deaths;
    }

    public int countLWBS() {
        int count = 0;
        for (Patient p : edDisposedPatients) {
            if (p.hasLWBS && p.currentStationName == this.zoneName) {
                count++;
            }
        }
        return count;
    }

    public void printQuickStats() {
        super.printQuickStats();
        System.out.println("Total deaths in zone: " + countDeaths());
        System.out.println("Avg deaths per day: " + (countDeaths() / (double) simulator.numDays));
        System.out.println("Total LWBS in zone: " + countLWBS());
        System.out.println("Avg LWBS per day: " + (countLWBS() / (double) simulator.numDays));
        System.out.println("Patients in bed waiting for staff: " + waitingForStaff.size());
        System.out.println("Active treatments: " + activeTreatments + "/" + maxStaffAvailable);
        System.out.println("Busy beds: " + busyBeds + "/" + numBeds);
    }
}
