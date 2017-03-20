package controllers.singlePlayer.ehauckdo.KBEvoMCTS;

import controllers.singlePlayer.ehauckdo.CustomController;
import controllers.singlePlayer.ehauckdo.KBEvoMCTS.weightMatrix;
import core.game.StateObservation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;
import ontology.Types;
import tools.ElapsedCpuTimer;

/**
 *
 * @author hauck based on the sample SingleMCTSPlayer
 */
public class MCTS extends CustomController {

    public SingleTreeNode m_root;

    public static Random m_rnd;
    public static int num_actions;
    public static int num_evolutions;
    public static double current_bestFitness;

    public static HashMap<Integer, Double> current_features = new HashMap<>();
    public static weightMatrix weightMatrix;

    public static RandomCollection<weightMatrix> matrix_collection = new RandomCollection<>();

    Types.ACTIONS[] actions;

    public MCTS(Random a_rnd, int num_actions, Types.ACTIONS[] actions) {
        this.num_actions = num_actions;
        this.actions = actions;
        m_rnd = a_rnd;
        weightMatrix = new weightMatrix(num_actions);
    }

    @Override
    public Types.ACTIONS act(StateObservation stateObs, ElapsedCpuTimer elapsedTimer) {
        

        //Set the game observation to a newly root node.
        m_root = new SingleTreeNode(stateObs, m_rnd, num_actions, actions);

        //Do the search within the available time.
        m_root.mctsSearch(elapsedTimer);

        // Sample from all the mutated matrices and choose 1 to keep stored
        if (!matrix_collection.isEmpty()) {
            //System.out.println("Sampling from collection.");
            weightMatrix = matrix_collection.next();
            current_bestFitness = weightMatrix.fitness;
            matrix_collection.clear();
            /*System.out.println("Chosen:");
            weightMatrix.printMatrix();*/
        }
        
        //Determine the best action to take and return it.
        int action = m_root.mostVisitedAction();
        return actions[action];
    }

    @Override
    public boolean switchController() {
        return false;
    }

 

}
