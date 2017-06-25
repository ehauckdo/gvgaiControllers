package tracks.singlePlayer.sampleMCTS_AvoidOpposite;

import core.game.Observation;
import java.util.ArrayList;
import java.util.Random;

import core.game.StateObservation;
import core.player.AbstractPlayer;
import java.util.HashMap;
import java.util.List;
import ontology.Types;
import tools.ElapsedCpuTimer;
import tools.Vector2d;
import tracks.singlePlayer.ehauckdo.MCTS.Util;

/**
 * Created with IntelliJ IDEA. User: ssamot Date: 14/11/13 Time: 21:45 This is
 * an implementation of MCTS UCT
 */
public class Agent extends AbstractPlayer {

    public int num_actions;
    public Types.ACTIONS[] actions;
    HashMap<Types.ACTIONS, List<Types.ACTIONS>> redundantActionList = new HashMap();

    protected MCTS mctsPlayer;

    /**
     * Public constructor with state observation and time due.
     *
     * @param so state observation of the current game.
     * @param elapsedTimer Timer for the controller creation.
     */
    public Agent(StateObservation so, ElapsedCpuTimer elapsedTimer) {
        //Get the actions in a static array.
        ArrayList<Types.ACTIONS> act = so.getAvailableActions();
        actions = new Types.ACTIONS[act.size()];
        for (int i = 0; i < actions.length; ++i) {
            actions[i] = act.get(i);
        }
        num_actions = actions.length;

        //Create the player.
        mctsPlayer = getPlayer(so, elapsedTimer);
        fecthRedundantActions(so);
        MCTS.redundantActionsList = redundantActionList;
    }

    public MCTS getPlayer(StateObservation so, ElapsedCpuTimer elapsedTimer) {
        return new MCTS(new Random(), num_actions, actions);
    }

    /**
     * Picks an action. This function is called every game step to request an
     * action from the player.
     *
     * @param stateObs Observation of the current state.
     * @param elapsedTimer Timer when the action returned is due.
     * @return An action for the current state
     */
    public Types.ACTIONS act(StateObservation stateObs, ElapsedCpuTimer elapsedTimer) {

        //Set the state observation object as the new root of the tree.
        mctsPlayer.init(stateObs);

        //Determine the action using MCTS...
        int action = mctsPlayer.run(elapsedTimer);

        //... and return it.
        return actions[action];
    }

    @Override
    public void printRolloutsAverage() {
       /* for (List<Types.ACTIONS> simulation : MCTS.simulations) {
            if (simulation.size() > 0) {
                for (Types.ACTIONS action : simulation) {
                    System.out.print(Util.printAction(action) + " ");
                }
                System.out.println("");
            }
        }*/
        System.out.println("Total redundant actions: "+countRedundantActions());
        int average = 0;
        int sum = 0;
        if (mctsPlayer.rolloutsPerAct.size() > 0) {
            for (Integer i : mctsPlayer.rolloutsPerAct) {
                sum += i;
            }
            average = sum / mctsPlayer.rolloutsPerAct.size();
        }
        System.out.println("Total number of rollouts: "+sum);
        System.out.println("Average: " + average);

    }
    
    public int countRedundantActions(){
        int total = 0;
        for (List<Types.ACTIONS> simulation : MCTS.simulations) {
            if (simulation.size() > 0) {
                
//                System.out.println("Simulation:");
//                for (Types.ACTIONS action : simulation) {
//                    System.out.print(Util.printAction(action) + " ");
//                }
//                System.out.println("");
                
                Types.ACTIONS current_action = simulation.get(0);
                for(int i = 1; i < simulation.size(); i++){
                    Types.ACTIONS next_action = simulation.get(i);
                    if(isRedundant(next_action, current_action))
                        total = total + 1;
                    current_action = next_action;
                }
//                System.out.println("Total redundant actions: "+total);
            }
        }
        return total;
    }
    
    public boolean isRedundant(Types.ACTIONS first_action, Types.ACTIONS second_action){
        List<Types.ACTIONS> redundantActions = MCTS.redundantActionsList.get(first_action);
        if(redundantActions.contains(second_action))
            return true;
        else 
               return false;
    }

