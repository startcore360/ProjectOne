package com.edu360.api.exception;

/** I-07: the action belongs to a module the tenant's plan / override has switched off (403). */
public class ModuleNotEnabledException extends RuntimeException {

    private final String moduleCode;

    public ModuleNotEnabledException(String moduleCode) {
        super("The " + moduleCode + " module is not enabled for this organisation.");
        this.moduleCode = moduleCode;
    }

    public String getModuleCode() {
        return moduleCode;
    }
}
