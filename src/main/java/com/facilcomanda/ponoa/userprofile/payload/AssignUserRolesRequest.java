package com.facilcomanda.ponoa.userprofile.payload;

import com.facilcomanda.ponoa.userprofile.AppUserRole;
import jakarta.validation.constraints.NotEmpty;
import java.util.Set;

public record AssignUserRolesRequest(
    @NotEmpty(message = "Debe especificar al menos un rol")
    Set<AppUserRole> roles) {}
