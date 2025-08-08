public class SortNurse extends ServiceStation {

    public SortNurse(Simulator simulator) {
        super(Simulator.StationName.SORT, 4.0, 2.0, 1, simulator);
    }

    @Override
    protected void setPatientArrivalTime(Patient patient, double time) {
        patient.sortingAT = time;
    }

    @Override
    protected void setPatientDepartureTime(Patient patient, double time) {
        patient.sortingDT = time;
    }

    @Override
    protected void setPatientProcessingTime(Patient patient, double time) {
        patient.sortingPT = time;
    }

    @Override
    protected Event.EventType getDepartureEventType() {
        return Event.EventType.sortDeparture;
    }

    @Override
    protected void sendToAppropriateNextStation(Event currentEvent) {
        double esi = currentEvent.patient.ESILevel;
        double rand = Math.random();

        Patient p = currentEvent.patient;

        if (esi == 1 && rand < 0.95) {
            simulator.eruZone.addPatient(currentEvent);
            p.originalZoneAssigned = stationName.ERU;
        } else if (esi == 2 && rand < 0.10) {
            simulator.eruZone.addPatient(currentEvent);
            p.originalZoneAssigned = stationName.ERU;
        } else {
            simulator.registration.addPatient(currentEvent);
        }
    }

    @Override
    protected double getPatientArrivalTime(Patient patient) {
        return patient.sortingAT;
    }

    @Override
    public ServiceStation getPrecedingStation() {
        return null;
    }
}
