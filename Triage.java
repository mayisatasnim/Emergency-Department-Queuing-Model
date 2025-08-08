import java.util.LinkedList;
import java.util.Queue;

public class Triage extends ServiceStation {

    // --- Zone-like properties for bed and staff management ---
    protected Queue<Patient> waitingForStaff;
    protected int maxStaffAvailable;
    protected int activeTreatments = 0;

    // --- Misdiagnosis properties ---
    public boolean misdiagnosis = BatchRunner.misdiagnosis;
    public double stdDevMultiplier = BatchRunner.stdDevMultiplier;
    int numMisdiagnosed;
    int numUnderDiagnosed;
    int numOverDiagnosed;
    int num1MD;
    int num2MD;
    int num3MD;
    int num4MD;
    int num5MD;
    public Queue<Patient> misdiagnosedPatients = new LinkedList<>();

    public Triage(Simulator simulator) {
        // super(stationName, meanServiceTime, serviceStdDev, numBeds, simulator)
        super(Simulator.StationName.TRIAGE, 5.0, 2.0, 3, simulator);
        this.waitingForStaff = new LinkedList<>();
        this.setStaffAvailable(3);

        // Initialize misdiagnosis counters
        this.numMisdiagnosed = 0;
        this.numUnderDiagnosed = 0;
        this.numOverDiagnosed = 0;
        this.num1MD = 0;
        this.num2MD = 0;
        this.num3MD = 0;
        this.num4MD = 0;
        this.num5MD = 0;
    }

    public void setStaffAvailable(int staffCount) {
        this.maxStaffAvailable = staffCount;
    }

    public int getMaxStaffAvailable() {
        return this.maxStaffAvailable;
    }

    // --- Overridden abstract methods from ServiceStation ---

    @Override
    protected void setPatientArrivalTime(Patient p, double t) {
        p.triageAT = t;
    }

    @Override
    protected void setPatientProcessingTime(Patient p, double t) {
        p.triagePT = t;
    }

    @Override
    protected void setPatientDepartureTime(Patient p, double t) {
        p.triageDT = t;
    }

    @Override
    protected Event.EventType getDepartureEventType() {
        return Event.EventType.triageDeparture;
    }

    @Override
    protected double getPatientArrivalTime(Patient patient) {
        return patient.triageAT;
    }

    @Override
    public ServiceStation getPrecedingStation() {
        return simulator.registration;
    }

    // --- Patient processing logic adapted from Zone ---

    @Override
    public void addPatient(Event currentEvent) {

            Patient patient = currentEvent.patient;
            double currentTime = currentEvent.eventTime;

            setPatientArrivalTime(patient, currentTime);
            arrivedPatients.add(patient);
            totalArrivals++;
            updatePatientLocation(patient);
            patient.scheduleDecideToLWBS(simulator);
            setPatientDepartureTime(patient, Double.POSITIVE_INFINITY);

            // Admit to a triage bay if one is available
            if (busyBeds < numBeds) {
                busyBeds++;
                waitingForStaff.add(patient);
                attemptToStartTreatmentForAll(currentTime);
            } else {
                // Add to queue if all bays are full
                queue.add(patient);
            }


    }

    private void attemptToStartTreatment(Patient patient, double currentTime) {
        // Check if a nurse is available
        if (activeTreatments < maxStaffAvailable) {
            waitingForStaff.remove(patient);
            setPatientProcessingTime(patient, currentTime);

            double serviceTime = Utils.getNormal(meanServiceTime, serviceStdDev);
            double nextDeparture = currentTime + serviceTime;
            eventList.add(new Event(nextDeparture, getDepartureEventType(), patient));
            activeTreatments++;
        }
    }

    public void attemptToStartTreatmentForAll(double currentTime) {
        while (!waitingForStaff.isEmpty() && activeTreatments < maxStaffAvailable) {
            Patient next = waitingForStaff.poll();
            attemptToStartTreatment(next, currentTime);
        }
    }

    @Override
    public void departServiceStation(Event currentEvent) {
        Patient patient = currentEvent.patient;
        double currentTime = currentEvent.eventTime;

        setPatientDepartureTime(patient, currentTime);
        departedPatients.add(patient);

        // A nurse and a bay become free
        activeTreatments--;
        busyBeds--;

        sendToAppropriateNextStation(currentEvent);

        // If patients are waiting for a bay, move one in
        if (!queue.isEmpty()) {
            Patient next = queue.poll();
            busyBeds++;
            waitingForStaff.add(next);
        }

        // Try to start service for any patient waiting for a nurse
        attemptToStartTreatmentForAll(currentTime);
    }

