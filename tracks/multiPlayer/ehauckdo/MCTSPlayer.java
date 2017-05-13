package tracks.multiPlayer.ehauckdo;

import tracks.multiPlayer.advanced.sampleMCTS.*;
import java.util.Random;

import core.game.StateObservationMulti;
import java.awt.Dimension;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import ontology.Types;
import tools.ElapsedCpuTimer;
import tools.Vector2d;

/**
 * Created with IntelliJ IDEA.
 * User: Diego
 * Date: 07/11/13
 * Time: 17:13
 */
public class MCTSPlayer
{

    /**
     * Root of the tree.
     */
    public TreeNode m_root;

    int[] NUM_ACTIONS;
    Types.ACTIONS[][] actions;

    /**
     * Random generator.
     */
    public Random m_rnd;
    public int id, oppID, no_players;
    
    public int gridSize_H;
    public int gridSize_W;
    
    public static double tileSet[][];

    public MCTSPlayer(Random a_rnd, StateObservationMulti so, int[] NUM_ACTIONS, Types.ACTIONS[][] actions, int id, int oppID, int no_players)
    {
        m_rnd = a_rnd;
        this.NUM_ACTIONS = NUM_ACTIONS;
        this.actions = actions;
        this.id = id;
        this.oppID = oppID;
        this.no_players = no_players;
        initializeTileSet(so);
    }

    /**
     * Inits the tree with the new observation state in the root.
     * @param a_gameState current state of the game.
     */
    public void init(StateObservationMulti a_gameState)
    {
        //Set the game observation to a newly root node.
        //System.out.println("learning_style = " + learning_style);
        m_root = new TreeNode(m_rnd, NUM_ACTIONS, actions, id, oppID, no_players);
        m_root.rootState = a_gameState;
    }

    /**
     * Runs MCTS to decide the action to take. It does not reset the tree.
     * @param elapsedTimer Timer when the action returned is due.
     * @return the action to execute in the game.
     */
    public int run(ElapsedCpuTimer elapsedTimer)
    {
        updateWeights();
        
        Vector2d pos = Util.getCurrentGridPosition(m_root.rootState, id);
        tileSet[(int)pos.x][(int)pos.y] = 0.5;
        
        //Do the search within the available time.
        m_root.mctsSearch(elapsedTimer);

        //Determine the best action to take and return it.
        int action = m_root.mostVisitedAction();
        //int action = m_root.bestAction();
        return action;
    }
    
    public final void initializeTileSet(StateObservationMulti so){
        System.out.println("=== Initializing Tile Set ====");
        Dimension d = so.getWorldDimension();
        gridSize_H = (int)d.getHeight()/so.getBlockSize();
        gridSize_W = (int) d.getWidth()/so.getBlockSize();
        System.out.println("gridSize_H: "+gridSize_H+", gridSize_W: "+gridSize_W);
        
        tileSet = new double[gridSize_W][gridSize_H];
        
        for(int i = 0; i < gridSize_W; i++){
            for(int j = 0; j < gridSize_H; j++){
                tileSet[i][j] = 1;
            }
        }
        System.out.println("=== Done ====");
    }

    private void updateWeights() { 
        for(int i = 0; i < gridSize_W; i++){
            for(int j = 0; j < gridSize_H; j++){
                tileSet[i][j] += 0.05;
            }
        }
    }
    
  

}
