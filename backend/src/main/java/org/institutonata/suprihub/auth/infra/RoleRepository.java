package org.institutonata.suprihub.auth.infra;

import java.util.Collection;
import java.util.Set;

import org.institutonata.suprihub.auth.domain.AppRole;
import org.institutonata.suprihub.auth.domain.Role;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, Short> {

    Set<Role> findByCodeIn(Collection<AppRole> codes);
}
