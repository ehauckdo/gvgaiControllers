package controllers.tracks.singlePlayer.ehauckdo.MCTS;

import java.util.HashMap;

/**
 *
 * @author hauck
 */
public class KnowledgeBase {

    HashMap<Integer, EventRecord> events = new HashMap();

    public KnowledgeBase() {
    }
    
    public KnowledgeBase(KnowledgeBase kb) {
        if(kb != null){
            for(Integer id: events.keySet()){
                EventRecord er = kb.events.get(id);
                EventRecord new_er = new EventRecord(er.activeTypeId, er.passiveTypeId, er.scoreChanges);
                this.events.put(id, new_er);
            }
        }
    }
     
    public void add(int actTypeId, int pasTypeId, double scoreChange){
        EventRecord er = events.get(Util.getCantorPairingId(actTypeId, pasTypeId));
        if(er != null){
            er.addOccurrence(scoreChange);
        }
        else{
            er = new EventRecord(actTypeId, pasTypeId, scoreChange);
            events.put(Util.getCantorPairingId(actTypeId, pasTypeId), er);
        }
    }
    
    public void clear(){
        events.clear();
    }
    
    public int getOcurrences(int actTypeId, int pasTypeId){
        int key = Util.getCantorPairingId(actTypeId, pasTypeId);
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
    
    public double getAvgScoreChange(int key){
        EventRecord event = events.get(key);
        if(event == null)
            return 0;
        else return event.averageScoreChange;
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
    
}
