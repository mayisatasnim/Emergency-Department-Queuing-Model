
import java.util.*;

public class BatchRunner {


    public static double stdDevMultiplier = 0.5;
    public static boolean misdiagnosis = true;
    public static int reassessmentDelay = 120;
    public static boolean reassessmentEnabled = true;


    public static void main(String[] args) {
        //setup
        int runsPerScenario = 10;
        int simDays = 365;
        double[] stdDevMultipliers = {0.0, 0.5, 1.0, 1.5, 2.0};
        int[] reassessmentDelays = {0, 60, 120, 180, 240};

        //store data
        Map<String, Map<Double, Double>> edLosResults = new LinkedHashMap<>();
        Map<String, Map<Double, Double>> eruLosResults = new LinkedHashMap<>();
        Map<String, Map<Double, Double>> redLosResults = new LinkedHashMap<>();
        Map<String, Map<Double, Double>> greenLosResults = new LinkedHashMap<>();
        Map<String, Map<Double, Double>> fastTrackLosResults = new LinkedHashMap<>();
        Map<String, Map<Double, Double>> deathsResults = new LinkedHashMap<>();
        Map<String, Map<Double, Double>> lwbsResults = new LinkedHashMap<>();

        //experiment 1: baseline - no misdiagnosis at all
        System.out.println("Running Experiment 1: Baseline (No Misdiagnosis)...");
        String baselinePolicy = "Baseline";
        misdiagnosis = false;
        reassessmentEnabled = false;
        stdDevMultiplier = 0.0; // unused
        Map<String, Double> baselineResult = runBatch(runsPerScenario, simDays);
        // baseline results stored across all stdDev columns for easy comparison
        for (double mult : stdDevMultipliers) {
            storeResult(edLosResults, baselinePolicy, mult, baselineResult.get("ED_LOS"));
            storeResult(eruLosResults, baselinePolicy, mult, baselineResult.get("ERU_LOS"));
            storeResult(redLosResults, baselinePolicy, mult, baselineResult.get("RED_LOS"));
            storeResult(greenLosResults, baselinePolicy, mult, baselineResult.get("GREEN_LOS"));
            storeResult(fastTrackLosResults, baselinePolicy, mult, baselineResult.get("FAST_TRACK_LOS"));
            storeResult(deathsResults, baselinePolicy, mult, baselineResult.get("DEATHS"));
            storeResult(lwbsResults, baselinePolicy, mult, baselineResult.get("LWBS_RATE"));
        }

        //experiment 2: misdiagnosis only - no reassessment
        System.out.println("\nRunning Experiment 2: Misdiagnosis Only...");
        String misdiagnosisPolicy = "Misdiagnosis Only";
        misdiagnosis = true;
        reassessmentEnabled = false;
        for (double mult : stdDevMultipliers) {
            stdDevMultiplier = mult;
            System.out.printf("  - Running with stdDevMultiplier = %.1f%n", mult);
            Map<String, Double> result = runBatch(runsPerScenario, simDays);
            storeResult(edLosResults, misdiagnosisPolicy, mult, result.get("ED_LOS"));
            storeResult(eruLosResults, misdiagnosisPolicy, mult, result.get("ERU_LOS"));
            storeResult(redLosResults, misdiagnosisPolicy, mult, result.get("RED_LOS"));
            storeResult(greenLosResults, misdiagnosisPolicy, mult, result.get("GREEN_LOS"));
            storeResult(fastTrackLosResults, misdiagnosisPolicy, mult, result.get("FAST_TRACK_LOS"));
            storeResult(deathsResults, misdiagnosisPolicy, mult, result.get("DEATHS"));
            storeResult(lwbsResults, misdiagnosisPolicy, mult, result.get("LWBS_RATE"));
        }

        //experiment 3: misdiagnosis with reassessment
        System.out.println("\nRunning Experiment 3: Misdiagnosis with Reassessment...");
        misdiagnosis = true;
        reassessmentEnabled = true;
        for (int delay : reassessmentDelays) {
            reassessmentDelay = delay;
            String policyName = String.format("Reassessment (Delay=%d)", delay);
            System.out.printf("  - Running Policy: %s%n", policyName);
            for (double mult : stdDevMultipliers) {
                stdDevMultiplier = mult;
                System.out.printf("    - Running with stdDevMultiplier = %.1f%n", mult);
                Map<String, Double> result = runBatch(runsPerScenario, simDays);
                storeResult(edLosResults, policyName, mult, result.get("ED_LOS"));
                storeResult(eruLosResults, policyName, mult, result.get("ERU_LOS"));
                storeResult(redLosResults, policyName, mult, result.get("RED_LOS"));
                storeResult(greenLosResults, policyName, mult, result.get("GREEN_LOS"));
                storeResult(fastTrackLosResults, policyName, mult, result.get("FAST_TRACK_LOS"));
                storeResult(deathsResults, policyName, mult, result.get("DEATHS"));
                storeResult(lwbsResults, policyName, mult, result.get("LWBS_RATE"));
            }
        }

        //results
        System.out.println("\n\n All experiments complete...\n");
        exportResults("ED Overall LOS (mins)", edLosResults, stdDevMultipliers);
        exportResults("ERU LOS (mins)", eruLosResults, stdDevMultipliers);
        exportResults("RED Zone LOS (mins)", redLosResults, stdDevMultipliers);
        exportResults("GREEN Zone LOS (mins)", greenLosResults, stdDevMultipliers);
        exportResults("FAST TRACK LOS (mins)", fastTrackLosResults, stdDevMultipliers);
        exportResults("Total Deaths", deathsResults, stdDevMultipliers);
        exportResults("LWBS Rate (%)", lwbsResults, stdDevMultipliers);
    }

