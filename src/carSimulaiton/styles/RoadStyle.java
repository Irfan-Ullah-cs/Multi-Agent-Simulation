package carSimulaiton.styles;

import java.awt.Color;
import repast.simphony.visualizationOGL2D.DefaultStyleOGL2D;
import saf.v3d.scene.VSpatial;
import saf.v3d.ShapeFactory2D;
import carSimulaiton.Road;

public class RoadStyle extends DefaultStyleOGL2D {
    
    @Override
    public VSpatial getVSpatial(Object object, VSpatial spatial) {
        if (object instanceof Road) {
            if (spatial == null) {
                ShapeFactory2D factory = new ShapeFactory2D();
                // Create a rectangle for roads
                return factory.createRectangle(30, 30);
            }
        }
        return spatial;
    }
    
    @Override
    public Color getColor(Object object) {
        if (object instanceof Road) {
            Road road = (Road) object;
            return road.getColor();
        }
        return Color.black;
    }
}