    public final void fecthRedundantActions(StateObservation so) {
        boolean[] fetched = new boolean[actions.length];

        for (int i = 0; i < actions.length; i++) {
            redundantActionList.put(actions[i], new ArrayList());

            StateObservation so_copy = so.copy();
            System.out.println("Testing movement "+Util.printAction(actions[i]));

            Vector2d initial_pos = so_copy.getAvatarPosition();
            Vector2d initial_orient = so_copy.getAvatarOrientation();
            int initial_events = so_copy.getEventsHistory().size();
            List<Observation> initial_projs = getProjectiles(so_copy);

            //System.out.println("Initial position: "+initial_pos.toString());
            //System.out.println("Initial orientation: "+initial_orient.toString());
            //System.out.println("Initial number of events: "+initial_events);
            //System.out.println("Initial number of projectiles: "+initial_projs.size());
            //System.out.println("");
            so_copy.advance(actions[i]);

            Vector2d intermediary_pos = so_copy.getAvatarPosition();
            Vector2d intermediary_orient = so_copy.getAvatarOrientation();
            int intermediary_events = so_copy.getEventsHistory().size();
            List<Observation> intermediary_projs = getProjectiles(so_copy);

            //System.out.println("Intermediary position: "+so_copy.getAvatarPosition().toString());
            //System.out.println("Intermediary orientation: "+intermediary_orient.toString());
            //System.out.println("Intermediary number of events: "+so_copy.getEventsHistory().size());
            //System.out.println("Intermediary number of projectiles: "+getProjectiles(so_copy).size());
            //System.out.println("");
            // if new event triggered, then we can't say anything about it
            if (so_copy.getEventsHistory().size() != initial_events) {
                System.out.println("New events triggered! Skipping...\n");
                continue;
            }

            // it's a movement action, fetch them here
            if (!initial_pos.equals(so_copy.getAvatarPosition())) {
                //System.out.println("It's a movement action.");

                for (int j = 0; j < actions.length; j++) {
                    //System.out.println("Combining with action "+printAction(actions[j]));
                    StateObservation so_copy2 = so_copy.copy();
                    so_copy2.advance(actions[j]);
                    Vector2d new_pos = so_copy2.getAvatarPosition();
                    //System.out.println("New position: "+new_pos);
                    if (new_pos.equals(initial_pos)) {
                        //System.out.println("It's a nulling action. Saving...\n");
                        addActionToHashMap(redundantActionList, actions[i], actions[j]);
                        fetched[i] = true;
                        break;
                    }
                }

            }
            
            // it's a shooting action, fetch them here
            if (initial_projs.size() != getProjectiles(so_copy).size()) {
                System.out.println("It's a shooting action.\n");
                for (int j = 0; j < actions.length; j++) {
                    //System.out.println("Combining with action "+printAction(actions[j]));
                    StateObservation so_copy2 = so_copy.copy();
                    so_copy2.advance(actions[j]);

                    Vector2d new_pos = so_copy.getAvatarPosition();
                    int new_events = so_copy.getEventsHistory().size();
                    List<Observation> new_projs = getProjectiles(so_copy2);

                    //System.out.println("Final position: "+new_pos.toString());
                    //System.out.println("Final number of events: "+new_events);
                    //System.out.println("Final number of projectiles: "+new_projs.size());
                    //System.out.println("");
                    if (initial_events != new_events) {
                        //System.out.println("New event triggered! Not a nulling action. Skipping...");
                        continue;
                    }

                    if (!initial_pos.equals(new_pos)) {
                        //System.out.println("Moved! Not a nulling action. Skipping...");
                        continue;
                    }

                    if (hasNewProjectiles(intermediary_projs, new_projs)) {
                        //System.out.println("New projectiles created! Not a nulling action. Skipping");
                        continue;
                    }

                    if (new_projs.size() == intermediary_projs.size()) {
                        //System.out.println("Has exactly same projectiles. It's a nulling action. Saving...\n");
                        addActionToHashMap(redundantActionList, actions[i], actions[j]);
                        fetched[i] = true;
                        break;
                    } else {
                        //System.out.println("One or more projectiles were destroyed. Not a nulling action. Skipping...\n");
                        continue;
                    }

                }
            }

            // It's a change of orientation action
            /*if (!initial_orient.equals(intermediary_orient)) {
                System.out.println("It's an orientation action.\n");

                // get a set of actions that change orientation
                for (int j = 0; j < actions.length; j++) {
                    //System.out.println("Combining with action "+printAction(actions[j]));
                    StateObservation so_copy2 = so_copy.copy();
                    so_copy2.advance(actions[j]);

                    Vector2d final_orient = so_copy2.getAvatarOrientation();
                    if (!intermediary_orient.equals(final_orient)) {
                        //System.out.println("Changed orientation! It's a nulling action. Saving...\n");
                        addActionToHashMap(redundantActionList, actions[i], actions[j]);
                        fetched[i] = true;
                        continue;
                    }

                }

            }*/

            System.out.println("Nulling actions for " + Util.printAction(actions[i]));
            List<Types.ACTIONS> nulling_actions = redundantActionList.get(actions[i]);
            for (Types.ACTIONS action : nulling_actions) {
                System.out.println(Util.printAction(action));
            }

        }

        for (int i = 0; i < actions.length; i++) {
            System.out.println("Action " + Util.printAction(actions[i]) + ": " + (fetched[i] == true ? "OK" : "Not Fetched"));
        }
    }

    final List<Observation> getProjectiles(StateObservation stateObs) {
        int projectiles_number = 0;
        List<Observation> all_projs = new ArrayList();
        ArrayList<Observation>[] projectiles = stateObs.getFromAvatarSpritesPositions();
        if (projectiles != null) {
            for (ArrayList<Observation> projectile : projectiles) {
                for (Observation obs : projectile) {
                    projectiles_number += 1;
                    all_projs.add(obs);
                }
            }
        }
        //return projectiles_number;
        return all_projs;
    }

    final boolean hasNewProjectiles(List<Observation> old_list, List<Observation> new_list) {
        for (Observation new_proj : new_list) {
            boolean found = false;
            for (Observation old_proj : old_list) {
                if (new_proj.obsID == old_proj.obsID) {
                    found = true;
                }
            }
            if (found == false) {
                return true;
            }
        }
        return false;
    }

    final void addActionToHashMap(HashMap<Types.ACTIONS, List<Types.ACTIONS>> hashmap,
            Types.ACTIONS action, Types.ACTIONS nulling_action) {
        List<Types.ACTIONS> nulling_actions = hashmap.get(action);
        if (nulling_actions == null) {
            nulling_actions = new ArrayList();
        }
        nulling_actions.add(nulling_action);
        hashmap.put(action, nulling_actions);
    }

}
