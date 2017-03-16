package controllers.singlePlayer.ehauckdo;

import controllers.singlePlayer.ehauckdo.KBEvoMCTS.MCTS;
import core.game.StateObservation;
import core.player.AbstractPlayer;
import java.util.ArrayList;
import java.util.Random;
import ontology.Types;
import tools.ElapsedCpuTimer;

/**
 *
 * @author hauck
 */
public class Agent extends AbstractPlayer{
    
    private CustomController controller;
    private MCTS mcts;
    boolean alert = false;
    
    public int num_actions;
    public Types.ACTIONS[] actions;

    public Agent(StateObservation stateObs, ElapsedCpuTimer elapsedTimer) {
        
        ArrayList<Types.ACTIONS> act = stateObs.getAvailableActions();
        actions = new Types.ACTIONS[act.size()];
        for(int i = 0; i < actions.length; ++i)
        {
            actions[i] = act.get(i);
        }
        num_actions = actions.length;
        
        //controller = new BFS(stateObs, elapsedTimer);
        controller = new MCTS(new Random(), num_actions, actions);
    }

    
    @Override
    public Types.ACTIONS act(StateObservation stateObs, ElapsedCpuTimer elapsedTimer) {
        
        if(controller.switchController() || stateObs.getNPCPositions() != null) {
    		if(controller instanceof BFS) {
                    if(!alert){
                        System.out.println("Not deterministic: Switching to MCTS");
                        alert = true;
                    }
                    controller = new MCTS(new Random(), num_actions, actions);
                    
                }
        
        }
        
        return controller.act(stateObs, elapsedTimer);
    }
    
    
}
