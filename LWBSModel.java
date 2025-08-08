import java.util.HashMap;
import java.util.Map;

public class LWBSModel {
    private static LWBSModel instance;
    private final double lwbsBaseRate = 0.07; // 7% baseline LWBS rate
    private final Map<String, Double> oddsRatios;
    private final Map<String, Double> coefficients;

    // Singleton pattern for efficiency
    private LWBSModel() {
        this.oddsRatios = initializeOddsRatios();
        this.coefficients = computeLogOdds();
    }

    public static LWBSModel getInstance() {
        if (instance == null) {
            instance = new LWBSModel();
        }
        return instance;
    }

    /**
     * Initialize predictor's (Odds Ratios) based on research
     */
    private Map<String, Double> initializeOddsRatios() {
        Map<String, Double> predictors = new HashMap<>();

        // Patient demographics
        predictors.put("age", 0.98);
        predictors.put("acuity_low", 2.02);
        predictors.put("arrivalMode_EMS", 0.29);

        // System load factors
        predictors.put("arrivalRatePerHour", 1.03);
        predictors.put("hostQueueSize", 1.05);
        predictors.put("boardersCount", 1.02);

        // Time of arrival (reference: 12AM-6AM = 1.0)
        predictors.put("hour_12AM_6AM", 1.0);
        predictors.put("hour_6AM_12PM", 0.28);
        predictors.put("hour_12PM_6PM", 0.40);
        predictors.put("hour_6PM_12AM", 0.56);

        // Door-to-provider time (reference: <= 30 mins = 1.0)
        predictors.put("dtp_lessEq_30", 1.00);
        predictors.put("dtp_30_59", 1.34);
        predictors.put("dtp_60_89", 1.69);
        predictors.put("dtp_90_119", 1.87);
        predictors.put("dtp_120plus", 1.99);

        return predictors;
    }

    /**
     * Convert odds ratios to log odds (β coefficients)
     */
    private Map<String, Double> computeLogOdds() {
        Map<String, Double> logCoeffs = new HashMap<>();
        for (Map.Entry<String, Double> entry : oddsRatios.entrySet()) {
            logCoeffs.put(entry.getKey(), Math.log(entry.getValue()));
        }
        return logCoeffs;
    }

    /**
     * Calculate LWBS probability for a specific patient with dynamic predictors
     */
    public double predictLWBSProbability(
            Patient patient, 
            int hostQueueLength,
            double currentArrivalRate,
            double doorToProviderTime,
            int currentHour
        ) {

        Map<String, Double> predictorValues = buildPatientPredictors(
            patient, 
            hostQueueLength, 
            currentArrivalRate,
            doorToProviderTime, 
            currentHour
        );

        double probability = computeProbability(predictorValues);
        
        return probability;
    }

    /**
     * Build predictor values dynamically based on patient and system state
     */
    private Map<String, Double> buildPatientPredictors(
            Patient patient, 
            int hostQueueSize,
            double arrivalRate,
            double doorToProviderTime, 
            int currentHour
        ) {
        Map<String, Double> predictors = new HashMap<>();

        // Patient-specific predictors
        predictors.put("age", (double) patient.age);
        predictors.put("acuity_low", patient.acuity.equals("Low") ? 1.0 : 0.0);
        predictors.put("arrivalMode_EMS", patient.arrivalMode.equals("Ambulance") ? 1.0 : 0.0);

        // System state predictors
        predictors.put("arrivalRatePerHour", arrivalRate);
        predictors.put("hostQueueSize", (double) hostQueueSize);
        // Time-based predictors (one-hot encoded)
        setHourPredictors(predictors, currentHour);

        // Door-to-provider time predictors (one-hot encoded)
        setDoorToProviderPredictors(predictors, doorToProviderTime);

        return predictors;
    }