    // --- Misdiagnosis and Routing Logic ---

    @Override
    protected void sendToAppropriateNextStation(Event currentEvent) {
        Patient patient = currentEvent.patient;
        int hour = (int) ((currentEvent.eventTime / 60.0) % 24);
        double routingESI;

        if (misdiagnosis) {
            double misdiagnosisRate = switch (hour) {
                case 0, 1, 2, 3, 4, 5 -> 0.05;
                case 6, 7, 8, 9 -> 0.1;
                case 10, 11, 12, 13, 14, 15, 16 -> 0.15;
                case 17, 18, 19 -> 0.1;
                default -> 0.07;
            };
            diagnose(patient, misdiagnosisRate);
            routingESI = patient.assignedESI;
        } else {
            routingESI = patient.ESILevel;
            patient.assignedESI = routingESI;
        }

        Zone targetZone;
        if (routingESI == 1) {
            targetZone = simulator.eruZone;
        } else if (routingESI == 2) {
            targetZone = simulator.redZone;
        } else if (routingESI == 3) {
            targetZone = (Math.random() < 0.33) ? simulator.redZone : simulator.greenZone;
        } else if (routingESI == 4) {
            targetZone = (Math.random() < 0.2) ? simulator.greenZone : simulator.fastTrackZone;
        } else {
            targetZone = simulator.fastTrackZone;
        }

        targetZone.queuePatientFromAnotherStation(currentEvent);
        patient.originalZoneAssigned = targetZone.stationName;
    }

    public void diagnose(Patient patient, double baseMisdiagnosisRate) {
        int trueESI = patient.ESILevel;
        int routingESI = trueESI;

        double patientComplexity = patient.complexity;
        double overloadFactor = (double) this.queue.size() / (getMaxStaffAvailable() + 1);
        overloadFactor = Math.min(overloadFactor, 3.0);

        double misdiagnosisRate = baseMisdiagnosisRate * (1.0 + 0.2 * overloadFactor) * (1.0 + 0.5) * stdDevMultiplier;

        if (trueESI == 1) {
            double rareMistake = 0.01 * (1.0 + 0.5 * patientComplexity);
            if (Math.random() < rareMistake) {
                routingESI = Math.random() < 0.7 ? 2 : 3;
                patient.wasMisdiagnosed = true;
                patient.misdiagnosisDelta = routingESI - trueESI;
                num1MD++;
                numUnderDiagnosed++;
                patient.underDiagnosed = true;
            }
        } else if (Math.random() < misdiagnosisRate) {
            double bias = 0.0;
            switch (trueESI) {
                case 2 -> bias = (Math.random() < 0.05) ? -1.0 : 1.0;
                case 3 -> bias = (Math.random() < 0.4) ? -1.0 : 1.0;
                case 4 -> bias = (Math.random() < 0.3) ? -1.0 : 1.0;
                case 5 -> bias = (Math.random() < 0.6) ? -1.0 : -2.0;
            }

            double stdDev = switch (trueESI) {
                case 2 -> 1.0;
                case 3 -> 1.2;
                default -> 1.0;
            };

            double diagnosedESI = Utils.getNormal(trueESI + bias, stdDev * stdDevMultiplier);
            int finalDiagnosedESI = (int) Math.round(Math.max(1.0, Math.min(5.0, diagnosedESI)));

            if (trueESI >= 3 && finalDiagnosedESI < 2) {
                finalDiagnosedESI = 2;
            }

            if (finalDiagnosedESI != trueESI) {
                numMisdiagnosed++;
                patient.wasMisdiagnosed = true;
                patient.misdiagnosisDelta = finalDiagnosedESI - trueESI;
                routingESI = finalDiagnosedESI;

                switch (trueESI) {
                    case 2 -> num2MD++;
                    case 3 -> num3MD++;
                    case 4 -> num4MD++;
                    case 5 -> num5MD++;
                }

                if (routingESI < trueESI) {
                    numUnderDiagnosed++;
                    patient.underDiagnosed = true;
                } else {
                    numOverDiagnosed++;
                    patient.overDiagnosed = true;
                }
            }
        }
        patient.assignedESI = routingESI;
    }
}