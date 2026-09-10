package com.hospital.platform.common.security;
import java.security.Principal; import java.util.Set;
public record AuthenticatedUser(Long id, String username, int tokenVersion, String clientType, Set<String> permissions) implements Principal { @Override public String getName(){return username;} }
