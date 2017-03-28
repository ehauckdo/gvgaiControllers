package controllers.singlePlayer.ehauckdo.KBEvoMCTS;

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
            events.put(getCantorPairingId(actTypeId, pasTypeId), er);
        }
    }
    
    public void clear(){
        events.clear();
    }
    
    public int getOcurrences(int actTypeId, int pasTypeId){
        int key = getCantorPairingId(actTypeId, pasTypeId);
        EventRecord event = events.get(key);
        if(event == null)
            return 0;
        else return event.scoreChanges.size();
    }
    
    public int getOcurrences(int key){
        EventRecord event = events.get(key);
        if(event == null)
            return 0;
        else return event.scoreChanges.size();
    }
    
    public boolean equals(KnowledgeBase kb){
        events.keySet();
        if(events.keySet() != kb.events.keySet()){
            return false;
        }
        for(Integer i : events.keySet()){
           
        }
        return false;
    }
    
    public void printKnowledgeBase(){
        for(Integer i: events.keySet()){
            System.out.println("Event ID: "+i);
            EventRecord er = events.get(i);
            System.out.println("Occurrences: "+er.scoreChanges.size());
            System.out.println("Active Type: "+er.activeTypeId+", Passive Type: "+er.passiveTypeId);
            System.out.println("Average Score Change: "+er.averageScoreChange);
            System.out.println("Scores: ");
            for(Double score : er.scoreChanges){
                System.out.print(score+", ");
            }
        }
        System.out.println("");
    }
       
    public class EventRecord{
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
            this.averageScoreChange = scoreChange;
            for(Double d : this.scoreChanges){
                this.averageScoreChange += d;
            }   
            this.averageScoreChange /= (double) this.scoreChanges.size();
            
        }
        
    }
    
    private int getCantorPairingId(int a, int b){
        return (a + b) * (a + b + 1) / 2 + a;
    }
}
