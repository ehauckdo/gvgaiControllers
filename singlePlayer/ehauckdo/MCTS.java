package controllers.singlePlayer.ehauckdo;

import controllers.singlePlayer.ehauckdo.structures.featureWeight;
import core.game.StateObservation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;
import java.util.Set;
import ontology.Types;
import tools.ElapsedCpuTimer;

/**
 *
 * @author hauck 
 * based on the sample SingleMCTSPlayer
 */
public class MCTS extends CustomController {

    public SingleTreeNode m_root;
    public static double[][] weightMatrix;
   
    public static Random m_rnd;
    public static int num_actions;
    public static int num_evolutions;
    public static double current_bestFitness;
    public static HashMap<Integer, Double> features = new HashMap<>();
    public static ArrayList<Integer> current_features = new ArrayList<>();
    public static HashMap<Integer, featureWeight> weightHashMap = new HashMap<>();
      
    Types.ACTIONS[] actions;

    public MCTS(Random a_rnd, int num_actions, Types.ACTIONS[] actions)
    {
        this.num_actions = num_actions;
        this.actions = actions;
        m_rnd = a_rnd;
        
    }  
    
    @Override
    public Types.ACTIONS act(StateObservation stateObs, ElapsedCpuTimer elapsedTimer){
        //Set the game observation to a newly root node.
        m_root = new SingleTreeNode(stateObs, m_rnd, num_actions, actions);
        
        //Do the search within the available time.
        m_root.mctsSearch(elapsedTimer);

        //Determine the best action to take and return it.
        int action = m_root.mostVisitedAction();
        return actions[action];
    }
    
    @Override
    public boolean switchController() {
        return false;
    }

    public static HashMap<Integer, featureWeight> mutateWeightMatrix(){
        
        HashMap<Integer, featureWeight> mutated_weightHashMap = new HashMap<>();
        
        for(Integer featureId: weightHashMap.keySet()){
            featureWeight weight = weightHashMap.get(featureId);
            featureWeight mutated_weight = new featureWeight(weight.distance, weight.weight);
            if(m_rnd.nextFloat() > 0.8)
                    mutated_weight.weight += m_rnd.nextGaussian()*0.1;
            mutated_weightHashMap.put(featureId, mutated_weight);
        }
        
        return mutated_weightHashMap;
    }
    

}
