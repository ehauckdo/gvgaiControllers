package controllers.singlePlayer.ehauckdo.KBEvoMCTS;

import core.game.GameDescription;
import java.util.HashMap;

/**
 *
 * @author hauck
 */
public class KnowledgeBase {

    HashMap<Integer, EventRecord> events = new HashMap();
     
    public void add(int actSpriteId, int pasSpriteId, double scoreChange){
        EventRecord er = events.get(getCantorPairingId(pasSpriteId, pasSpriteId));
        if(er != null){
            er.addOccurrence(scoreChange);
        }
        else{
            er = new EventRecord(actSpriteId, pasSpriteId, scoreChange);
            events.put(getCantorPairingId(pasSpriteId, pasSpriteId), er);
        }
    }
    
    public void clear(){
        events.clear();
    }
    
    public void printKnowledgeBase(){
        for(Integer i: events.keySet()){
            System.out.println("Event ID: "+i);
            EventRecord er = events.get(i);
            System.out.println("Occurrences: "+er.occurrences);
            System.out.println("Active Sprite: "+er.activeSpriteId+", Passive Sprite: "+er.passiveSpriteId);
            System.out.println("Score Change: "+er.totalScoreChange);
            //System.out.println("Passive Sprite: "+er.passiveSpriteId);
        }
        System.out.println("");
    }
       
    public class EventRecord{
        int occurrences;
        double scoreChange;
        double totalScoreChange;
        int activeSpriteId;
        int passiveSpriteId;

        public EventRecord(int activeSpriteId, int passiveSpriteId, double scoreChange) {
            this.occurrences = 1;
            this.activeSpriteId = activeSpriteId;
            this.passiveSpriteId = passiveSpriteId;
            this.scoreChange = scoreChange;
            this.totalScoreChange = scoreChange;
        }
        
        public void addOccurrence(double scoreChange){
            this.occurrences += 1;
            this.totalScoreChange += scoreChange;
            this.scoreChange = this.totalScoreChange/this.occurrences;
            
        }
        
    }
    
    private int getCantorPairingId(int a, int b){
        return (a + b) * (a + b + 1) / 2 + a;
    }
}
