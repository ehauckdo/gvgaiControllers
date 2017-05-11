package tracks.singlePlayer.KBFEvoMCTS.MCTS;

import tracks.singlePlayer.KBFEvoMCTS.CustomController;
import core.game.Event;
import core.game.StateObservation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Random;
import ontology.Types;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import tools.ElapsedCpuTimer;



/**
 *
 * @author hauck based on the sample SingleMCTSPlayer
 */
public class MCTS extends CustomController {

    static final Logger LOGGER = Logger.getLogger(MCTS.class.getName());
    public static int ID = 0;
    public SingleTreeNode m_root;

    public static Random m_rnd;
    public static int num_actions;
    public static int num_evolutions;

    public static WeightMatrix weightMatrix;
    public static RandomCollection<WeightMatrix> matrix_collection = new RandomCollection<>();

    public static KnowledgeBase knowledgeBase = new KnowledgeBase();

    Types.ACTIONS[] actions;

    public MCTS(Random a_rnd, int num_actions, Types.ACTIONS[] actions) {
        this.num_actions = num_actions;
        this.actions = actions;
        m_rnd = a_rnd;
        weightMatrix = new WeightMatrix(num_actions);
        LOGGER.setLevel(Level.OFF);   
    }

    @Override
    public Types.ACTIONS act(StateObservation stateObs, ElapsedCpuTimer elapsedTimer) {
        
        //Set the game observation to a newly root node.
        m_root = new SingleTreeNode(stateObs, m_rnd, num_actions, actions);
        
        //Do the search within the available time.
        m_root.mctsSearch(elapsedTimer);

        // Sample from all the mutated matrices and choose 1 to keep stored
        //if (!matrix_collection.isEmpty()) {         
            //simpleSample();
            //differentialEvolution();
        //}

        //Determine the best action to take and return it.
        int action = m_root.getNextAction();
        return actions[action];
    }

    @Override
    public boolean switchController() {
        return false;
    }
    

    public static void simpleSample() {
        //MCTS.LOGGER.log(Level.INFO, "Sampling from collection of "+matrix_collection.map.size()+" matrices"); 
        weightMatrix = matrix_collection.next();
        matrix_collection.clear();
        /*MCTS.LOGGER.log(Level.INFO, "Chosen:");
        weightMatrix.printMatrix();*/
    }

    public static void differentialEvolution() {
        /*weightMatrix best = weightMatrix;
        weightMatrix sample1 = matrix_collection.next();
        weightMatrix sample2 = matrix_collection.next();*/

        /*for(Integer i :sample1.mapped_features.keySet()){
            MCTS.LOGGER.log(Level.INFO, i);
        }
        for(Integer i :sample2.mapped_features.keySet()){
            MCTS.LOGGER.log(Level.INFO, i);
        }*/
    }
        
    
}
