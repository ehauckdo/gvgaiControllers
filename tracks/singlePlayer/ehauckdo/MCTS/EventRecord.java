package tracks.singlePlayer.ehauckdo.MCTS;

import java.util.ArrayList;

/**
 *
 * @author hauck
 */
public class EventRecord {

    double averageScoreChange;
    int activeTypeId;
    int passiveTypeId;
    ArrayList<Double> scoreChanges = new ArrayList<>();

    public EventRecord(int activeTypeId, int passiveTypeId, double scoreChange) {
        this.activeTypeId = activeTypeId;
        this.passiveTypeId = passiveTypeId;
        this.scoreChanges.add(scoreChange);
        this.averageScoreChange = scoreChange;
    }

    public EventRecord(int activeTypeId, int passiveTypeId, ArrayList<Double> scoreChanges) {
        this.activeTypeId = activeTypeId;
        this.passiveTypeId = passiveTypeId;
        this.averageScoreChange = 0;
        for (Double d : scoreChanges) {
            this.scoreChanges.add(d);
            this.averageScoreChange += d;
        }
        this.averageScoreChange /= (double) this.scoreChanges.size();
    }

    public void addOccurrence(double scoreChange) {
        this.scoreChanges.add(scoreChange);
        this.averageScoreChange = 0;
        for (Double d : this.scoreChanges) {
            this.averageScoreChange += d;
        }
        this.averageScoreChange /= (double) this.scoreChanges.size();

    }
    
    public int getOccurrences(){
        return scoreChanges.size();
    }
    
    public ArrayList<Double> getScores(){
        return scoreChanges;
    }

}
