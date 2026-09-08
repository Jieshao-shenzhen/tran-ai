package com.gdcp.lab.business.common;

public final class ApprovalRoleUtil {
    public static final String DIRECTOR = "DIRECTOR";
    public static final String DEAN = "DEAN";
    public static final String SYSTEM_ADMIN = "SYSTEM_ADMIN";

    private ApprovalRoleUtil() {}

    public static boolean canFirstApprove(String role) {
        return DIRECTOR.equals(role) || SYSTEM_ADMIN.equals(role);
    }

    public static boolean canSecondApprove(String role) {
        return DEAN.equals(role) || SYSTEM_ADMIN.equals(role);
    }
}
