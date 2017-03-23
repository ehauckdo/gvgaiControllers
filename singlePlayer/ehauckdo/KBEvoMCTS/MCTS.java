package controllers.singlePlayer.ehauckdo.KBEvoMCTS;

import controllers.singlePlayer.ehauckdo.CustomController;
import core.game.Event;
import core.game.StateObservation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
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
    
    public static KnowledgeBase knowledgeBase = new KnowledgeBase();
    public static double lastScore = 0;
    public static ArrayList<Integer> knownSprites = new ArrayList<>();
    public static int lastKnownEvent = -1;

    Types.ACTIONS[] actions;

    public MCTS(Random a_rnd, int num_actions, Types.ACTIONS[] actions) {
        this.num_actions = num_actions;
        this.actions = actions;
        m_rnd = a_rnd;
        weightMatrix = new weightMatrix(num_actions);
        
    }

    @Override
    public Types.ACTIONS act(StateObservation stateObs, ElapsedCpuTimer elapsedTimer) { 
        
        buildKnowledgeBase(stateObs);
        
        //Set the game observation to a newly root node.
        m_root = new SingleTreeNode(stateObs, m_rnd, num_actions, actions);

        //Do the search within the available time.
        m_root.mctsSearch(elapsedTimer);

        // Sample from all the mutated matrices and choose 1 to keep stored
        if (!matrix_collection.isEmpty()) {
            
            simpleSample();
            //differentialEvolution();
        }
        
        lastScore = stateObs.getGameScore();
        
        //Determine the best action to take and return it.
        int action = m_root.mostVisitedAction();
        return actions[action];
    }

    @Override
    public boolean switchController() {
        return false;
    }
    
    public static void simpleSample(){
        //System.out.println("Sampling from collection of "+matrix_collection.map.size()+" matrices"); 
        weightMatrix = matrix_collection.next();
        current_bestFitness = weightMatrix.fitness;
        matrix_collection.clear();
        /*System.out.println("Chosen:");
        weightMatrix.printMatrix();*/
    }
    
    public static void differentialEvolution(){
        /*weightMatrix best = weightMatrix;
        weightMatrix sample1 = matrix_collection.next();
        weightMatrix sample2 = matrix_collection.next();*/
        
        /*for(Integer i :sample1.mapped_features.keySet()){
            System.out.println(i);
        }
        for(Integer i :sample2.mapped_features.keySet()){
            System.out.println(i);
        }*/    

    }

    private void buildKnowledgeBase(StateObservation stateObs) {
        
        // if there are no events, just return
        if(stateObs.getEventsHistory().isEmpty())
            return;
        
        Iterator<Event> events = stateObs.getEventsHistory().iterator();
        int event_index = -1;
        
        // Save to the knowledge base only new events happened in the previous
        // step of the game
        while(events.hasNext()){
            event_index += 1;
            Event e = events.next();
            if(event_index > lastKnownEvent){
                if(current_features.containsKey(e.passiveTypeId))
                    knowledgeBase.add(e.activeTypeId, e.passiveTypeId, stateObs.getGameScore()-lastScore);
            }
        }
        lastKnownEvent = event_index;
        
        //knowledgeBase.printKnowledgeBase();
        //System.out.println(event_index+", "+lastKnownEvent);
        //if(knowledgeBase.events.size() > 10){
        //    System.out.println(stateObs.getAvatarType());
        //    System.exit(0);
        //}
    }
    
    public void querySprites(StateObservation stateObs){
        knownSprites.clear();
        
        /*ArrayList<Observation>[] resources = stateObs.getResourcesPositions();
        for (ArrayList<Observation> resource : resources) {
            for(Observation obs : resource){
                knownSprites.add(obs.)
            }
        }*/
    }
   
    
}
