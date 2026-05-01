package hackerrouter.dungeonforcer;

public enum SearchType {
    MAX_TOTAL(false),
    MAX_TOTAL_PREFER_TYPE(true),
    MAX_TYPE(true),
    MAX_TYPE_PREFER_MORE(true),
    MAX_ACTIVATED_TOTAL(false),
    MAX_ACTIVATED_TOTAL_PREFER_TYPE(true),
    MAX_ACTIVATED_TYPE(true),
    MAX_ACTIVATED_TYPE_PREFER_MORE(true);

    public final boolean hasType;

    SearchType(boolean hasType) {
        this.hasType = hasType;
    }
}