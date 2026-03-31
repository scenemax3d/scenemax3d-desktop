package com.scenemaxeng.common.ui.model;

import org.json.JSONObject;

/**
 * Represents a single constraint connecting one side of a widget to a side of another widget
 * (or the parent container).
 *
 * Example: "my left side is constrained to the right side of 'headerPanel'" becomes:
 *   new UIConstraint(UIConstraintSide.LEFT, "headerPanel", UIConstraintSide.RIGHT, 8)
 *
 * The target name "parent" is reserved and means the containing layer/panel.
 */
public class UIConstraint {

    public static final String TARGET_PARENT = "parent";

    private UIConstraintSide side;          // which side of THIS widget
    private String targetName;              // name of the target widget (or "parent")
    private UIConstraintSide targetSide;    // which side of the TARGET
    private float margin;                   // margin in pixels from the anchor point

    public UIConstraint() {}

    public UIConstraint(UIConstraintSide side, String targetName, UIConstraintSide targetSide, float margin) {
        this.side = side;
        this.targetName = targetName;
        this.targetSide = targetSide;
        this.margin = margin;
    }

    // --- Getters/Setters ---

    public UIConstraintSide getSide() { return side; }
    public void setSide(UIConstraintSide side) { this.side = side; }

    public String getTargetName() { return targetName; }
    public void setTargetName(String targetName) { this.targetName = targetName; }

    public UIConstraintSide getTargetSide() { return targetSide; }
    public void setTargetSide(UIConstraintSide targetSide) { this.targetSide = targetSide; }

    public float getMargin() { return margin; }
    public void setMargin(float margin) { this.margin = margin; }

    /**
     * Returns true if this constraint targets the parent container.
     */
    public boolean isParentConstraint() {
        return TARGET_PARENT.equals(targetName);
    }

    // --- Serialization ---

    public JSONObject toJSON() {
        JSONObject json = new JSONObject();
        json.put("side", side.name());
        json.put("targetName", targetName);
        json.put("targetSide", targetSide.name());
        json.put("margin", margin);
        return json;
    }

    public static UIConstraint fromJSON(JSONObject json) {
        UIConstraint c = new UIConstraint();
        c.side = UIConstraintSide.valueOf(json.getString("side"));
        c.targetName = json.getString("targetName");
        c.targetSide = UIConstraintSide.valueOf(json.getString("targetSide"));
        c.margin = (float) json.optDouble("margin", 0);
        return c;
    }
}
