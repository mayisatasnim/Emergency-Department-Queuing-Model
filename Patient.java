public class Patient {
    static boolean debug = false;

    //identifier information
    public String id;
    public int age;
    public int ESILevel; 
    public String arrivalMode;
    public String acuity;

    //lwbs
    public boolean hasLWBS;
    public double LWBSProbability;
    public double LWBSTime;
    public boolean hasScheduledLWBSCheck = false; // to avoid multiple LWBS checks


    public static double lwbsToughnessFactor = 0.1;

    //death
    public boolean died;
    public double deathTime;
    public boolean isCountedDisposed;

    //misdiagnosis
    public boolean wasMisdiagnosed = false;
    public boolean underDiagnosed = false;
    public boolean overDiagnosed = false;


    public double assignedESI;
    public Simulator.StationName originalZoneAssigned;


    //reassessment
    public boolean reassessed = false;
    public double timeInQueueBeforeReassessment;



    //patient diagnosis complexity
    public double complexity = Math.random();
    public int misdiagnosisDelta = 0;

    // Station timing
    public double sortingAT;
    public double sortingPT;
    public double sortingDT;
    public double registrationAT;
    public double registrationPT;
    public double registrationDT;
    public double triageAT;
    public double triagePT;
    public double triageDT;
    public double zoneAT;
    public double zonePT;
    public double zoneDT;
    public int regNo;
    public double edDepartureTime = 0.0;



    //reassessment times
    public double reassessmentAT;
    public double reassessmentPT;

    public double reassessmentDT;
    public Event reassessmentEvent = null;

    public Simulator.StationName currentStationName;

    public Patient(int regNo) {
        // Initialize all fields
        this.id = "P_" + regNo; 
        this.regNo = regNo;
        this.age = inferAge();
        this.arrivalMode = inferArrivalMode();
        this.ESILevel = inferESILevel();
        this.acuity = inferAcuity(this.ESILevel);
        this.hasLWBS = false;
        this.LWBSProbability = 0.0;
        this.LWBSTime = 0.0;
        this.died = false;
        this.deathTime = -1;
        this.isCountedDisposed = false;
        currentStationName = Simulator.StationName.NONE; // Default station name

        if(debug) {
            printDebugInfo();
        }
    }


    public void printDebugInfo() {
        System.out.println(this.id + "-arrivalMode: " + arrivalMode);
        System.out.println(this.id + "-ESILevel: " + ESILevel);
        System.out.println(this.id + "-acuity: " + acuity);
        System.out.println(this.id + "-age: " + age);
        System.out.println(this.id + "-LWBS prob: " + LWBSProbability);
        System.out.println(this.id + "-HostStation: " + currentStationName);
    }

    public String inferArrivalMode(){
        String patientType;
        double r = Math.random();
        if (r < 0.82) patientType = "Walk-in";
        else patientType = "Ambulance";

        return patientType;
    }

    public int inferESILevel() {
        double r = Math.random();

        if (r < 0.01) return 1;              // 1.0%
        else if (r < 0.01 + 0.294) return 2;   // +29.4%
        else if (r < 0.01 + 0.294 + 0.488) return 3; // +48.8%
        else if (r < 0.01 + 0.294 + 0.488 + 0.173) return 4; // +17.3%
        else return 5; // 2.2%
    }


    public String inferAcuity(int ESILevel){
        switch(ESILevel){
            case 1: return "High";
            case 2: return "Moderate";
            case 3: return "Moderate";
            case 4: return "Low";
            case 5: return "Low";
            default: return "Unknown";
        }
    }

    public void computeLWBSProbability(
            int hostQueueSize,
            double arrivalRate,
            double doorToProviderTime,
            int currentHour
    ) {
        LWBSModel model = LWBSModel.getInstance();
        this.LWBSProbability = model.predictLWBSProbability(
                this, hostQueueSize, arrivalRate,
                doorToProviderTime, currentHour
        );
    }

    public int inferAge() {
        double r = Math.random();

        // Based on typical ED age distribution
        if (r < 0.20) return 18 + (int)(Math.random() * 27); // 18-44 (20%)
        else if (r < 0.40) return 45 + (int)(Math.random() * 20); // 45-64 (20%)
        else if (r < 0.70) return 65 + (int)(Math.random() * 15); // 65-79 (30%)
        else if (r < 0.90) return 80 + (int)(Math.random() * 10); // 80-89 (20%)
        else return 0 + (int)(Math.random() * 18); // 0-17 (10%)
    }


    public void processLWBSDecision(Simulator simulator) {
        double currentTime = simulator.currentTime;

        // Exit immediately if the patient has already been disposed
        if (this.isCountedDisposed) {
            return;
        }

        ServiceStation station = simulator.getStationByName(currentStationName);
        double doorToProviderTime = currentTime - this.sortingAT;
        int currentHour = Utils.getDayTimeFromMins(currentTime);
        double arrivalRate = Simulator.getArrivalRateByTime(currentHour);

        // 1. Calculate the base probability
        this.computeLWBSProbability(simulator.getTotalPatientsInWaitingAreas(), arrivalRate, doorToProviderTime, currentHour);
        double baseProbability = this.LWBSProbability;

        // 2. Determine an acuity modifier
        double acuityModifier = 1.0;
        switch (this.ESILevel) {
            case 1: acuityModifier = 0.01; break;
            case 2: acuityModifier = 0.10; break;
            case 3: acuityModifier = 0.60; break;
        }

        double finalProbability = baseProbability * acuityModifier * lwbsToughnessFactor;

        if (Math.random() < finalProbability) {
            this.hasLWBS = true;
            this.LWBSTime = currentTime;
            station.lwbsPatients.add(this);

            if (station.queue.contains(this)) {
                station.queue.remove(this);
            } else if (station instanceof Zone && ((Zone) station).waitingForStaff.contains(this)) {
                ((Zone) station).waitingForStaff.remove(this);
            } else if (station instanceof Triage && ((Triage) station).waitingForStaff.contains(this)) {
                ((Triage) station).waitingForStaff.remove(this);
            } else if (station instanceof ReassessmentZone && ((ReassessmentZone) station).waitingForStaff.contains(this)){
                ((ReassessmentZone) station).waitingForStaff.remove(this);
            }

            simulator.addDisposedPatient(this);
            if (!this.isCountedDisposed) {
                isCountedDisposed = true; // prevent recheck after Disposition
            }

        } else {
            hasScheduledLWBSCheck = false;
            this.scheduleDecideToLWBS(simulator);
        }
    }

    public boolean hasLWBS() {
        return hasLWBS;
    }
    public void scheduleDecideToLWBS(Simulator simulator) {
        if (!this.hasScheduledLWBSCheck) {
            this.hasScheduledLWBSCheck = true; // prevent multiple scheduling
            simulator.eventList.add(new Event(simulator.currentTime + simulator.lwbsReevaluationPeriod, Event.EventType.decideToLWBS, this));
        }
    }

    public double getDoorToProviderTime() {
        if(!(zonePT == 0.0 || hasLWBS() || died)) { // exclude LWBS patients
            return this.zonePT - this.sortingAT;
        }

        return 0.0; // Return 0 for LWBS patients
    }

//    public double getEDResponseTime(){
//        if(!(zoneDT == 0.0 || hasLWBS() || died)) { // exclude LWBS patients
//            //return this.zoneDT - this.sortingAT;
//            return this.edDepartureTime - this.sortingAT;
//
//        }
//
//        return 0.0; // Return 0 for LWBS patients
//    }

    public double getEDResponseTime(){
        // if the patient left without being seen
        if (hasLWBS()) {
            return this.LWBSTime - this.sortingAT;
        }
        // if the patient was successfully treated
        if(!(zoneDT == 0.0 || died)) {
            // Add the lost time back into the calculation
            return (this.edDepartureTime - this.sortingAT) + this.timeInQueueBeforeReassessment;
        }

        return 0.0; // Return 0 for other cases
    }

    public static void main(String[]args){
        // Test both modes
        System.out.println("=== Testing CLASS_LEVEL_DEBUG = true ===");
        Patient.debug = true;
        boolean debugLWBS = true;
        Patient pt = new Patient(1);

        if(debugLWBS) {
            // Simulate different system states
            System.out.println("\n=== LWBS Probability Under Different Conditions ===");
            // Normal conditions
            pt.computeLWBSProbability(20,  15.0, 45.0, 10); // 10 AM, normal load
            System.out.println("Normal conditions (10 AM, low load): " + String.format("%.3f", pt.LWBSProbability));        
            // Busy evening
            pt.computeLWBSProbability(20,  15.0, 45.0, 10); // 10 AM, normal load
            System.out.println("Busy evening (8 PM, high load): " + String.format("%.3f", pt.LWBSProbability));     
            // Very congested
            pt.computeLWBSProbability(60,  18.0, 35.0, 22); // 10 PM, very high load
            System.out.println("Very congested (10 PM, very high load): " + String.format("%.3f", pt.LWBSProbability));
        }
    }
}