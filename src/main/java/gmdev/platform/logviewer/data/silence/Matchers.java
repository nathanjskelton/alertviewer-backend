package gmdev.platform.logviewer.data.silence;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Matchers {

    @JsonProperty("isRegex")
    private boolean isregex;
    @JsonProperty("isEqual")
    private boolean isequal;
    private String name;
    private String value;

    public void setIsregex(boolean isregex) {
        this.isregex = isregex;
    }

    public boolean getIsregex() {
        return isregex;
    }

    public void setIsequal(boolean isequal) {
        this.isequal = isequal;
    }

    public boolean getIsequal() {
        return isequal;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

}