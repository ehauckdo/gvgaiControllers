package controllers.multiPlayer.ehauckdo;

import core.game.StateObservation;
import ontology.Types;
import tools.ElapsedCpuTimer;

/**
 *
 * @author hauck
 */
public abstract class CustomController {
    
    public abstract Types.ACTIONS act(StateObservation stateObs, ElapsedCpuTimer elapsedTimer);
    
    public abstract boolean switchController();
    
}
