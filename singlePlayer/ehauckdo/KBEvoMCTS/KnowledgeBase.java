package controllers.singlePlayer.ehauckdo.KBEvoMCTS;

import core.game.GameDescription;
import java.util.ArrayList;
import java.util.HashMap;

/**
 *
 * @author hauck
 */
public class KnowledgeBase {

    HashMap<Integer, EventRecord> events = new HashMap();
     
    public void add(int actTypeId, int pasTypeId, double scoreChange){
        EventRecord er = events.get(getCantorPairingId(actTypeId, pasTypeId));
        if(er != null){
            er.addOccurrence(scoreChange);
        }
        else{
            er = new EventRecord(actTypeId, pasTypeId, scoreChange);
            events.put(getCantorPairingId(pasTypeId, pasTypeId), er);
        }
    }
    
    public void clear(){
        events.clear();
    }
    
    public void printKnowledgeBase(){
        for(Integer i: events.keySet()){
            System.out.println("Event ID: "+i);
            EventRecord er = events.get(i);
            System.out.println("Occurrences: "+er.scoreChanges.size());
            System.out.println("Active Type: "+er.activeTypeId+", Passive Type: "+er.passiveTypeId);
            System.out.println("Score Change: "+er.averageScoreChange);
        }
        System.out.println("");
    }
       
    public class EventRecord{
        int occurrences;
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
        
        public void addOccurrence(double scoreChange){
            this.scoreChanges.add(scoreChange);
            this.averageScoreChange = 0;
            for(Double d : this.scoreChanges){
                this.averageScoreChange += d;
            }
            this.averageScoreChange /= this.scoreChanges.size();
            
        }
        
    }
    
    private int getCantorPairingId(int a, int b){
        return (a + b) * (a + b + 1) / 2 + a;
    }
}
