package carSimulaiton;

/**
 * Message class for communication between agents in the garbage collection simulation.
 */
public class Message {
    private int senderId;
    private String type;
    private String content;
    private long timestamp;

    /**
     * Create a new message.
     *
     * @param senderId ID of the sender agent
     * @param type Type of message (e.g., "BIN_BROADCAST", "VEHICLE_STATUS")
     * @param content String content of the message
     */
    public Message(int senderId, String type, String content) {
        this.senderId = senderId;
        this.type = type;
        this.content = content;
        this.timestamp = System.currentTimeMillis();
    }

    public int getSenderId() {
        return senderId;
    }

    public String getType() {
        return type;
    }

    public String getContent() {
        return content;
    }

    public long getTimestamp() {
        return timestamp;
    }
}