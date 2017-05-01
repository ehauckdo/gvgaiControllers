package controllers.multiPlayer.ehauckdo;

import controllers.multiPlayer.ehauckdo.MCTS.MCTS;
import core.game.Observation;
import core.game.StateObservationMulti;
import core.player.AbstractMultiPlayer;
import ontology.Types;
import tools.ElapsedCpuTimer;

import java.util.ArrayList;
import java.util.Random;

public class Agent extends AbstractMultiPlayer {

    public int[] NUM_ACTIONS;
    public Types.ACTIONS[][] actions;

    public int id, oppID, no_players;

    private MCTS mctsPlayer;

    public Agent(StateObservationMulti so, ElapsedCpuTimer elapsedTimer, int playerID){
        //get game information

        no_players = so.getNoPlayers();
        id = playerID;
        oppID = (id + 1) % so.getNoPlayers();

        //Get the actions for all players in a static array.

        NUM_ACTIONS = new int[no_players];
        actions = new Types.ACTIONS[no_players][];
        for (int i = 0; i < no_players; i++) {

            ArrayList<Types.ACTIONS> act = so.getAvailableActions(i);

            actions[i] = new Types.ACTIONS[act.size()];
            for (int j = 0; j < act.size(); ++j) {
                actions[i][j] = act.get(j);
            }
            NUM_ACTIONS[i] = actions[i].length;
        }

        //Create the player.
        mctsPlayer = new MCTS(new Random(), NUM_ACTIONS, actions, id, oppID, no_players);
    }

    public Types.ACTIONS act(StateObservationMulti stateObs, ElapsedCpuTimer elapsedTimer) {

        ArrayList<Observation> obs[] = stateObs.getFromAvatarSpritesPositions();
        ArrayList<Observation> grid[][] = stateObs.getObservationGrid();

        //Set the state observation object as the new root of the tree.
        mctsPlayer.init(stateObs);

        //Determine the action using MCTS...
        int action = mctsPlayer.run(elapsedTimer);

        //... and return it.
        return actions[id][action];
    }

}