    //batch simulations
    private static Map<String, Double> runBatch(int runs, int simDays) {
        List<Double> totalLOSList = new ArrayList<>();
        List<Double> eruLOSList = new ArrayList<>();
        List<Double> redLOSList = new ArrayList<>();
        List<Double> greenLOSList = new ArrayList<>();
        List<Double> fastTrackLOSList = new ArrayList<>();
        List<Double> totalDeathsList = new ArrayList<>();
        List<Double> lwbsRateList = new ArrayList<>();

        for (int i = 0; i < runs; i++) {
            Simulator sim = new Simulator();
            sim.runForDays(simDays);

            totalLOSList.add(Statistics.calculateMean(sim, Simulator.StationName.ED, Statistics.Property.RESPONSE_TIME));
            eruLOSList.add(Statistics.calculateMean(sim, Simulator.StationName.ERU, Statistics.Property.RESPONSE_TIME));
            redLOSList.add(Statistics.calculateMean(sim, Simulator.StationName.RED, Statistics.Property.RESPONSE_TIME));
            greenLOSList.add(Statistics.calculateMean(sim, Simulator.StationName.GREEN, Statistics.Property.RESPONSE_TIME));
            fastTrackLOSList.add(Statistics.calculateMean(sim, Simulator.StationName.FAST_TRACK, Statistics.Property.RESPONSE_TIME));
            totalDeathsList.add((double) sim.getTotalDeaths());
            lwbsRateList.add(sim.getLWBSRate());
        }

        Map<String, Double> meanResults = new HashMap<>();
        meanResults.put("ED_LOS", computeMean(totalLOSList));
        meanResults.put("ERU_LOS", computeMean(eruLOSList));
        meanResults.put("RED_LOS", computeMean(redLOSList));
        meanResults.put("GREEN_LOS", computeMean(greenLOSList));
        meanResults.put("FAST_TRACK_LOS", computeMean(fastTrackLOSList));
        meanResults.put("DEATHS", computeMean(totalDeathsList));
        meanResults.put("LWBS_RATE", computeMean(lwbsRateList));

        return meanResults;
    }

    //store results
    private static void storeResult(Map<String, Map<Double, Double>> resultMap, String policy, double stdDevMult, double value) {
        resultMap.computeIfAbsent(policy, k -> new LinkedHashMap<>()).put(stdDevMult, value);
    }

    //print results
    private static void exportResults(String title, Map<String, Map<Double, Double>> results, double[] multipliers) {
        System.out.println("========== " + title + " ==========");
        StringJoiner header = new StringJoiner(",");
        header.add("Policy");
        for (double mult : multipliers) {
            header.add(String.format("%.1f", mult));
        }
        System.out.println(header);

        results.forEach((policy, data) -> {
            StringJoiner row = new StringJoiner(",");
            row.add(policy);
            for (double mult : multipliers) {
                row.add(String.format("%.2f", data.getOrDefault(mult, 0.0)));
            }
            System.out.println(row);
        });
        System.out.println();
    }


    public static double computeMean(List<Double> data) {
        if (data == null || data.isEmpty() || data.stream().allMatch(d -> d.isNaN() || d.isInfinite())) {
            return 0.0;
        }
        return data.stream().filter(d -> !d.isNaN() && !d.isInfinite()).mapToDouble(Double::doubleValue).average().orElse(0.0);
    }
}