    /**
     * Set hour-based predictor variables (one-hot encoding)
     */
    private void setHourPredictors(Map<String, Double> predictors, int hour) {
        // Reset all hour predictors
        predictors.put("hour_12AM_6AM", 0.0);
        predictors.put("hour_6AM_12PM", 0.0);
        predictors.put("hour_12PM_6PM", 0.0);
        predictors.put("hour_6PM_12AM", 0.0);

        // Set the appropriate hour category
        if (hour >= 0 && hour < 6) {
            predictors.put("hour_12AM_6AM", 1.0);
        } else if (hour >= 6 && hour < 12) {
            predictors.put("hour_6AM_12PM", 1.0);
        } else if (hour >= 12 && hour < 18) {
            predictors.put("hour_12PM_6PM", 1.0);
        } else {
            predictors.put("hour_6PM_12AM", 1.0);
        }
    }

    /**
     * Set door-to-provider time predictor variables (one-hot encoding)
     */
    private void setDoorToProviderPredictors(Map<String, Double> predictors, double dtpMinutes) {
        // Reset all DTP predictors
        predictors.put("dtp_lessEq_30", 0.0);
        predictors.put("dtp_30_59", 0.0);
        predictors.put("dtp_60_89", 0.0);
        predictors.put("dtp_90_119", 0.0);
        predictors.put("dtp_120plus", 0.0);

        // Set the appropriate DTP category
        if (dtpMinutes <= 30) {
            predictors.put("dtp_lessEq_30", 1.0);
        } else if (dtpMinutes <= 59) {
            predictors.put("dtp_30_59", 1.0);
        } else if (dtpMinutes <= 89) {
            predictors.put("dtp_60_89", 1.0);
        } else if (dtpMinutes <= 119) {
            predictors.put("dtp_90_119", 1.0);
        } else {
            predictors.put("dtp_120plus", 1.0);
        }
    }

    /**
     * Compute LWBS probability using logistic regression
     */
    private double computeProbability(Map<String, Double> patientPredictorValues) {
        if (coefficients.isEmpty()) {
            throw new IllegalArgumentException("Log coefficients cannot be empty.");
        }
        if (patientPredictorValues.isEmpty()) {
            throw new IllegalArgumentException("Patient predictor values cannot be empty.");
        }

        // Calculate intercept (log-odds of baseline probability)
        double intercept = Math.log(lwbsBaseRate / (1 - lwbsBaseRate));
        double z = intercept;

        // Calculate z = intercept + Σ(βi * xi)
        for (Map.Entry<String, Double> entry : coefficients.entrySet()) {
            String predictor = entry.getKey();
            double logBeta = entry.getValue();
            double xi = patientPredictorValues.getOrDefault(predictor, 0.0);
            z += logBeta * xi;
        }

        // Return probability P(LWBS=1|X) = 1 / (1 + e^(-z))
        return 1.0 / (1.0 + Math.exp(-z));
    }

    /**
     * Debug method to see detailed calculation
     */
    public void debugPrediction(
            Patient patient, 
            int hostQueueLength,
            double currentArrivalRate,
            double doorToProviderTime,
            int currentHour
        ) {
        
        Map<String, Double> predictorValues = buildPatientPredictors(
            patient, hostQueueLength, currentArrivalRate, doorToProviderTime, currentHour
        );
        
        System.out.println("=== LWBS DEBUG ===");
        System.out.println("Patient ESI: " + patient.ESILevel + ", Age: " + patient.age);
        System.out.println("Door-to-provider time: " + doorToProviderTime + " minutes");
        
        double intercept = Math.log(lwbsBaseRate / (1 - lwbsBaseRate));
        double z = intercept;
        System.out.println("Intercept: " + intercept);
        
        for (Map.Entry<String, Double> entry : coefficients.entrySet()) {
            String predictor = entry.getKey();
            double logBeta = entry.getValue();
            double xi = predictorValues.getOrDefault(predictor, 0.0);
            if (xi > 0) {
                System.out.println(predictor + ": " + xi + " * " + logBeta + " = " + (xi * logBeta));
                z += logBeta * xi;
            }
        }
        
        System.out.println("Total z: " + z);
        double probability = 1.0 / (1.0 + Math.exp(-z));
        System.out.println("Final probability: " + probability);
        System.out.println("==================");
    }

}
