package cst3492.courseta;

final class ProviderConnection {
    private final String baseUrl;
    private final String model;
    private final String key;

    ProviderConnection(String baseUrl, String model, String key) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.strip();
        this.model = model == null ? "" : model.strip();
        this.key = key == null ? "" : key.strip();
    }

    String baseUrl() {
        return baseUrl;
    }

    String model() {
        return model;
    }

    String key() {
        return key;
    }

    String failure(Throwable error) {
        String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        if (!key.isEmpty()) {
            message = message.replace(key, "[redacted]");
        }
        return message.length() <= 2000 ? message : message.substring(0, 2000) + "…";
    }

    @Override
    public String toString() {
        return "ProviderConnection[redacted]";
    }
}
