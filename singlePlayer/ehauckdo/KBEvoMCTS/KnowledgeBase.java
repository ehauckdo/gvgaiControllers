package controllers.singlePlayer.ehauckdo.KBEvoMCTS;

import java.util.HashMap;

/**
 *
 * @author hauck
 */
public class KnowledgeBase {

    HashMap<Integer, EventRecord> events = new HashMap();
     
    public void add(int actSpriteId, int pasSpriteId, int scoreChange){
        EventRecord er = events.get(getCantorPairingId(pasSpriteId, pasSpriteId));
        if(er != null){
            er.addOccurrence(scoreChange);
        }
        else{
            er = new EventRecord(actSpriteId, pasSpriteId, scoreChange);
            events.put(getCantorPairingId(pasSpriteId, pasSpriteId), er);
        }
    }
       
    public class EventRecord{
        int occurrences;
        int scoreChange;
        int totalScoreChange;
        int activeSpriteId;
        int passiveSpriteId;

        public EventRecord(int activeSpriteId, int passiveSpriteId, int scoreChange) {
            this.occurrences = 1;
            this.activeSpriteId = activeSpriteId;
            this.passiveSpriteId = passiveSpriteId;
            this.scoreChange = scoreChange;
            this.totalScoreChange = scoreChange;
        }
        
        public void addOccurrence(int scoreChange){
            this.occurrences += 1;
            this.totalScoreChange += scoreChange;
            this.scoreChange = this.totalScoreChange/this.occurrences;
        }
        
    }
    
    private int getCantorPairingId(int a, int b){
        return (a + b) * (a + b + 1) / 2 + a;
    }
}
