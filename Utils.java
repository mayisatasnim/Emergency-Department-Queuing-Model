public abstract class Utils {
    public static double getNormal(double mean, double standardDeviation) {
        return mean + standardDeviation * new java.util.Random().nextGaussian();
    }
    public static double getExp(double rate){
        if(rate == 0) return Integer.MAX_VALUE;
        double u = Math.random();
        return (-1.0/rate)*Math.log(1-u);
    }

    public static int getDayTimeFromMins(double minutes) {
        return (int) ((minutes / 60) % 24);
    }

    public static String formatMinsToHours(double minutes) {
        int hours = (int) (minutes / 60.0);
        int mins = (int) (minutes % 60.0);
        if(hours > 0)
            return String.format("%02d h + %02d mins <=> %.2f mins", hours, mins, minutes);
        return String.format("%02d mins", mins);
    }
}
