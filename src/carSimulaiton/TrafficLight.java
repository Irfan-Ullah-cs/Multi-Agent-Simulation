package carSimulaiton;

import repast.simphony.engine.schedule.ScheduledMethod;
import repast.simphony.random.RandomHelper;

import java.awt.Color;

public class TrafficLight {
    private LightState state;
    private int timer;
    private static final int GREEN_DURATION = 10;
    private static final int YELLOW_DURATION = 3;
    private static final int RED_DURATION = 10;

    public enum LightState {
        GREEN(Color.GREEN),
        YELLOW(Color.YELLOW),
        RED(Color.RED);

        private final Color color;

        LightState(Color color) {
            this.color = color;
        }

        public Color getColor() {
            return color;
        }
    }

    public TrafficLight() {
        // Start with a random state to desynchronize lights
        LightState[] states = LightState.values();
        this.state = states[RandomHelper.nextIntFromTo(0, states.length - 1)];
        this.timer = RandomHelper.nextIntFromTo(0, 5); // Random initial timer value
        System.out.println("Traffic light created with state: " + state);
    }
    
    public TrafficLight(LightState initialState) {
        this.state = initialState;
        this.timer = 0;
        System.out.println("Traffic light created with state: " + state);
    }

    @ScheduledMethod(start = 1, interval = 1)
    public void step() {
        timer++;

        if (timer >= getDuration(state)) {
            // Change state
            switch (state) {
                case GREEN:
                    state = LightState.YELLOW;
                    break;
                case YELLOW:
                    state = LightState.RED;
                    break;
                case RED:
                    state = LightState.GREEN;
                    break;
            }
            timer = 0; // Reset the timer
            System.out.println("Traffic light state changed to: " + state + " with color: " + state.getColor());
        }
    }

    private int getDuration(LightState state) {
        switch (state) {
            case GREEN:
                return GREEN_DURATION;
            case YELLOW:
                return YELLOW_DURATION;
            case RED:
                return RED_DURATION;
            default:
                return 10;
        }
    }

    public LightState getState() {
        return state;
    }

    public Color getColor() {
        return state.getColor();
    }
}