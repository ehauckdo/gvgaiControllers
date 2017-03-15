package controllers.singlePlayer.ehauckdo;

/**
 *
 * @author hauck
 */
public class featureWeight {

    public double weight;
    public double distance;

    public featureWeight(double distance) {
        this.distance = distance;
        this.weight = 1;
    }
    
    public featureWeight(double distance, double weight) {
        this.distance = distance;
        this.weight = weight;
    }

}
