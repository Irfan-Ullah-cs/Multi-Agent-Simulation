package carSimulaiton.styles;

import java.awt.Color;
import java.io.IOException;
import java.net.URL;

import repast.simphony.visualizationOGL2D.DefaultStyleOGL2D;
import saf.v3d.scene.VSpatial;
import saf.v3d.ShapeFactory2D;
import carSimulaiton.Car;
import carSimulaiton.Road;

public class CarStyle extends DefaultStyleOGL2D {

    private ShapeFactory2D shapeFactory;

    @Override
    public VSpatial getVSpatial(Object object, VSpatial spatial) {
        if (object instanceof Car) {
            if (spatial == null) {
                shapeFactory = new ShapeFactory2D();
                String imagePath = getImagePath(object); // Get the image path
                if (imagePath != null) {
                    // Create the image
                    try {
                        spatial = shapeFactory.createImage(imagePath);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } else {
                    // Fallback to a rectangle if the image cannot be loaded
                    spatial = shapeFactory.createRectangle(15, 15);
                }
            }
        }
        return spatial;
    }

    @Override
    public Color getColor(Object object) {
        if (object instanceof Car) {
            Car car = (Car) object;
            Car.Direction direction = car.getCurrentDirection();

            if (direction != null) {
                // Use color to indicate direction
                switch (direction) {
                    case EAST: return Color.RED;
                    case WEST: return Color.BLUE;
                    case NORTH: return Color.GREEN;
                    case SOUTH: return Color.YELLOW;
                    default: return Color.GRAY;
                }
            }
        }
        return Color.GRAY;
    }

    @Override
    public float getScale(Object object) {
        // This is the key method for controlling size
        if (object instanceof Car) {
            return 0.06f; // This makes the image very small (20% of original size)
            // You can adjust this value as needed (0.1f = 10%, 0.3f = 30%, etc.)
        }
        return 1.0f; // Default scale
    }

    @Override
    public float getRotation(Object object) {
        if (object instanceof Car) {
            Car car = (Car) object;
            Car.Direction direction = car.getCurrentDirection();

            if (direction != null) {
                switch (direction) {
                    case EAST: return 0f;
                    case NORTH: return (float)(Math.PI * 1.5); // 270 degrees
                    case WEST: return (float)Math.PI; // 180 degrees
                    case SOUTH: return (float)(Math.PI * 0.5); // 90 degrees
                }
            }
        }
        return 0f;
    }

    /**
     * Returns the path to the image file based on the car's direction.
     */
    public String getImagePath(Object object) {
        if (object instanceof Car) {
            Car car = (Car) object;
            Car.Direction direction = car.getCurrentDirection();
            String fileName = "car_west.png"; // Default image

            if (direction != null) {
                switch (direction) {
                    case EAST: fileName = "car_east.png"; break;
                    case WEST: fileName = "car_west.png"; break;
                    case NORTH: fileName = "car_north.png"; break;
                    case SOUTH: fileName = "car_south.png"; break;
                }
            }

            // Construct the full path to the image file
            return "resources/" + fileName;
        }
        return null;
    }
}