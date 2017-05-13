package tracks.multiPlayer.ehauckdo;

import core.game.Event;
import core.game.StateObservationMulti;
import java.util.HashMap;
import ontology.Types;
import org.apache.log4j.Level;
import tools.Vector2d;

/**
 *
 * @author hauck
 */
public class Util {
       
    public static int getCantorPairingId(int a, int b){
        return (a + b) * (a + b + 1) / 2 + a;
    }
    
    public static int[] ReverseCantorPairingId(int z){
        int[] pair = new int[2];
        int t = (int)Math.floor((-1D + Math.sqrt(1D + 8 * z))/2D);
        pair[0] = t * (t + 3) / 2 - z;
        pair[1] = z - t * (t + 1) / 2;
        return pair;
    }
    
    public static double calculateGridDistance(Vector2d a, Vector2d b, double blockSize){
        Vector2d grid_a = new Vector2d(a.x/blockSize, a.y/blockSize);
        Vector2d grid_b = new Vector2d(b.x/blockSize, b.y/blockSize);
        return grid_a.dist(grid_b);
    }
    
    public static String printAction(Types.ACTIONS action){
        String character = "";
        if(null != action)
            switch (action) {
            case ACTION_UP:
                character = "↑";
                break;
            case ACTION_DOWN:
                character = "↓";
                break;
            case ACTION_LEFT:
                character = "←";
                break;
            case ACTION_RIGHT:
                character = "→";
                break;
            case ACTION_USE:
                character = "USE";
                break;
            case ACTION_NIL:
                character = "NIL";
                break;
            default:
                break;
        }
        return character;
    }
    
    public static Types.ACTIONS getOppositeAction(Types.ACTIONS action){
        switch (action) {
            case ACTION_UP:
               return Types.ACTIONS.ACTION_DOWN;
            case ACTION_DOWN:
                return Types.ACTIONS.ACTION_UP;
            case ACTION_LEFT:
                 return Types.ACTIONS.ACTION_RIGHT;
            case ACTION_RIGHT:
                 return Types.ACTIONS.ACTION_LEFT;
            default:
               return null;
        }
    }
    
    public static Vector2d getCurrentGridPosition(StateObservationMulti so, int avatarId){
        Vector2d position = so.getAvatarPosition(avatarId);
        //System.out.println("Current position: "+position.x+","+position.y);
        
        int gridPos_X = (int)(position.x / so.getBlockSize());
        int gridPos_Y = (int)(position.y / so.getBlockSize());
        
        //System.out.println("Current Grid position: "+gridPos_X+","+gridPos_Y);
        
        return new Vector2d(gridPos_X, gridPos_Y);
        
    }
    
    public static void printEvent(Event e){
        System.out.println("GameStep: "+e.gameStep);
        System.out.println("Collision With Player: "+(e.fromAvatar?"NO":"YES"));
        System.out.println("Sprite Collided with: "+e.passiveSpriteId);
        System.out.println("Position: "+e.position.toString());

    }
   
}

