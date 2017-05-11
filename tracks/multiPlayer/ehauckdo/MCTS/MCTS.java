package tracks.multiPlayer.ehauckdo.MCTS;

import java.util.Random;
import core.game.StateObservationMulti;

import ontology.Types;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import tools.ElapsedCpuTimer;

public class MCTS
{
    static final Logger LOGGER = Logger.getLogger(MCTS.class.getName());
    public TreeNode m_root;
    public static Random m_rnd;

    int[] NUM_ACTIONS;
    Types.ACTIONS[][] actions;

    public int iters = 0, num = 0;
    public int id, oppID, no_players;
    
    public static WeightMatrix weightMatrix;
    public static RandomCollection<WeightMatrix> matrix_collection = new RandomCollection<>();
    
    public static KnowledgeBase knowledgeBase = new KnowledgeBase();
    public static int num_evolutions = 0;
    
    public MCTS(Random a_rnd, int[] NUM_ACTIONS, Types.ACTIONS[][] actions, int id, int oppID, int no_players){
        m_rnd = a_rnd;
        this.NUM_ACTIONS = NUM_ACTIONS;
        this.actions = actions;
        this.id = id;
        this.oppID = oppID;
        this.no_players = no_players;
        m_root = new TreeNode(a_rnd, NUM_ACTIONS, actions, id, oppID, no_players);
        weightMatrix = new WeightMatrix(NUM_ACTIONS[0]);
        LOGGER.setLevel(Level.OFF);  
    }

   
    public void init(StateObservationMulti a_gameState){
        //Set the game observation to a newly root node.
        m_root = new TreeNode(m_rnd, NUM_ACTIONS, actions, id, oppID, no_players);
        m_root.state = a_gameState;

    }


    public int run(ElapsedCpuTimer elapsedTimer){
        //Do the search within the available time.
        m_root.mctsSearch(elapsedTimer);
        //System.out.println(elapsedTimer.remainingTimeMillis());

        iters += TreeNode.totalIters;
        num ++;

        //Determine the best action to take and return it.
        int action = m_root.getNextAction();
        //System.out.println(elapsedTimer.remainingTimeMillis());
        //int action = m_root.bestAction();
        return action;
    }
    
    public static void simpleSample() {
        weightMatrix = matrix_collection.next();
        matrix_collection.clear();
    }

    public static void differentialEvolution() {}

}
