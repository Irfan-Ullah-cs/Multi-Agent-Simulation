package carSimulaiton.styles;

import java.awt.Color;
import repast.simphony.visualizationOGL2D.DefaultStyleOGL2D;
import saf.v3d.scene.VSpatial;
import saf.v3d.ShapeFactory2D;
import carSimulaiton.TrafficLight;

public class TrafficLightStyle extends DefaultStyleOGL2D {
    
    @Override
    public VSpatial getVSpatial(Object object, VSpatial spatial) {
        if (object instanceof TrafficLight) {
            if (spatial == null) {
                ShapeFactory2D factory = new ShapeFactory2D();
                // Create a circle for traffic lights
                return factory.createCircle(10, 10);
            }
        }
        return spatial;
    }
    
    @Override
    public Color getColor(Object object) {
        if (object instanceof TrafficLight) {
            TrafficLight light = (TrafficLight) object;
            TrafficLight.LightState state = light.getState();
            
            if (state != null) {
                switch (state) {
                    case RED:    return Color.RED;
                    case YELLOW: return Color.YELLOW;
                    case GREEN:  return Color.GREEN;
                    default:     return Color.RED;
                }
            }
        }
        return Color.RED;
    }
}