package com.bank.esps.domain.auth;

/**
 * Function-level entitlements for Position Management Service
 * Based on user_entitlements_architecture.md
 */
public enum PositionFunction {
    // Position Viewing
    POSITION_VIEW("position:view", "View positions"),
    POSITION_SEARCH("position:search", "Search positions"),
    POSITION_EXPORT("position:export", "Export position data"),
    
    // Position Management
    POSITION_UPDATE("position:update", "Update positions"),
    POSITION_ADJUST("position:adjust", "Adjust positions"),
    
    // Trade Operations
    TRADE_CREATE("trade:create", "Create new trades"),
    TRADE_VIEW("trade:view", "View trades"),
    TRADE_INCREASE("trade:increase", "Increase trade quantity"),
    TRADE_DECREASE("trade:decrease", "Decrease trade quantity"),
    TRADE_TERMINATE("trade:terminate", "Terminate trades"),
    
    // Diagnostics and Administration
    DIAGNOSTICS_VIEW("diagnostics:view", "View diagnostics"),
    DIAGNOSTICS_RECALCULATE("diagnostics:recalculate", "Trigger recalculation"),
    DIAGNOSTICS_ADMIN("diagnostics:admin", "Diagnostics administration"),
    
    // Position Administration
    POSITION_ADMIN("position:admin", "Position administration");
    
    private final String functionName;
    private final String description;
    
    PositionFunction(String functionName, String description) {
        this.functionName = functionName;
        this.description = description;
    }
    
    public String getFunctionName() {
        return functionName;
    }
    
    public String getDescription() {
        return description;
    }
    
    public static PositionFunction fromFunctionName(String functionName) {
        for (PositionFunction func : values()) {
            if (func.functionName.equals(functionName)) {
                return func;
            }
        }
        return null;
    }
}
