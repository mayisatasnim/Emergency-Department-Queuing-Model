import java.util.*;

public class WaitingArea extends TreeSet<Patient> {

    public enum PrioritizationPolicy {
        HIGHER_ACUITY_FIRST, EARLY_ARRIVAL_FIRST
    }

    public WaitingArea(PrioritizationPolicy policy) {
        super(getComparator(policy));
    }

    private static Comparator<Patient> getComparator(PrioritizationPolicy policy) {
        return switch (policy) {
            case HIGHER_ACUITY_FIRST -> Comparator
                    .comparing((Patient p) -> p.assignedESI)
                    .thenComparing((Patient p) -> p.sortingAT);
            case EARLY_ARRIVAL_FIRST -> Comparator
                    .comparing((Patient p) -> p.sortingAT)
                    .thenComparing((Patient p) -> p.assignedESI);
        };
    }

    public void reTriage() {
        super.forEach(p -> {
            // reTriageLogic: ie: p.acuity = p.inferAcuity(p.ESILevel);
        });
    }

    // get patient with higher priority
    public Patient poll() {
        return super.pollFirst();
    }

    public void changePolicy(PrioritizationPolicy newPolicy) {
        Comparator<Patient> newComp = getComparator(newPolicy);
        TreeSet<Patient> reordered = new TreeSet<>(newComp);
        reordered.addAll(this);
        this.clear();
        this.addAll(reordered);
    }

    //iterate over patients
    public List<Patient> getAllPatients() {
        return new ArrayList<>(this);
    }


    // test 
    public static void main(String[] args) {
        WaitingArea wa = new WaitingArea(PrioritizationPolicy.EARLY_ARRIVAL_FIRST);
        // add five patients for testing
        for (int i = 1; i <= 5; i++) {
            Patient p = new Patient(i);
            wa.add(p);
            p.sortingAT = i;
        }

        // print patients in waiting area
        while (!wa.isEmpty()) {
            Patient pa = wa.pollFirst();
            System.out.println("Patient ID: " + pa.id + ", ESI: " + pa.ESILevel + ", Arrival Time: " + pa.sortingAT);
        }
    }
}

