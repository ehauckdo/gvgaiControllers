package tracks.singlePlayer.sampleMCTS_AvoidOpposite;

import java.util.Random;

import core.game.StateObservation;
import java.util.ArrayList;
import java.util.List;
import ontology.Types;
import tools.ElapsedCpuTimer;

/**
 * Created with IntelliJ IDEA.
 * User: Diego
 * Date: 07/11/13
 * Time: 17:13
 */
public class MCTS
{


    /**
     * Root of the tree.
     */
    public TreeNode m_root;

    /**
     * Random generator.
     */
    public Random m_rnd;

    public int num_actions;
    public Types.ACTIONS[] actions;
    List<Integer> rolloutsPerAct = new ArrayList();
    public static List<List<Types.ACTIONS>> simulations = new ArrayList();

    public MCTS(Random a_rnd, int num_actions, Types.ACTIONS[] actions)
    {
        this.num_actions = num_actions;
        this.actions = actions;
        m_rnd = a_rnd;
    }

    /**
     * Inits the tree with the new observation state in the root.
     * @param a_gameState current state of the game.
     */
    public void init(StateObservation a_gameState)
    {
        //Set the game observation to a newly root node.
        //System.out.println("learning_style = " + learning_style);
        m_root = new TreeNode(m_rnd, num_actions, actions);
        m_root.rootState = a_gameState;
    }

    /**
     * Runs MCTS to decide the action to take. It does not reset the tree.
     * @param elapsedTimer Timer when the action returned is due.
     * @return the action to execute in the game.
     */
    public int run(ElapsedCpuTimer elapsedTimer)
    {
        //Do the search within the available time.
        m_root.mctsSearch(elapsedTimer);

        //Determine the best action to take and return it.
        int action = m_root.mostVisitedAction();
        rolloutsPerAct.add(m_root.numIters);
        
        //int action = m_root.bestAction();
        return action;
    }

}
