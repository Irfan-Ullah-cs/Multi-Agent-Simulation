package carSimulaiton.styles;

import java.awt.Color;
import java.awt.Font;

import carSimulaiton.GarbageBin;
import repast.simphony.visualizationOGL2D.DefaultStyleOGL2D;
import saf.v3d.scene.VSpatial;

/**
 * Style class for garbage bins that changes their color based on fill level.
 */
public class GarbageBinStyle extends DefaultStyleOGL2D {
    
    @Override
    public VSpatial getVSpatial(Object agent, VSpatial spatial) {
        if (spatial == null) {
            // Use a rectangle shape for the bin
            spatial = shapeFactory.createRectangle(15, 15);
        }
        return spatial;
    }
    
    @Override
    public Color getColor(Object agent) {
        if (agent instanceof GarbageBin) {
            GarbageBin bin = (GarbageBin) agent;
            double fillPercent = bin.getFillPercentage();
            
            if (bin.isBeingServiced()) {
                return new Color(100, 149, 237); // Cornflower Blue for bins being serviced
            } else if (fillPercent >= 90) {
                return new Color(255, 0, 0);     // Red for very full bins (90-100%)
            } else if (fillPercent >= 70) {
                return new Color(255, 165, 0);   // Orange for high fill (70-90%)
            } else if (fillPercent >= 40) {
                return new Color(255, 255, 0);   // Yellow for medium fill (40-70%)
            } else if (fillPercent >= 10) {
                return new Color(144, 238, 144); // Light green for low fill (10-40%)
            } else {
                return new Color(0, 128, 0);     // Green for nearly empty bins (0-10%)
            }
        }
        
        return Color.GREEN; // Default color
    }
    
    @Override
    public String getLabel(Object agent) {
        if (agent instanceof GarbageBin) {
            GarbageBin bin = (GarbageBin) agent;
            return String.format("%d: %.0f%%", bin.getId(), bin.getFillPercentage());
        }
        return "";
    }
    
    @Override
    public Font getLabelFont(Object agent) {
        return new Font("Arial", Font.BOLD, 12);
    }
    
    @Override
    public Color getLabelColor(Object agent) {
        return Color.BLACK;
    }
    
    @Override
    public float getScale(Object agent) {
        if (agent instanceof GarbageBin) {
            GarbageBin bin = (GarbageBin) agent;
            // Scale bins slightly larger as they fill up
            double fillPercent = bin.getFillPercentage();
            return (float) (0.7 + (fillPercent / 100.0) * 0.5);
        }
        return 1.0f;
    }
}