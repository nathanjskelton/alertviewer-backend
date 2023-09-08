package gmdev.platform.logviewer.server;

public class ServiceResponse<T> {

    private String message;
    private T payload;

    public ServiceResponse(String message, T payload) {
        this.message = message;
        this.payload = payload;
    }

    public ServiceResponse(String message) {
        this.message = message;
        this.payload = null;
    }

    public String getMessage() {
        return message;
    }

    public T getPayload() {
        return payload;
    }
}
