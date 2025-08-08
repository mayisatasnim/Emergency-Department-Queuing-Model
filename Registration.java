public class Registration extends ServiceStation {

    public Registration(Simulator simulator) {
        super(Simulator.StationName.REGISTRATION, 8.0, 3.0, 1, simulator);
    }

    @Override
    protected void setPatientArrivalTime(Patient patient, double time) {
        patient.registrationAT = time;
    }

    @Override
    protected void setPatientDepartureTime(Patient patient, double time) {
        patient.registrationDT = time;
    }

    @Override
    protected void setPatientProcessingTime(Patient patient, double time) {
        patient.registrationPT = time;
    }

    @Override
    protected Event.EventType getDepartureEventType() {
        return Event.EventType.registerDeparture;
    }

    @Override
    protected void sendToAppropriateNextStation(Event currentEvent) {
        simulator.triage.addPatient(currentEvent);
    }

    @Override
    protected double getPatientArrivalTime(Patient patient) {
        return patient.registrationAT;
    }

    @Override
    public ServiceStation getPrecedingStation() {
        return simulator.sortNurse;
    }
}
